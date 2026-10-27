package com.woopie;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

/** Draws the Woopie sword model in first person and drives its animations. */
public final class SwordRenderer {
    // Sign conventions found by checking that the hand cube stays on the sword grip across the animations.
    static final double[] MODEL_ROT_SIGN = {1, 1, 1};
    static final double[] ANIM_ROT_SIGN = {-1, -1, 1};
    static final double[] ANIM_POS_SIGN = {1, 1, -1};

    // Tunables: view anchor (the idle_view bone), overall scale and extra offset in blocks.
    static final double[] VIEW_ANCHOR = {5.0, 9.1563, 13.05};
    static double SCALE = 1.0;
    static double OFF_X = 0.0, OFF_Y = 0.0, OFF_Z = 0.0;
    // Whole-model view rotation in degrees (yaw = swing blade left/right, pitch = tilt up/down, roll = twist) and arm skin choice.
    static double VIEW_YAW = 0.0, VIEW_PITCH = 0.0, VIEW_ROLL = 0.0;
    static boolean STEVE_ARM = true;

    static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("woopie", "textures/gun/uv/woopie.png");
    static final int FULL_BRIGHT = 15728880;

    static Geo geo;
    static Map<String, Anim> anims;
    static boolean loadFailed;

    static String current = null;
    static long startNanos;
    static boolean wasSwinging, wasUse, wasHolding;
    static int comboIndex = 0;
    static int lastSlot = -1;
    static net.minecraft.world.item.Item lastItem;

    static void load() {
        if (geo != null || loadFailed) return;
        try {
            var rm = Minecraft.getInstance().getResourceManager();
            try (Reader r = rm.getResource(Identifier.fromNamespaceAndPath("woopie", "geo/woopie_geo.json")).orElseThrow().openAsReader()) {
                geo = Geo.parse(JsonParser.parseReader(r).getAsJsonObject());
            }
            try (Reader r = rm.getResource(Identifier.fromNamespaceAndPath("woopie", "animations/woopie.animation.json")).orElseThrow().openAsReader()) {
                anims = Anim.parseAll(JsonParser.parseReader(r).getAsJsonObject());
            }
        } catch (Exception e) {
            loadFailed = true;
            e.printStackTrace();
        }
    }

