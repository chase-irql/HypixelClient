package com.teeko.enemyboxes.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class EnemyBoxesConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("enemyboxes.json");

    private EnemyBoxesConfig() {}

    private static class TargetEntry {
        String  name;
        boolean enabled;
        TargetEntry(String name, boolean enabled) {
            this.name    = name;
            this.enabled = enabled;
        }
    }

    private static class Data {
        // ESP
        boolean enabled       = true;
        boolean showFovCircle = true;
        float   lockFov       = 50f;
        // Aimbot
        boolean aimbotEnabled    = false;
        float   aimSmoothingMin  = 0.05f;
        float   aimSmoothingMode = 0.15f;
        float   aimSmoothingMax  = 0.30f;
        float   driftStrength    = 0.05f;
        float   jitterStrength   = 0.1f;
        float   aimPriorityBlend = 0.0f;
        // Combat
        boolean autoSwingEnabled       = false;
        boolean requireLineOfSight     = true;
        boolean randomizeReactionDelay = false;
        int     swingDelayMin          = 80;
        int     swingDelayMax          = 250;
        int     swingDelayMode         = 150;
        int     reactionDelayMin       = 50;
        int     reactionDelayMax       = 300;
        int     reactionDelayMode      = 150;
        // Targets — stored as array of {name, enabled} objects
        TargetEntry[] targets = new TargetEntry[0];
        // CPS
        boolean showCps  = false;
        float   cpsX     = 4f;
        float   cpsY     = 4f;
        float   cpsScale = 1.0f;
    }

    public static void save() {
        Data d = new Data();
        d.enabled                = EnemyBoxesState.enabled;
        d.showFovCircle          = EnemyBoxesState.showFovCircle;
        d.lockFov                = EnemyBoxesState.lockFov;
        d.aimbotEnabled          = EnemyBoxesState.aimbotEnabled;
        d.aimSmoothingMin        = EnemyBoxesState.aimSmoothingMin;
        d.aimSmoothingMode       = EnemyBoxesState.aimSmoothingMode;
        d.aimSmoothingMax        = EnemyBoxesState.aimSmoothingMax;
        d.driftStrength          = EnemyBoxesState.driftStrength;
        d.jitterStrength         = EnemyBoxesState.jitterStrength;
        d.aimPriorityBlend       = EnemyBoxesState.aimPriorityBlend;
        d.autoSwingEnabled       = EnemyBoxesState.autoSwingEnabled;
        d.requireLineOfSight     = EnemyBoxesState.requireLineOfSight;
        d.randomizeReactionDelay = EnemyBoxesState.randomizeReactionDelay;
        d.swingDelayMin          = EnemyBoxesState.swingDelayMin;
        d.swingDelayMax          = EnemyBoxesState.swingDelayMax;
        d.swingDelayMode         = EnemyBoxesState.swingDelayMode;
        d.reactionDelayMin       = EnemyBoxesState.reactionDelayMin;
        d.reactionDelayMax       = EnemyBoxesState.reactionDelayMax;
        d.reactionDelayMode      = EnemyBoxesState.reactionDelayMode;
        d.showCps                = EnemyBoxesState.showCps;
        d.cpsX                   = EnemyBoxesState.cpsX;
        d.cpsY                   = EnemyBoxesState.cpsY;
        d.cpsScale               = EnemyBoxesState.cpsScale;

        d.targets = EnemyBoxesState.targets.entrySet().stream()
                .map(e -> new TargetEntry(e.getKey(), e.getValue()))
                .toArray(TargetEntry[]::new);

        try (Writer w = new FileWriter(CONFIG_PATH.toFile())) {
            GSON.toJson(d, w);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void load() {
        File file = CONFIG_PATH.toFile();
        if (!file.exists()) return;

        try (Reader r = new FileReader(file)) {
            Data d = GSON.fromJson(r, Data.class);
            if (d == null) return;

            EnemyBoxesState.enabled                = d.enabled;
            EnemyBoxesState.showFovCircle          = d.showFovCircle;
            EnemyBoxesState.lockFov                = d.lockFov;
            EnemyBoxesState.aimbotEnabled          = d.aimbotEnabled;
            EnemyBoxesState.aimSmoothingMin        = d.aimSmoothingMin;
            EnemyBoxesState.aimSmoothingMode       = d.aimSmoothingMode;
            EnemyBoxesState.aimSmoothingMax        = d.aimSmoothingMax;
            EnemyBoxesState.driftStrength          = d.driftStrength;
            EnemyBoxesState.jitterStrength         = d.jitterStrength;
            EnemyBoxesState.aimPriorityBlend       = d.aimPriorityBlend;
            EnemyBoxesState.autoSwingEnabled       = d.autoSwingEnabled;
            EnemyBoxesState.requireLineOfSight     = d.requireLineOfSight;
            EnemyBoxesState.randomizeReactionDelay = d.randomizeReactionDelay;
            EnemyBoxesState.swingDelayMin          = d.swingDelayMin;
            EnemyBoxesState.swingDelayMax          = d.swingDelayMax;
            EnemyBoxesState.swingDelayMode         = d.swingDelayMode;
            EnemyBoxesState.reactionDelayMin       = d.reactionDelayMin;
            EnemyBoxesState.reactionDelayMax       = d.reactionDelayMax;
            EnemyBoxesState.reactionDelayMode      = d.reactionDelayMode;
            EnemyBoxesState.showCps                = d.showCps;
            EnemyBoxesState.cpsX                   = d.cpsX;
            EnemyBoxesState.cpsY                   = d.cpsY;
            EnemyBoxesState.cpsScale               = d.cpsScale;

            EnemyBoxesState.targets.clear();
            if (d.targets != null) {
                for (TargetEntry t : d.targets) {
                    if (t != null && t.name != null && !t.name.isBlank()) {
                        EnemyBoxesState.targets.put(t.name, t.enabled);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}