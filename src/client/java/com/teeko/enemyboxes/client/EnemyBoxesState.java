package com.teeko.enemyboxes.client;

import net.minecraft.world.entity.LivingEntity;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class EnemyBoxesState {
    public static boolean enabled = true;
    public static boolean aimbotEnabled = false;

    public static final Set<String> targetNames = new LinkedHashSet<>();

    public static boolean showFovCircle = true;
    public static float lockFov = 50f;
    public static UUID lockedTarget = null;

    // Humanization settings
    public static float aimSmoothing = 0.15f;
    public static float driftStrength = 0.8f;
    public static float jitterStrength = 0.3f;

    private EnemyBoxesState() {}

    public static boolean hasTarget() {
        return !targetNames.isEmpty();
    }

    /**
     * Matches against multiple name sources in priority order:
     *
     * 1. getScoreboardName() — set at entity spawn, available at any render distance.
     *    On Hypixel, fake NPC entities use this as the mob's display name (e.g. "Ice Walker").
     *
     * 2. getDisplayName() — team-formatted name, also populated at range on Hypixel
     *    since they use scoreboard teams (fkt_Ice Walker etc).
     *
     * 3. getCustomName() — actual NBT nametag, only for real named entities.
     *
     * All checks are case-insensitive substring matches, with § color codes stripped.
     */
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

    /** Strip Minecraft § color/format codes from a string. */
    private static String stripFormatting(String s) {
        if (s == null) return "";
        // § followed by any character is a formatting code
        return s.replaceAll("§.", "").toLowerCase(Locale.ROOT);
    }

    public static Set<String> getTargetNamesView() {
        return Collections.unmodifiableSet(targetNames);
    }
}