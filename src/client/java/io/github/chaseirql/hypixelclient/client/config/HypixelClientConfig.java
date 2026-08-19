package io.github.chaseirql.hypixelclient.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.chaseirql.hypixelclient.client.state.HypixelClientState;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.net.URI;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Serializes user-facing settings to {@code config/hypixelclient.json}.
 *
 * <p>The former {@code enemyboxes.json} path is read once as a compatibility fallback. A
 * subsequent save writes the same settings under the HypixelClient name without deleting the legacy
 * file.</p>
 */
public final class HypixelClientConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir();
    private static final Path CONFIG_PATH = CONFIG_DIR.resolve("hypixelclient.json");
    private static final Path LEGACY_CONFIG_PATH = CONFIG_DIR.resolve("enemyboxes.json");

    private HypixelClientConfig() {}

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
        d.enabled                = HypixelClientState.enabled;
        d.showFovCircle          = HypixelClientState.showFovCircle;
        d.lockFov                = HypixelClientState.lockFov;
        d.aimbotEnabled          = HypixelClientState.aimbotEnabled;
        d.aimSmoothingMin        = HypixelClientState.aimSmoothingMin;
        d.aimSmoothingMode       = HypixelClientState.aimSmoothingMode;
        d.aimSmoothingMax        = HypixelClientState.aimSmoothingMax;
        d.driftStrength          = HypixelClientState.driftStrength;
        d.jitterStrength         = HypixelClientState.jitterStrength;
        d.aimPriorityBlend       = HypixelClientState.aimPriorityBlend;
        d.autoSwingEnabled       = HypixelClientState.autoSwingEnabled;
        d.requireLineOfSight     = HypixelClientState.requireLineOfSight;
        d.randomizeReactionDelay = HypixelClientState.randomizeReactionDelay;
        d.swingDelayMin          = HypixelClientState.swingDelayMin;
        d.swingDelayMax          = HypixelClientState.swingDelayMax;
        d.swingDelayMode         = HypixelClientState.swingDelayMode;
        d.reactionDelayMin       = HypixelClientState.reactionDelayMin;
        d.reactionDelayMax       = HypixelClientState.reactionDelayMax;
        d.reactionDelayMode      = HypixelClientState.reactionDelayMode;
        d.showCps                = HypixelClientState.showCps;
        d.cpsX                   = HypixelClientState.cpsX;
        d.cpsY                   = HypixelClientState.cpsY;
        d.cpsScale               = HypixelClientState.cpsScale;
        d.snaplinesEnabled       = HypixelClientState.snaplinesEnabled;
        d.drawOffscreenEnemies   = HypixelClientState.drawOffscreenEnemies;
        d.snaplinesOnlyClosest   = HypixelClientState.snaplinesOnlyClosest;
        d.snaplineThickness      = HypixelClientState.snaplineThickness;
        d.showBoxDistance        = HypixelClientState.showBoxDistance;
        d.autoHuntEnabled          = HypixelClientState.autoHuntEnabled;
        d.shardTrackerEnabled      = HypixelClientState.shardTrackerEnabled;
        d.beachballCrouchEnabled   = HypixelClientState.beachballCrouchEnabled;
        d.beachballForcedStopAlertsEnabled = HypixelClientState.beachballForcedStopAlertsEnabled;
        d.chatNameMentionAlertsEnabled = HypixelClientState.chatNameMentionAlertsEnabled;
        d.serverShutdownAlertsEnabled = HypixelClientState.serverShutdownAlertsEnabled;
        d.alertServerUrl = HypixelClientState.alertServerUrl;
        d.alertServerSecret = HypixelClientState.alertServerSecret;
        d.alertAuthToken = HypixelClientState.alertAuthToken;
        d.alertAuthMinecraftUuid = HypixelClientState.alertAuthMinecraftUuid;
        d.autoFisherEnabled                    = HypixelClientState.autoFisherEnabled;
        d.fishingWeaponSlot                    = HypixelClientState.fishingWeaponSlot;
        d.autoFisherForcedStopAlertsEnabled    = HypixelClientState.autoFisherForcedStopAlertsEnabled;
        d.autoClickerEnabled     = HypixelClientState.autoClickerEnabled;
        d.acCpsMin               = HypixelClientState.acCpsMin;
        d.acCpsMode              = HypixelClientState.acCpsMode;
        d.acCpsMax               = HypixelClientState.acCpsMax;
        d.showClickGraph         = HypixelClientState.showClickGraph;

        d.targets = HypixelClientState.targets.entrySet().stream()
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

            HypixelClientState.enabled                = d.enabled;
            HypixelClientState.showFovCircle          = d.showFovCircle;
            HypixelClientState.lockFov                = d.lockFov;
            HypixelClientState.aimbotEnabled          = d.aimbotEnabled;
            HypixelClientState.aimSmoothingMin        = d.aimSmoothingMin;
            HypixelClientState.aimSmoothingMode       = d.aimSmoothingMode;
            HypixelClientState.aimSmoothingMax        = d.aimSmoothingMax;
            HypixelClientState.driftStrength          = d.driftStrength;
            HypixelClientState.jitterStrength         = d.jitterStrength;
            HypixelClientState.aimPriorityBlend       = d.aimPriorityBlend;
            HypixelClientState.autoSwingEnabled       = d.autoSwingEnabled;
            HypixelClientState.requireLineOfSight     = d.requireLineOfSight;
            HypixelClientState.randomizeReactionDelay = d.randomizeReactionDelay;
            HypixelClientState.swingDelayMin          = d.swingDelayMin;
            HypixelClientState.swingDelayMax          = d.swingDelayMax;
            HypixelClientState.swingDelayMode         = d.swingDelayMode;
            HypixelClientState.reactionDelayMin       = d.reactionDelayMin;
            HypixelClientState.reactionDelayMax       = d.reactionDelayMax;
            HypixelClientState.reactionDelayMode      = d.reactionDelayMode;
            HypixelClientState.showCps                = d.showCps;
            HypixelClientState.cpsX                   = d.cpsX;
            HypixelClientState.cpsY                   = d.cpsY;
            HypixelClientState.cpsScale               = d.cpsScale;
            HypixelClientState.snaplinesEnabled       = d.snaplinesEnabled;
            HypixelClientState.drawOffscreenEnemies   = d.drawOffscreenEnemies;
            HypixelClientState.snaplinesOnlyClosest   = d.snaplinesOnlyClosest;
            HypixelClientState.snaplineThickness      = d.snaplineThickness;
            HypixelClientState.showBoxDistance        = d.showBoxDistance;
            HypixelClientState.autoHuntEnabled          = d.autoHuntEnabled;
            HypixelClientState.shardTrackerEnabled      = d.shardTrackerEnabled;
            HypixelClientState.beachballCrouchEnabled   = d.beachballCrouchEnabled;
            applyAlertSettings(d);
            HypixelClientState.beachballMacroRunning  = false;
            HypixelClientState.autoFisherEnabled      = d.autoFisherEnabled;
            HypixelClientState.fishingWeaponSlot      = Math.max(0, Math.min(8, d.fishingWeaponSlot));
            HypixelClientState.autoClickerEnabled     = d.autoClickerEnabled;
            HypixelClientState.acCpsMin               = d.acCpsMin;
            HypixelClientState.acCpsMode              = d.acCpsMode;
            HypixelClientState.acCpsMax               = d.acCpsMax;
            HypixelClientState.showClickGraph         = d.showClickGraph;

            HypixelClientState.targets.clear();
            if (d.targets != null) {
                for (TargetEntry t : d.targets) {
                    if (t != null && t.name != null && !t.name.isBlank()) {
                        HypixelClientState.targets.put(t.name, t.enabled);
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
        HypixelClientState.beachballForcedStopAlertsEnabled = resolveBoolean(
                d.beachballForcedStopAlertsEnabled,
                d.beachballForcedStopWebhookEnabled,
                false
        );
        HypixelClientState.chatNameMentionAlertsEnabled = resolveBoolean(
                d.chatNameMentionAlertsEnabled,
                d.chatNameMentionWebhookEnabled,
                false
        );
        HypixelClientState.serverShutdownAlertsEnabled = resolveBoolean(
                d.serverShutdownAlertsEnabled,
                null,
                false
        );
        HypixelClientState.autoFisherForcedStopAlertsEnabled = resolveBoolean(
                d.autoFisherForcedStopAlertsEnabled,
                null,
                false
        );
        HypixelClientState.alertServerUrl = resolveAlertServerUrl(d);
        HypixelClientState.alertServerSecret = sanitize(d.alertServerSecret);
        HypixelClientState.alertAuthToken = sanitize(d.alertAuthToken);
        HypixelClientState.alertAuthMinecraftUuid = sanitize(d.alertAuthMinecraftUuid);
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
            return HypixelClientState.DEFAULT_ALERT_SERVER_URL;
        }

        String normalizedLegacy = normalizeAlertServerUrl(legacy);
        return normalizedLegacy.isEmpty()
                ? HypixelClientState.DEFAULT_ALERT_SERVER_URL
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