    static void play(String name, String sound) {
        current = name;
        startNanos = System.nanoTime();
        if (sound != null) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(
                    SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("woopie", sound)), 1.0F, 1.0F));
        }
    }

    /** Called every frame while the sword is the main-hand item. */
    public static void render(PoseStack poseStack, SubmitNodeCollector collector, PlayerSkin skin, float swing, int light, boolean sneaking, float inv) {
        load();
        Tuning.apply();
        if (geo == null) return;
        Minecraft mc = Minecraft.getInstance();

        // Swap detection uses the live selected slot/item so the draw animation starts the instant the
        // player switches, instead of waiting for vanilla's lower/raise cycle. Vanilla also dips the hand
        // after every attack; that dip is ignored here so attacks no longer replay "draw".
        int slot = -1;
        net.minecraft.world.item.Item item = null;
        if (mc.player != null) {
            slot = mc.player.getInventory().getSelectedSlot();
            item = mc.player.getMainHandItem().getItem();
        }
        if (!wasHolding || slot != lastSlot || item != lastItem) play("draw", "draw");
        lastSlot = slot;
        lastItem = item;
        wasHolding = true;
        boolean swinging = swing > 0.0F;
        boolean use = mc.options.keyUse.isDown();
        if (swinging && !wasSwinging) {
            if (sneaking) play("inspect_empty", "inspect");
            else { comboIndex = comboIndex % 4 + 1; play("melee_bayonet_" + comboIndex, "melee_bayonet"); }
        } else if (use && !wasUse) {
            play("melee_push", "melee_push");
        }
        wasSwinging = swinging;
        wasUse = use;

        Anim cur = anims.get(current);
        double t = (System.nanoTime() - startNanos) / 1.0e9;
        if (cur == null || (!cur.loop && t > cur.length)) {
            current = "static_idle";
            cur = anims.get("static_idle");
            t = 0;
        }
        if (cur.loop && cur.length > 0) t %= cur.length;
        else if (cur.length == 0) t = 0;

        // Pose every bone (parents first).
        Map<Geo.Bone, M4> world = new HashMap<>();
        for (Geo.Bone b : geo.bones) pose(b, cur, t, world);

        PoseStack.Pose pose;
        poseStack.pushPose();
        // Model pixel space -> view space: anchor idle_view at the eye, 1/16 block per pixel.
        poseStack.translate(OFF_X, OFF_Y, OFF_Z);
        poseStack.mulPose(new org.joml.Matrix4f().rotationY((float) Math.toRadians(VIEW_YAW)).rotateX((float) Math.toRadians(VIEW_PITCH)).rotateZ((float) Math.toRadians(VIEW_ROLL)));
        // Bedrock +X is screen-left for this model (the left hand sits at +X), so mirror X.
        poseStack.scale((float) (-SCALE / 16.0), (float) (SCALE / 16.0), (float) (SCALE / 16.0));
        poseStack.translate(-VIEW_ANCHOR[0], -VIEW_ANCHOR[1], -VIEW_ANCHOR[2]);

        final Map<Geo.Bone, M4> w = world;
        final Anim animRef = cur;
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(TEXTURE), (p, vc) -> draw(p, vc, w, light, false, null));
        collector.submitCustomGeometry(poseStack, RenderTypes.eyes(TEXTURE), (p, vc) -> draw(p, vc, w, FULL_BRIGHT, true, null));
        Identifier skinTex = STEVE_ARM ? Identifier.fromNamespaceAndPath("minecraft", "textures/entity/player/wide/steve.png") : skin.body().texturePath();
        final boolean slimArm = !STEVE_ARM && skin.model() == PlayerModelType.SLIM;
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(skinTex), (p, vc) -> drawHands(p, vc, w, light, slimArm));
        poseStack.popPose();
    }

    /** Reset animation state when the sword is no longer held. */
    public static void notHolding() {
        wasHolding = false;
        lastSlot = -1;
        lastItem = null;
        wasSwinging = false;
        comboIndex = 0;
    }

    static M4 pose(Geo.Bone b, Anim a, double t, Map<Geo.Bone, M4> out) {
        M4 done = out.get(b);
        if (done != null) return done;
        double[] pos = {0, 0, 0}, rot = {0, 0, 0}, sc = {1, 1, 1};
        Anim.BoneAnim ba = a.bones.get(b.name);
        if (ba != null) {
            double[] v;
            if (ba.pos != null && (v = ba.pos.sample(t)) != null) pos = v;
            if (ba.rot != null && (v = ba.rot.sample(t)) != null) rot = v;
            if (ba.scale != null && (v = ba.scale.sample(t)) != null) sc = v;
        }
        double ax = b.rot[0] * MODEL_ROT_SIGN[0] + rot[0] * ANIM_ROT_SIGN[0];
        double ay = b.rot[1] * MODEL_ROT_SIGN[1] + rot[1] * ANIM_ROT_SIGN[1];
        double az = b.rot[2] * MODEL_ROT_SIGN[2] + rot[2] * ANIM_ROT_SIGN[2];
        M4 local = M4.translate(b.pivot[0] + pos[0] * ANIM_POS_SIGN[0], b.pivot[1] + pos[1] * ANIM_POS_SIGN[1], b.pivot[2] + pos[2] * ANIM_POS_SIGN[2])
                .mul(M4.rotateZYX(ax, ay, az))
                .mul(M4.scale(sc[0], sc[1], sc[2]))
                .mul(M4.translate(-b.pivot[0], -b.pivot[1], -b.pivot[2]));
        M4 m = b.parent != null ? pose(b.parent, a, t, out).mul(local) : local;
        out.put(b, m);
        return m;
    }

    static boolean isHand(Geo.Bone b) { return b.name.equals("righthand_pos") || b.name.equals("lefthand_pos"); }

    static void draw(PoseStack.Pose pose, VertexConsumer vc, Map<Geo.Bone, M4> world, int light, boolean glowPass, Object unused) {
        for (Geo.Bone b : geo.bones) {
            if (isHand(b) || b.glow != glowPass) continue;
            M4 m = world.get(b);
            for (Geo.Cube c : b.cubes) cube(pose, vc, m, b, c, c.uv, geo.texW, geo.texH, light);
        }
    }

    static void drawHands(PoseStack.Pose pose, VertexConsumer vc, Map<Geo.Bone, M4> world, int light, boolean slim) {
        for (Geo.Bone b : geo.bones) {
            if (!isHand(b)) continue;
            boolean right = b.name.startsWith("right");
            M4 m = world.get(b);
            for (Geo.Cube c : b.cubes) {
                Geo.Cube arm = new Geo.Cube();
                arm.o = c.o.clone();
                arm.s = c.s.clone();
                if (slim) { arm.s[0] = 3; arm.o[0] += 0.5; }
                double[] uv0 = right ? new double[]{40, 16} : new double[]{32, 48};
                arm.uv = Geo.boxUv(uv0, arm.s[0], arm.s[1], arm.s[2]);
                cube(pose, vc, m, b, arm, arm.uv, 64, 64, light);
            }
        }
    }

    // Face corners as {TL, TR, BL, BR}, each given as {xSel, ySel, zSel}: 0 = min side, 1 = max side.
    static final int[][][] CORNERS = {
            {{1, 1, 0}, {0, 1, 0}, {1, 0, 0}, {0, 0, 0}}, // north (-Z)
            {{1, 1, 1}, {1, 1, 0}, {1, 0, 1}, {1, 0, 0}}, // east  (+X)
            {{0, 1, 1}, {1, 1, 1}, {0, 0, 1}, {1, 0, 1}}, // south (+Z)
            {{0, 1, 0}, {0, 1, 1}, {0, 0, 0}, {0, 0, 1}}, // west  (-X)
            {{0, 1, 0}, {1, 1, 0}, {0, 1, 1}, {1, 1, 1}}, // up    (+Y)
            {{0, 0, 1}, {1, 0, 1}, {0, 0, 0}, {1, 0, 0}}, // down  (-Y)
    };
    static final double[][] NORMALS = {{0, 0, -1}, {1, 0, 0}, {0, 0, 1}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}};

    static void cube(PoseStack.Pose pose, VertexConsumer vc, M4 boneM, Geo.Bone bone, Geo.Cube c, double[][] uvs, double tw, double th, int light) {
        M4 m = boneM;
        if (c.rot != null) {
            double[] pv = c.pivot != null ? c.pivot : bone.pivot;
            m = m.mul(M4.translate(pv[0], pv[1], pv[2]))
                    .mul(M4.rotateZYX(c.rot[0] * MODEL_ROT_SIGN[0], c.rot[1] * MODEL_ROT_SIGN[1], c.rot[2] * MODEL_ROT_SIGN[2]))
                    .mul(M4.translate(-pv[0], -pv[1], -pv[2]));
        }
        double inf = c.inflate;
        double[] lo = {c.o[0] - inf, c.o[1] - inf, c.o[2] - inf};
        double[] hi = {c.o[0] + c.s[0] + inf, c.o[1] + c.s[1] + inf, c.o[2] + c.s[2] + inf};
        for (int f = 0; f < 6; f++) {
            double[] uv = uvs[f];
            if (uv == null) continue;
            double[] n = m.dir(NORMALS[f][0], NORMALS[f][1], NORMALS[f][2]);
            for (int v = 0; v < 4; v++) {
                int[] cs = CORNERS[f][v];
                double[] p = m.point(cs[0] == 1 ? hi[0] : lo[0], cs[1] == 1 ? hi[1] : lo[1], cs[2] == 1 ? hi[2] : lo[2]);
                double u = (uv[0] + ((v & 1) == 1 ? uv[2] : 0)) / tw;
                double vv = (uv[1] + (v >= 2 ? uv[3] : 0)) / th;
                vc.addVertex(pose, (float) p[0], (float) p[1], (float) p[2])
                        .setColor(-1)
                        .setUv((float) u, (float) vv)
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(light)
                        .setNormal(pose, (float) n[0], (float) n[1], (float) n[2]);
            }
        }
    }
}
