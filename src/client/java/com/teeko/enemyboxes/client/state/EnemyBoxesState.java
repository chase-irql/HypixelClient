package com.teeko.enemyboxes.client.state;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class EnemyBoxesState {

    public static final String DEFAULT_ALERT_SERVER_URL = "https://bot.sky-craft.uk/events";

    // ESP
    public static boolean enabled       = true;
    public static boolean showFovCircle = true;
    public static float   lockFov       = 50f;

    // Aimbot
    public static boolean aimbotEnabled      = false;
    public static float   aimSmoothingMin    = 0.05f;
    public static float   aimSmoothingMode   = 0.15f;
    public static float   aimSmoothingMax    = 0.30f;
    public static float   driftStrength      = 0.8f;
    public static float   jitterStrength     = 0.3f;
    public static float   aimPriorityBlend   = 0.0f; // 0 = pure crosshair, 1 = pure proximity

    // Combat
    public static boolean autoSwingEnabled       = false;
    public static boolean requireLineOfSight      = true;
    public static boolean randomizeReactionDelay  = false;
    public static int     swingDelayMin           = 50;
    public static int     swingDelayMax           = 100;
    public static int     swingDelayMode          = 70;
    public static int     reactionDelayMin        = 50;
    public static int     reactionDelayMax        = 300;
    public static int     reactionDelayMode       = 150;

    // CPS display
    public static boolean showCps  = false;
    public static float   cpsX     = 4f;
    public static float   cpsY     = 4f;
    public static float   cpsScale = 1.0f;

    // Snaplines
    public static boolean snaplinesEnabled    = false;
    public static boolean drawOffscreenEnemies = false;
    public static boolean snaplinesOnlyClosest = false;
    public static int     snaplineThickness   = 1;

    // Box labels
    public static boolean showBoxDistance = false;

    // Hideonleaf shard tracker
    public static boolean shardTrackerEnabled = false;

    // Auto-fisher
    public static boolean autoFisherEnabled = false;

    // Auto-clicker
    public static boolean autoClickerEnabled = false;
    public static int     acCpsMin           = 7;
    public static int     acCpsMode          = 12;
    public static int     acCpsMax           = 16;
    public static boolean showClickGraph     = false;

    // Hideonleaf auto hunt
    public static boolean autoHuntEnabled    = false;
    public static UUID    huntLockedShulker  = null;
    public static UUID    huntTrackedBullet  = null;
    public static UUID    huntLockedBullet   = null;

    // Beachball macro
    public static boolean beachballMacroRunning  = false;
    public static boolean beachballCrouchEnabled = true;
    public static boolean beachballForcedStopAlertsEnabled = false;
    public static boolean chatNameMentionAlertsEnabled = true;
    public static boolean serverShutdownAlertsEnabled = true;
    public static String  alertServerUrl = DEFAULT_ALERT_SERVER_URL;
    public static String  alertServerSecret = "";
    public static String  alertAuthToken = "";
    public static String  alertAuthMinecraftUuid = "";

    // Targets — name -> enabled. Order preserved via LinkedHashMap.

    public static final Map<String, Boolean> targets = new LinkedHashMap<>();
    public static UUID lockedTarget = null;

    private EnemyBoxesState() {}

    public static boolean hasTarget() {
        return targets.values().stream().anyMatch(Boolean::booleanValue);
    }

    /** Add a new target (enabled by default). No-op if name already exists. */
    public static void addTarget(String name) {
        targets.putIfAbsent(name, true);
    }

    /** Toggle a target's enabled state. */
    public static void toggleTarget(String name) {
        targets.computeIfPresent(name, (k, v) -> !v);
    }

    /** Remove a target entirely. */
    public static void removeTarget(String name) {
        targets.remove(name);
    }

    public static boolean matches(LivingEntity entity) {
        if (!enabled || !hasTarget() || entity == null) return false;

        // ArmorStands are used as floating nametag holders on most servers —
        // they match name filters but are never a valid aim target.
        if (entity instanceof ArmorStand) return false;

        // Entities with a degenerate hitbox (< 0.1 blocks on both axes) are
        // invisible nametag proxies, not real mobs.
        AABB box = entity.getBoundingBox();
        if ((box.maxX - box.minX) < 0.1 && (box.maxY - box.minY) < 0.1) return false;

        String scoreboard = stripFormatting(entity.getScoreboardName());
        String display    = stripFormatting(entity.getDisplayName().getString());
        String custom     = entity.hasCustomName()
                ? stripFormatting(entity.getCustomName().getString())
                : null;

        for (Map.Entry<String, Boolean> entry : targets.entrySet()) {
            if (!entry.getValue()) continue; // skip disabled entries
            String f = entry.getKey().toLowerCase(Locale.ROOT);
            if (scoreboard.contains(f)) return true;
            if (display.contains(f))    return true;
            if (custom != null && custom.contains(f)) return true;
        }

        return false;
    }

    private static String stripFormatting(String s) {
        if (s == null) return "";
        return s.replaceAll("§.", "").toLowerCase(Locale.ROOT);
    }

    public static Map<String, Boolean> getTargetsView() {
        return Collections.unmodifiableMap(targets);
    }
}
