package com.teeko.enemyboxes.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.Path;

public final class EnemyBoxesConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("enemyboxes.json");

    private EnemyBoxesConfig() {}

    private static class Data {
        // ESP
        boolean enabled       = true;
        boolean showFovCircle = true;
        float   lockFov       = 50f;
        // Aimbot
        boolean aimbotEnabled  = false;
        float   aimSmoothing   = 0.15f;
        float   driftStrength  = 0.8f;
        float   jitterStrength = 0.3f;
        // Combat
        boolean autoSwingEnabled      = false;
        boolean requireLineOfSight    = true;
        boolean randomizeReactionDelay = false;
        int     swingDelayMin         = 80;
        int     swingDelayMax         = 250;
        int     swingDelayMode        = 150;
        int     reactionDelayMin      = 50;
        int     reactionDelayMax      = 300;
        int     reactionDelayMode     = 150;
        // Targets
        String[] targetNames = new String[0];
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
        d.aimSmoothing           = EnemyBoxesState.aimSmoothing;
        d.driftStrength          = EnemyBoxesState.driftStrength;
        d.jitterStrength         = EnemyBoxesState.jitterStrength;
        d.autoSwingEnabled       = EnemyBoxesState.autoSwingEnabled;
        d.requireLineOfSight     = EnemyBoxesState.requireLineOfSight;
        d.randomizeReactionDelay = EnemyBoxesState.randomizeReactionDelay;
        d.swingDelayMin          = EnemyBoxesState.swingDelayMin;
        d.swingDelayMax          = EnemyBoxesState.swingDelayMax;
        d.swingDelayMode         = EnemyBoxesState.swingDelayMode;
        d.reactionDelayMin       = EnemyBoxesState.reactionDelayMin;
        d.reactionDelayMax       = EnemyBoxesState.reactionDelayMax;
        d.reactionDelayMode      = EnemyBoxesState.reactionDelayMode;
        d.targetNames            = EnemyBoxesState.targetNames.toArray(new String[0]);
        d.showCps                = EnemyBoxesState.showCps;
        d.cpsX                   = EnemyBoxesState.cpsX;
        d.cpsY                   = EnemyBoxesState.cpsY;
        d.cpsScale               = EnemyBoxesState.cpsScale;

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
            EnemyBoxesState.aimSmoothing           = d.aimSmoothing;
            EnemyBoxesState.driftStrength          = d.driftStrength;
            EnemyBoxesState.jitterStrength         = d.jitterStrength;
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

            EnemyBoxesState.targetNames.clear();
            if (d.targetNames != null) {
                for (String name : d.targetNames) {
                    if (name != null && !name.isBlank()) {
                        EnemyBoxesState.targetNames.add(name);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}