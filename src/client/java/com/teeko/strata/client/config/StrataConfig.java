package com.teeko.strata.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.teeko.strata.client.state.StrataState;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.net.URI;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Serializes user-facing settings to {@code config/strata.json}.
 *
 * <p>The former {@code enemyboxes.json} path is read once as a compatibility fallback. A
 * subsequent save writes the same settings under the Strata name without deleting the legacy
 * file.</p>
 */
public final class StrataConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir();
    private static final Path CONFIG_PATH = CONFIG_DIR.resolve("strata.json");
    private static final Path LEGACY_CONFIG_PATH = CONFIG_DIR.resolve("enemyboxes.json");

    private StrataConfig() {}

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
        d.enabled                = StrataState.enabled;
        d.showFovCircle          = StrataState.showFovCircle;
        d.lockFov                = StrataState.lockFov;
        d.aimbotEnabled          = StrataState.aimbotEnabled;
        d.aimSmoothingMin        = StrataState.aimSmoothingMin;
        d.aimSmoothingMode       = StrataState.aimSmoothingMode;
        d.aimSmoothingMax        = StrataState.aimSmoothingMax;
        d.driftStrength          = StrataState.driftStrength;
        d.jitterStrength         = StrataState.jitterStrength;
        d.aimPriorityBlend       = StrataState.aimPriorityBlend;
        d.autoSwingEnabled       = StrataState.autoSwingEnabled;
        d.requireLineOfSight     = StrataState.requireLineOfSight;
        d.randomizeReactionDelay = StrataState.randomizeReactionDelay;
        d.swingDelayMin          = StrataState.swingDelayMin;
        d.swingDelayMax          = StrataState.swingDelayMax;
        d.swingDelayMode         = StrataState.swingDelayMode;
        d.reactionDelayMin       = StrataState.reactionDelayMin;
        d.reactionDelayMax       = StrataState.reactionDelayMax;
        d.reactionDelayMode      = StrataState.reactionDelayMode;
        d.showCps                = StrataState.showCps;
        d.cpsX                   = StrataState.cpsX;
        d.cpsY                   = StrataState.cpsY;
        d.cpsScale               = StrataState.cpsScale;
        d.snaplinesEnabled       = StrataState.snaplinesEnabled;
        d.drawOffscreenEnemies   = StrataState.drawOffscreenEnemies;
        d.snaplinesOnlyClosest   = StrataState.snaplinesOnlyClosest;
        d.snaplineThickness      = StrataState.snaplineThickness;
        d.showBoxDistance        = StrataState.showBoxDistance;
        d.autoHuntEnabled          = StrataState.autoHuntEnabled;
        d.shardTrackerEnabled      = StrataState.shardTrackerEnabled;
        d.beachballCrouchEnabled   = StrataState.beachballCrouchEnabled;
        d.beachballForcedStopAlertsEnabled = StrataState.beachballForcedStopAlertsEnabled;
        d.chatNameMentionAlertsEnabled = StrataState.chatNameMentionAlertsEnabled;
        d.serverShutdownAlertsEnabled = StrataState.serverShutdownAlertsEnabled;
        d.alertServerUrl = StrataState.alertServerUrl;
        d.alertServerSecret = StrataState.alertServerSecret;
        d.alertAuthToken = StrataState.alertAuthToken;
        d.alertAuthMinecraftUuid = StrataState.alertAuthMinecraftUuid;
        d.autoFisherEnabled                    = StrataState.autoFisherEnabled;
        d.fishingWeaponSlot                    = StrataState.fishingWeaponSlot;
        d.autoFisherForcedStopAlertsEnabled    = StrataState.autoFisherForcedStopAlertsEnabled;
        d.autoClickerEnabled     = StrataState.autoClickerEnabled;
        d.acCpsMin               = StrataState.acCpsMin;
        d.acCpsMode              = StrataState.acCpsMode;
        d.acCpsMax               = StrataState.acCpsMax;
        d.showClickGraph         = StrataState.showClickGraph;

        d.targets = StrataState.targets.entrySet().stream()
                .map(e -> new TargetEntry(e.getKey(), e.getValue()))
                .toArray(TargetEntry[]::new);

        try (Writer w = new FileWriter(CONFIG_PATH.toFile())) {
            GSON.toJson(d, w);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void load() {
        File file = readableConfigFile();
        if (!file.exists()) return;

        try (Reader r = new FileReader(file)) {
            Data d = GSON.fromJson(r, Data.class);
            if (d == null) return;

            StrataState.enabled                = d.enabled;
            StrataState.showFovCircle          = d.showFovCircle;
            StrataState.lockFov                = d.lockFov;
            StrataState.aimbotEnabled          = d.aimbotEnabled;
            StrataState.aimSmoothingMin        = d.aimSmoothingMin;
            StrataState.aimSmoothingMode       = d.aimSmoothingMode;
            StrataState.aimSmoothingMax        = d.aimSmoothingMax;
            StrataState.driftStrength          = d.driftStrength;
            StrataState.jitterStrength         = d.jitterStrength;
            StrataState.aimPriorityBlend       = d.aimPriorityBlend;
            StrataState.autoSwingEnabled       = d.autoSwingEnabled;
            StrataState.requireLineOfSight     = d.requireLineOfSight;
            StrataState.randomizeReactionDelay = d.randomizeReactionDelay;
            StrataState.swingDelayMin          = d.swingDelayMin;
            StrataState.swingDelayMax          = d.swingDelayMax;
            StrataState.swingDelayMode         = d.swingDelayMode;
            StrataState.reactionDelayMin       = d.reactionDelayMin;
            StrataState.reactionDelayMax       = d.reactionDelayMax;
            StrataState.reactionDelayMode      = d.reactionDelayMode;
            StrataState.showCps                = d.showCps;
            StrataState.cpsX                   = d.cpsX;
            StrataState.cpsY                   = d.cpsY;
            StrataState.cpsScale               = d.cpsScale;
            StrataState.snaplinesEnabled       = d.snaplinesEnabled;
            StrataState.drawOffscreenEnemies   = d.drawOffscreenEnemies;
            StrataState.snaplinesOnlyClosest   = d.snaplinesOnlyClosest;
            StrataState.snaplineThickness      = d.snaplineThickness;
            StrataState.showBoxDistance        = d.showBoxDistance;
            StrataState.autoHuntEnabled          = d.autoHuntEnabled;
            StrataState.shardTrackerEnabled      = d.shardTrackerEnabled;
            StrataState.beachballCrouchEnabled   = d.beachballCrouchEnabled;
            applyAlertSettings(d);
            StrataState.beachballMacroRunning  = false;
            StrataState.autoFisherEnabled      = d.autoFisherEnabled;
            StrataState.fishingWeaponSlot      = Math.max(0, Math.min(8, d.fishingWeaponSlot));
            StrataState.autoClickerEnabled     = d.autoClickerEnabled;
            StrataState.acCpsMin               = d.acCpsMin;
            StrataState.acCpsMode              = d.acCpsMode;
            StrataState.acCpsMax               = d.acCpsMax;
            StrataState.showClickGraph         = d.showClickGraph;

            StrataState.targets.clear();
            if (d.targets != null) {
                for (TargetEntry t : d.targets) {
                    if (t != null && t.name != null && !t.name.isBlank()) {
                        StrataState.targets.put(t.name, t.enabled);
                    }
                }
            }

            if (file.toPath().equals(LEGACY_CONFIG_PATH)) {
                save();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void reloadAlertSettings() {
        File file = readableConfigFile();
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
        StrataState.beachballForcedStopAlertsEnabled = resolveBoolean(
                d.beachballForcedStopAlertsEnabled,
                d.beachballForcedStopWebhookEnabled,
                false
        );
        StrataState.chatNameMentionAlertsEnabled = resolveBoolean(
                d.chatNameMentionAlertsEnabled,
                d.chatNameMentionWebhookEnabled,
                false
        );
        StrataState.serverShutdownAlertsEnabled = resolveBoolean(
                d.serverShutdownAlertsEnabled,
                null,
                false
        );
        StrataState.autoFisherForcedStopAlertsEnabled = resolveBoolean(
                d.autoFisherForcedStopAlertsEnabled,
                null,
                false
        );
        StrataState.alertServerUrl = resolveAlertServerUrl(d);
        StrataState.alertServerSecret = sanitize(d.alertServerSecret);
        StrataState.alertAuthToken = sanitize(d.alertAuthToken);
        StrataState.alertAuthMinecraftUuid = sanitize(d.alertAuthMinecraftUuid);
    }

    private static boolean resolveBoolean(Boolean current, Boolean legacy, boolean fallback) {
        if (current != null) return current;
        if (legacy != null) return legacy;
        return fallback;
    }

    private static File readableConfigFile() {
        File current = CONFIG_PATH.toFile();
        if (current.exists()) {
            return current;
        }
        return LEGACY_CONFIG_PATH.toFile();
    }

    private static String resolveAlertServerUrl(Data d) {
        String current = normalizeAlertServerUrl(d.alertServerUrl);
        if (!current.isEmpty()) {
            return current;
        }

        String legacy = sanitize(d.beachballForcedStopWebhookUrl);
        String legacyLower = legacy.toLowerCase(Locale.ROOT);
        if (legacyLower.contains("discord.com/api/webhooks") || legacyLower.contains("discordapp.com/api/webhooks")) {
            return StrataState.DEFAULT_ALERT_SERVER_URL;
        }

        String normalizedLegacy = normalizeAlertServerUrl(legacy);
        return normalizedLegacy.isEmpty()
                ? StrataState.DEFAULT_ALERT_SERVER_URL
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
