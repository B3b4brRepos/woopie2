package com.ans.client.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.tags.ItemTags;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * Detects whether the player has a resource pack enabled that replaces vanilla tools
 * (swords, pickaxes, axes, shovels, hoes). If so, skip our own 3D tool attachables so the
 * other pack's tools show instead.
 *
 * Compatibility notes:
 *  - Works with the 26.x pack API (Pack.open() returns a Stream of PackResources).
 *  - The result is cached against the list of enabled pack ids, so enabling, disabling or
 *    reordering packs is picked up automatically; invalidate() is still available for reload hooks.
 *  - Built-in, feature and mod-provided packs are ignored by pack source, not just by name.
 *  - Detects item model definitions, legacy item models, item textures, and CIT-style
 *    overrides (OptiFine / CIT Resewn / Fabric CIT) that mention a tool.
 *  - Tool names come from both a fixed material list and the item registry/tags, so modded
 *    material variants in the minecraft namespace are covered too.
 *  - Users can force the result with -Dans.toolpack=on|off.
 */
public final class CustomToolPackDetector {

    private static final List<String> MATERIALS =
            List.of("wooden", "stone", "iron", "golden", "diamond", "netherite", "copper");
    private static final List<String> TOOLS =
            List.of("sword", "pickaxe", "axe", "shovel", "hoe");

    /** Packs that belong to the base game or to this mod and must be ignored. */
    private static final Set<String> IGNORED_PREFIXES =
            Set.of("vanilla", "mod/", "ans", "fabric", "high_contrast");

    /** Folders (inside any namespace) where CIT packs keep their overrides. */
    private static final List<String> CIT_ROOTS =
            List.of("optifine/cit", "citresewn/cit", "cit");

    private static volatile Boolean cached = null;
    private static volatile List<String> cachedKey = null;

    private CustomToolPackDetector() {}

    /** Call this from a resource reload listener if you have one (optional, see class notes). */
    public static void invalidate() {
        cached = null;
        cachedKey = null;
    }

    public static boolean shouldDisable3dTools() {
        String force = System.getProperty("ans.toolpack");
        if ("on".equalsIgnoreCase(force)) return true;
        if ("off".equalsIgnoreCase(force)) return false;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return false;
        PackRepository repo = mc.getResourcePackRepository();
        if (repo == null) return false;

        List<String> key = new ArrayList<>(repo.getSelectedIds());
        Boolean c = cached;
        if (c != null && key.equals(cachedKey)) return c;

        boolean result = detect(repo.getSelectedPacks());
        cachedKey = key;
        cached = result;
        return result;
    }

    private static boolean detect(Collection<Pack> selected) {
        Set<String> names = toolNames();
        for (Pack pack : selected) {
            if (isIgnored(pack)) continue;
            try (Stream<PackResources> stream = pack.open()) {
                AtomicBoolean hit = new AtomicBoolean(false);
                stream.forEach(res -> {
                    if (!hit.get() && replacesTools(res, names)) hit.set(true);
                });
                if (hit.get()) return true;
            } catch (Exception ignored) {
                // unreadable pack: treat as not a tool pack
            }
        }
        return false;
    }

    private static boolean isIgnored(Pack pack) {
        PackSource src = pack.getPackSource();
        if (src == PackSource.BUILT_IN || src == PackSource.FEATURE) return true;
        String id = pack.getId();
        for (String p : IGNORED_PREFIXES) {
            if (id.equals(p) || id.startsWith(p)) return true;
        }
        // user packs are listed as "file/<name>"; ignore our own pack if it is shipped that way
        return id.startsWith("file/ans");
    }

    /** Every vanilla-namespace tool item path (e.g. "iron_sword"). */
    private static Set<String> toolNames() {
        Set<String> names = new LinkedHashSet<>();
        for (String m : MATERIALS) for (String t : TOOLS) names.add(m + "_" + t);
        try {
            BuiltInRegistries.ITEM.listElements().forEach(h -> {
                if (!"minecraft".equals(h.key().identifier().getNamespace())) return;
                if (h.is(ItemTags.SWORDS) || h.is(ItemTags.PICKAXES) || h.is(ItemTags.AXES)
                        || h.is(ItemTags.SHOVELS) || h.is(ItemTags.HOES)) {
                    names.add(h.key().identifier().getPath());
                }
            });
        } catch (Exception ignored) {
            // tags not bound yet (title screen): fixed list is enough
        }
        return names;
    }

    private static boolean replacesTools(PackResources res, Set<String> names) {
        for (String name : names) {
            // item model definition (modern), legacy item model, or plain item texture
            if (has(res, "items/" + name + ".json")
                    || has(res, "models/item/" + name + ".json")
                    || has(res, "textures/item/" + name + ".png")) {
                return true;
            }
        }
        return hasCitOverride(res);
    }

    /** CIT packs: any file under a cit folder whose name mentions a tool type. */
    private static boolean hasCitOverride(PackResources res) {
        AtomicBoolean hit = new AtomicBoolean(false);
        try {
            for (String ns : res.getNamespaces(PackType.CLIENT_RESOURCES)) {
                for (String root : CIT_ROOTS) {
                    if (hit.get()) return true;
                    res.listResources(PackType.CLIENT_RESOURCES, ns, root, (id, supplier) -> {
                        String p = id.getPath().toLowerCase(Locale.ROOT);
                        if (!(p.endsWith(".properties") || p.endsWith(".json"))) return;
                        for (String t : TOOLS) {
                            if (p.contains(t)) { hit.set(true); return; }
                        }
                    });
                }
            }
        } catch (Exception ignored) {
        }
        return hit.get();
    }

    private static boolean has(PackResources res, String path) {
        try {
            return res.getResource(PackType.CLIENT_RESOURCES,
                    Identifier.fromNamespaceAndPath("minecraft", path)) != null;
        } catch (Exception e) {
            return false;
        }
    }
}
