package com.woopie;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

/** Reads config/woopie_tuning.properties once a second so placement and rotation signs can be tuned live. */
final class Tuning {
    private static long last;

    private static void signs(Properties pr, String key, String def, double[] target) {
        String[] p = pr.getProperty(key, def).split(",");
        if (p.length != 3) p = def.split(",");
        for (int i = 0; i < 3; i++) target[i] = Double.parseDouble(p[i].trim());
    }

    static void apply() {
        long now = System.currentTimeMillis();
        if (now - last < 1000) return;
        last = now;
        try {
            Path p = FabricLoader.getInstance().getConfigDir().resolve("woopie_tuning.properties");
            Properties pr = new Properties();
            boolean fresh = true;
            if (Files.exists(p)) {
                try (var in = Files.newBufferedReader(p)) { pr.load(in); }
                fresh = !"7".equals(pr.getProperty("version"));
            }
            if (fresh) {
                pr.clear();
                pr.setProperty("version", "7");
                pr.setProperty("scale", "1.0");
                pr.setProperty("off_x", "0.2");
                pr.setProperty("off_y", "-0.1");
                pr.setProperty("off_z", "-0.36");
                pr.setProperty("view_yaw", "29");
                pr.setProperty("view_pitch", "14");
                pr.setProperty("view_roll", "0");
                pr.setProperty("steve_arm", "false");
                pr.setProperty("model_rot", "1,1,1");
                pr.setProperty("anim_rot", "-1,-1,1");
                pr.setProperty("anim_pos", "1,1,-1");
                try (var out = Files.newBufferedWriter(p)) {
                    pr.store(out, "Woopie tuning. off_z negative = farther away (smaller). Edits apply within 1 second.");
                }
            }
            SwordRenderer.SCALE = Double.parseDouble(pr.getProperty("scale", "1.0").trim());
            SwordRenderer.OFF_X = Double.parseDouble(pr.getProperty("off_x", "0.2").trim());
            SwordRenderer.OFF_Y = Double.parseDouble(pr.getProperty("off_y", "-0.1").trim());
            SwordRenderer.OFF_Z = Double.parseDouble(pr.getProperty("off_z", "-0.36").trim());
            SwordRenderer.VIEW_YAW = Double.parseDouble(pr.getProperty("view_yaw", "29").trim());
            SwordRenderer.VIEW_PITCH = Double.parseDouble(pr.getProperty("view_pitch", "14").trim());
            SwordRenderer.VIEW_ROLL = Double.parseDouble(pr.getProperty("view_roll", "0").trim());
            SwordRenderer.STEVE_ARM = Boolean.parseBoolean(pr.getProperty("steve_arm", "false").trim());
            signs(pr, "model_rot", "1,1,1", SwordRenderer.MODEL_ROT_SIGN);
            signs(pr, "anim_rot", "-1,-1,1", SwordRenderer.ANIM_ROT_SIGN);
            signs(pr, "anim_pos", "1,1,-1", SwordRenderer.ANIM_POS_SIGN);
        } catch (Exception ignored) {
        }
    }
}
