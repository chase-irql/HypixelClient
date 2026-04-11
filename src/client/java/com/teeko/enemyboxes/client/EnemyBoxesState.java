package com.teeko.enemyboxes.client;

import net.minecraft.world.entity.LivingEntity;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class EnemyBoxesState {

    // ESP
    public static boolean enabled       = true;
    public static boolean showFovCircle = true;
    public static float   lockFov       = 50f;

    // Aimbot
    public static boolean aimbotEnabled  = false;
    public static float   aimSmoothing   = 0.15f;
    public static float   driftStrength  = 0.8f;
    public static float   jitterStrength = 0.3f;

    // Combat
    public static boolean autoSwingEnabled       = false;
    public static boolean requireLineOfSight      = true;
    public static boolean randomizeReactionDelay  = false;
    public static int     swingDelayMin           = 80;
    public static int     swingDelayMax           = 250;
    public static int     swingDelayMode          = 150;
    public static int     reactionDelayMin        = 50;
    public static int     reactionDelayMax        = 300;
    public static int     reactionDelayMode       = 150;

    // CPS display
    public static boolean showCps  = false;
    public static float   cpsX     = 4f;
    public static float   cpsY     = 4f;
    public static float   cpsScale = 1.0f;

    // Shared
    public static final Set<String> targetNames = new LinkedHashSet<>();
    public static UUID lockedTarget = null;

    private EnemyBoxesState() {}

    public static boolean hasTarget() {
        return !targetNames.isEmpty();
    }

    public static boolean matches(LivingEntity entity) {
        if (!enabled || !hasTarget() || entity == null) return false;

        String scoreboard = stripFormatting(entity.getScoreboardName());
        String display    = stripFormatting(entity.getDisplayName().getString());
        String custom     = entity.hasCustomName()
                ? stripFormatting(entity.getCustomName().getString())
                : null;

        for (String filter : targetNames) {
            String f = filter.toLowerCase(Locale.ROOT);
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

    public static Set<String> getTargetNamesView() {
        return Collections.unmodifiableSet(targetNames);
    }
}