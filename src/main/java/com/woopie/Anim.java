package com.woopie;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Bedrock animation file: keyframes with pre/post values and linear/catmullrom interpolation. */
final class Anim {
    static final class Key {
        double t;
        double[] pre, post;
        boolean catmull;
    }

    static final class Channel {
        final List<Key> keys = new ArrayList<>();

        double[] sample(double t) {
            if (keys.isEmpty()) return null;
            Key first = keys.get(0), last = keys.get(keys.size() - 1);
            if (t <= first.t) return first.post;
            if (t >= last.t) return last.pre != null ? last.post : last.post;
            int i = 0;
            while (i < keys.size() - 2 && keys.get(i + 1).t <= t) i++;
            Key a = keys.get(i), b = keys.get(i + 1);
            double f = (t - a.t) / (b.t - a.t);
            double[] p1 = a.post, p2 = b.pre;
            double[] out = new double[3];
            if (a.catmull || b.catmull) {
                double[] p0 = i > 0 ? keys.get(i - 1).post : p1;
                double[] p3 = i + 2 < keys.size() ? keys.get(i + 2).pre : p2;
                for (int k = 0; k < 3; k++) out[k] = catmull(p0[k], p1[k], p2[k], p3[k], f);
            } else {
                for (int k = 0; k < 3; k++) out[k] = p1[k] + (p2[k] - p1[k]) * f;
            }
            return out;
        }

        static double catmull(double p0, double p1, double p2, double p3, double t) {
            double t2 = t * t, t3 = t2 * t;
            return 0.5 * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
        }
    }

    static final class BoneAnim {
        Channel pos, rot, scale;
    }

    double length;
    boolean loop;
    final Map<String, BoneAnim> bones = new HashMap<>();

    static Map<String, Anim> parseAll(JsonObject root) {
        Map<String, Anim> out = new HashMap<>();
        JsonObject all = root.getAsJsonObject("animations");
        for (String name : all.keySet()) {
            JsonObject aj = all.getAsJsonObject(name);
            Anim a = new Anim();
            if (aj.has("animation_length")) a.length = aj.get("animation_length").getAsDouble();
            if (aj.has("loop")) {
                JsonElement l = aj.get("loop");
            a.loop = name.equals("static_idle");
            }
            if (aj.has("bones")) {
                JsonObject bj = aj.getAsJsonObject("bones");
                for (String bn : bj.keySet()) {
                    JsonObject c = bj.getAsJsonObject(bn);
                    BoneAnim ba = new BoneAnim();
                    ba.pos = channel(c.get("position"), 0);
                    ba.rot = channel(c.get("rotation"), 0);
                    ba.scale = channel(c.get("scale"), 1);
                    a.bones.put(bn, ba);
                }
            }
            out.put(name, a);
        }
        return out;
    }

    static double num(JsonElement e) {
        // Molang expressions are not supported: plain numbers only, anything else counts as 0.
        try { return e.getAsJsonPrimitive().isNumber() ? e.getAsDouble() : Double.parseDouble(e.getAsString()); }
        catch (Exception ex) { return 0; }
    }

    static double[] vec(JsonElement e, double scalarFill) {
        if (e == null) return null;
        if (e.isJsonArray()) {
            JsonArray a = e.getAsJsonArray();
            double[] r = new double[3];
            for (int i = 0; i < 3 && i < a.size(); i++) r[i] = num(a.get(i));
            return r;
        }
        if (e.isJsonPrimitive()) { double v = num(e); return new double[]{v, v, v}; }
        return null;
    }

    static Channel channel(JsonElement e, double scalarFill) {
        if (e == null) return null;
        Channel ch = new Channel();
        if (e.isJsonArray() || e.isJsonPrimitive()) {
            Key k = new Key();
            k.pre = k.post = vec(e, scalarFill);
            ch.keys.add(k);
            return ch;
        }
        JsonObject o = e.getAsJsonObject();
        if (o.has("vector")) { // single keyframe object
            Key k = key(0, o);
            ch.keys.add(k);
            return ch;
        }
        for (String ts : o.keySet()) {
            try { ch.keys.add(key(Double.parseDouble(ts), o.get(ts))); } catch (NumberFormatException ignored) { }
        }
        ch.keys.sort((x, y) -> Double.compare(x.t, y.t));
        return ch;
    }

    static Key key(double t, JsonElement v) {
        Key k = new Key();
        k.t = t;
        if (v.isJsonObject()) {
            JsonObject o = v.getAsJsonObject();
            double[] pre = vec(o.get("pre"), 0), post = vec(o.get("post"), 0), vector = vec(o.get("vector"), 0);
            k.post = post != null ? post : (vector != null ? vector : pre);
            k.pre = pre != null ? pre : k.post;
            if (k.post == null) k.post = k.pre = new double[3];
            k.catmull = o.has("lerp_mode") && o.get("lerp_mode").getAsString().equals("catmullrom");
        } else {
            k.pre = k.post = vec(v, 0);
        }
        return k;
    }
}
