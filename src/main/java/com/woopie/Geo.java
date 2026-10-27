package com.woopie;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Bedrock geometry (the format TACZ gun models use). */
final class Geo {
    static final String[] FACES = {"north", "east", "south", "west", "up", "down"};

    static final class Cube {
        double[] o = new double[3], s = new double[3];
        double[] pivot, rot;
        double inflate;
        double[][] uv = new double[6][]; // per face: u, v, uw, vh (null = not drawn)
    }

    static final class Bone {
        String name, parentName;
        Bone parent;
        double[] pivot = new double[3], rot = new double[3];
        List<Cube> cubes = new ArrayList<>();
        boolean glow;
    }

    final List<Bone> bones = new ArrayList<>();
    final Map<String, Bone> byName = new HashMap<>();
    double texW = 128, texH = 128;

    static double[] arr(JsonElement e, double[] def) {
        if (e == null || !e.isJsonArray()) return def;
        JsonArray a = e.getAsJsonArray();
        double[] r = new double[a.size()];
        for (int i = 0; i < r.length; i++) r[i] = a.get(i).getAsDouble();
        return r;
    }

    static Geo parse(JsonObject root) {
        JsonObject g = root.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        Geo geo = new Geo();
        JsonObject d = g.getAsJsonObject("description");
        if (d.has("texture_width")) geo.texW = d.get("texture_width").getAsDouble();
        if (d.has("texture_height")) geo.texH = d.get("texture_height").getAsDouble();
        for (JsonElement be : g.getAsJsonArray("bones")) {
            JsonObject bj = be.getAsJsonObject();
            Bone b = new Bone();
            b.name = bj.get("name").getAsString();
            b.parentName = bj.has("parent") ? bj.get("parent").getAsString() : null;
            b.pivot = arr(bj.get("pivot"), new double[3]);
            b.rot = arr(bj.get("rotation"), new double[3]);
            b.glow = b.name.toLowerCase().contains("illuminated");
            if (bj.has("cubes")) for (JsonElement ce : bj.getAsJsonArray("cubes")) b.cubes.add(cube(ce.getAsJsonObject()));
            geo.bones.add(b);
            geo.byName.put(b.name, b);
        }
        for (Bone b : geo.bones) if (b.parentName != null) b.parent = geo.byName.get(b.parentName);
        return geo;
    }

    static Cube cube(JsonObject j) {
        Cube c = new Cube();
        c.o = arr(j.get("origin"), c.o);
        c.s = arr(j.get("size"), c.s);
        c.pivot = j.has("pivot") ? arr(j.get("pivot"), null) : null;
        c.rot = j.has("rotation") ? arr(j.get("rotation"), null) : null;
        if (j.has("inflate")) c.inflate = j.get("inflate").getAsDouble();
        JsonElement uv = j.get("uv");
        if (uv != null && uv.isJsonObject()) {
            JsonObject uo = uv.getAsJsonObject();
            for (int i = 0; i < 6; i++) {
                if (!uo.has(FACES[i])) continue;
                JsonObject f = uo.getAsJsonObject(FACES[i]);
                double[] p = arr(f.get("uv"), new double[2]);
                double[] sz = arr(f.get("uv_size"), new double[]{0, 0});
                c.uv[i] = new double[]{p[0], p[1], sz[0], sz[1]};
            }
        } else if (uv != null && uv.isJsonArray()) {
            c.uv = boxUv(arr(uv, new double[2]), c.s[0], c.s[1], c.s[2]);
        }
        return c;
    }

    /** Classic box UV layout: east | north | west | south on the middle row, up/down above. */
    static double[][] boxUv(double[] p, double w, double h, double d) {
        double u = p[0], v = p[1];
        double[][] r = new double[6][];
        r[1] = new double[]{u, v + d, d, h};            // east
        r[0] = new double[]{u + d, v + d, w, h};        // north
        r[3] = new double[]{u + d + w, v + d, d, h};    // west
        r[2] = new double[]{u + d + w + d, v + d, w, h};// south
        r[4] = new double[]{u + d, v, w, d};            // up
        r[5] = new double[]{u + d + w, v, w, d};        // down
        return r;
    }
}
