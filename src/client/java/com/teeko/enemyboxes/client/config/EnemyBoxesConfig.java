package com.teeko.enemyboxes.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.teeko.enemyboxes.client.state.EnemyBoxesState;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.net.URI;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
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
        // Snaplines
        boolean snaplinesEnabled     = false;
        boolean drawOffscreenEnemies = false;
        boolean snaplinesOnlyClosest = false;
        int     snaplineThickness    = 1;
        // Box labels
        boolean showBoxDistance = false;
        // Hideonleaf hunt & shard tracker
        boolean autoHuntEnabled      = false;
        boolean shardTrackerEnabled  = false;
        // Beachball macro
        boolean beachballCrouchEnabled = true;
        Boolean beachballForcedStopAlertsEnabled = null;
        Boolean chatNameMentionAlertsEnabled = null;
        Boolean serverShutdownAlertsEnabled = null;
        String  alertServerUrl = "";
        String  alertServerSecret = "";
        String  alertAuthToken = "";
        String  alertAuthMinecraftUuid = "";
        @Deprecated
        Boolean beachballForcedStopWebhookEnabled = null;
        @Deprecated
        Boolean chatNameMentionWebhookEnabled = null;
        @Deprecated
        String  beachballForcedStopWebhookUrl = "";
        @Deprecated
        String  beachballForcedStopDiscordUserId = "";
        // Auto-fisher
        boolean autoFisherEnabled = false;
        int     fishingWeaponSlot = 0;
        Boolean autoFisherForcedStopAlertsEnabled = null;
        // Auto-clicker
        boolean autoClickerEnabled = false;
        int     acCpsMin           = 7;
        int     acCpsMode          = 12;
        int     acCpsMax           = 16;
        boolean showClickGraph     = false;
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
        d.snaplinesEnabled       = EnemyBoxesState.snaplinesEnabled;
        d.drawOffscreenEnemies   = EnemyBoxesState.drawOffscreenEnemies;
        d.snaplinesOnlyClosest   = EnemyBoxesState.snaplinesOnlyClosest;
        d.snaplineThickness      = EnemyBoxesState.snaplineThickness;
        d.showBoxDistance        = EnemyBoxesState.showBoxDistance;
        d.autoHuntEnabled          = EnemyBoxesState.autoHuntEnabled;
        d.shardTrackerEnabled      = EnemyBoxesState.shardTrackerEnabled;
        d.beachballCrouchEnabled   = EnemyBoxesState.beachballCrouchEnabled;
        d.beachballForcedStopAlertsEnabled = EnemyBoxesState.beachballForcedStopAlertsEnabled;
        d.chatNameMentionAlertsEnabled = EnemyBoxesState.chatNameMentionAlertsEnabled;
        d.serverShutdownAlertsEnabled = EnemyBoxesState.serverShutdownAlertsEnabled;
        d.alertServerUrl = EnemyBoxesState.alertServerUrl;
        d.alertServerSecret = EnemyBoxesState.alertServerSecret;
        d.alertAuthToken = EnemyBoxesState.alertAuthToken;
        d.alertAuthMinecraftUuid = EnemyBoxesState.alertAuthMinecraftUuid;
        d.autoFisherEnabled                    = EnemyBoxesState.autoFisherEnabled;
        d.fishingWeaponSlot                    = EnemyBoxesState.fishingWeaponSlot;
        d.autoFisherForcedStopAlertsEnabled    = EnemyBoxesState.autoFisherForcedStopAlertsEnabled;
        d.autoClickerEnabled     = EnemyBoxesState.autoClickerEnabled;
        d.acCpsMin               = EnemyBoxesState.acCpsMin;
        d.acCpsMode              = EnemyBoxesState.acCpsMode;
        d.acCpsMax               = EnemyBoxesState.acCpsMax;
        d.showClickGraph         = EnemyBoxesState.showClickGraph;

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
            EnemyBoxesState.snaplinesEnabled       = d.snaplinesEnabled;
            EnemyBoxesState.drawOffscreenEnemies   = d.drawOffscreenEnemies;
            EnemyBoxesState.snaplinesOnlyClosest   = d.snaplinesOnlyClosest;
            EnemyBoxesState.snaplineThickness      = d.snaplineThickness;
            EnemyBoxesState.showBoxDistance        = d.showBoxDistance;
            EnemyBoxesState.autoHuntEnabled          = d.autoHuntEnabled;
            EnemyBoxesState.shardTrackerEnabled      = d.shardTrackerEnabled;
            EnemyBoxesState.beachballCrouchEnabled   = d.beachballCrouchEnabled;
            applyAlertSettings(d);
            EnemyBoxesState.beachballMacroRunning  = false;
            EnemyBoxesState.autoFisherEnabled      = d.autoFisherEnabled;
            EnemyBoxesState.fishingWeaponSlot      = Math.max(0, Math.min(8, d.fishingWeaponSlot));
            EnemyBoxesState.autoClickerEnabled     = d.autoClickerEnabled;
            EnemyBoxesState.acCpsMin               = d.acCpsMin;
            EnemyBoxesState.acCpsMode              = d.acCpsMode;
            EnemyBoxesState.acCpsMax               = d.acCpsMax;
            EnemyBoxesState.showClickGraph         = d.showClickGraph;

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

    public static void reloadAlertSettings() {
        File file = CONFIG_PATH.toFile();
        if (!file.exists()) return;

        try (Reader r = new FileReader(file)) {
            Data d = GSON.fromJson(r, Data.class);
            if (d == null) return;

            applyAlertSettings(d);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void applyAlertSettings(Data d) {
        EnemyBoxesState.beachballForcedStopAlertsEnabled = resolveBoolean(
                d.beachballForcedStopAlertsEnabled,
                d.beachballForcedStopWebhookEnabled,
                false
        );
        EnemyBoxesState.chatNameMentionAlertsEnabled = resolveBoolean(
                d.chatNameMentionAlertsEnabled,
                d.chatNameMentionWebhookEnabled,
                true
        );
        EnemyBoxesState.serverShutdownAlertsEnabled = resolveBoolean(
                d.serverShutdownAlertsEnabled,
                null,
                true
        );
        EnemyBoxesState.autoFisherForcedStopAlertsEnabled = resolveBoolean(
                d.autoFisherForcedStopAlertsEnabled,
                null,
                false
        );
        EnemyBoxesState.alertServerUrl = resolveAlertServerUrl(d);
        EnemyBoxesState.alertServerSecret = sanitize(d.alertServerSecret);
        EnemyBoxesState.alertAuthToken = sanitize(d.alertAuthToken);
        EnemyBoxesState.alertAuthMinecraftUuid = sanitize(d.alertAuthMinecraftUuid);
    }

    private static boolean resolveBoolean(Boolean current, Boolean legacy, boolean fallback) {
        if (current != null) return current;
        if (legacy != null) return legacy;
        return fallback;
    }

    private static String resolveAlertServerUrl(Data d) {
        String current = normalizeAlertServerUrl(d.alertServerUrl);
        if (!current.isEmpty()) {
            return current;
        }

        String legacy = sanitize(d.beachballForcedStopWebhookUrl);
        String legacyLower = legacy.toLowerCase(Locale.ROOT);
        if (legacyLower.contains("discord.com/api/webhooks") || legacyLower.contains("discordapp.com/api/webhooks")) {
            return EnemyBoxesState.DEFAULT_ALERT_SERVER_URL;
        }

        String normalizedLegacy = normalizeAlertServerUrl(legacy);
        return normalizedLegacy.isEmpty()
                ? EnemyBoxesState.DEFAULT_ALERT_SERVER_URL
                : normalizedLegacy;
    }

    private static String normalizeAlertServerUrl(String value) {
        String clean = sanitize(value);
        if (clean.isEmpty()) {
            return "";
        }

        String lower = clean.toLowerCase(Locale.ROOT);
        if (lower.endsWith("/webhook")) {
            return clean.substring(0, clean.length() - "/webhook".length()) + "/events";
        }

        if (lower.endsWith("/events")) {
            return clean;
        }

        try {
            URI uri = URI.create(clean);
            String path = uri.getPath();

            if (path == null || path.isBlank() || "/".equals(path)) {
                return clean.endsWith("/") ? clean + "events" : clean + "/events";
            }
        } catch (IllegalArgumentException ignored) {
            return clean;
        }

        return clean;
    }

    private static String sanitize(String value) {
        return value == null ? "" : value.trim();
    }
}
