package io.github.chaseirql.hypixelclient.client.debug;

import io.github.chaseirql.hypixelclient.client.feature.beachball.BeachballMacro;
import io.github.chaseirql.hypixelclient.client.state.HypixelClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class DebugDump {

    private static final int MAX_DEBUG_ENTRIES = 12;
    private static final double MAX_DEBUG_DISTANCE_SQ = 2500.0;

    private DebugDump() {}

    public static void dumpNearbyEntities(Minecraft client) {
        if (client.level == null || client.player == null) return;

        Vec3 playerPos = client.player.position();
        List<DebugEntry> entries = new ArrayList<>();

        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity == client.player || entity.isRemoved()) continue;

            double distSq = entity.distanceToSqr(playerPos.x, playerPos.y, playerPos.z);
            if (distSq > MAX_DEBUG_DISTANCE_SQ) continue;

            entries.add(new DebugEntry(entity, Math.sqrt(distSq)));
        }

        entries.sort(Comparator.comparingDouble(DebugEntry::dist));

        chat(client, "\u00A7e=== HypixelClient Debug Dump ===");
        chat(client, "\u00A7eActive filters: \u00A7f" + HypixelClientState.targets);
        chat(client, "\u00A7eESP enabled: \u00A7f" + HypixelClientState.enabled);

        for (String line : BeachballMacro.getDebugLines(client)) {
            chat(client, "\u00A7d" + line);
        }

        chat(client, "\u00A7eFound \u00A7f" + entries.size() + " \u00A7eentities within 50 blocks (sorted by distance)");

        int count = 0;
        UUID lockedBallId = BeachballMacro.getLockedBallId();
        UUID lockedCounterId = BeachballMacro.getLockedCounterId();
        UUID lockedBoxId = BeachballMacro.getLockedBoxId();

        for (DebugEntry entry : entries) {
            Entity entity = entry.entity();
            count++;

            String matchTag = getMatchTag(entity);
            String beachballTag = getBeachballTag(entity.getUUID(), lockedBallId, lockedCounterId, lockedBoxId);
            String type = net.minecraft.world.entity.EntityType.getKey(entity.getType()).toString();

            chat(client, "\u00A77--- #" + count
                    + " dist=" + String.format(Locale.ROOT, "%.1f", entry.dist())
                    + " " + matchTag
                    + (beachballTag.isEmpty() ? "" : " " + beachballTag)
                    + " \u00A77---");
            chat(client, "\u00A7bClass:       \u00A7f" + entity.getClass().getSimpleName());
            chat(client, "\u00A7bType:        \u00A7f" + type);
            chat(client, "\u00A7bUUID:        \u00A7f" + entity.getUUID());
            chat(client, "\u00A7bScore raw:   \u00A7f[" + entity.getScoreboardName() + "]");
            chat(client, "\u00A7bScore strip: \u00A7f[" + stripFormatting(entity.getScoreboardName()) + "]");
            chat(client, "\u00A7bDisp raw:    \u00A7f[" + entity.getDisplayName().getString() + "]");
            chat(client, "\u00A7bDisp strip:  \u00A7f[" + stripFormatting(entity.getDisplayName().getString()) + "]");
            chat(client, "\u00A7bCustom raw:  \u00A7f[" + (entity.hasCustomName() && entity.getCustomName() != null
                    ? entity.getCustomName().getString()
                    : "null") + "]");
            chat(client, "\u00A7bTeam:        \u00A7f" + (entity.getTeam() != null ? entity.getTeam().getName() : "null"));
            chat(client, "\u00A7bPos:         \u00A7f" + formatVec(entity.position()));
            chat(client, "\u00A7bVelocity:    \u00A7f" + formatVec(entity.getDeltaMovement()));
            chat(client, "\u00A7bBbW/H:       \u00A7f" + entity.getBbWidth() + " / " + entity.getBbHeight());
            chat(client, "\u00A7bEntityY:     \u00A7f" + String.format(Locale.ROOT, "%.3f", entity.getY()));
            chat(client, "\u00A7bAABB minY:   \u00A7f" + String.format(Locale.ROOT, "%.3f", entity.getBoundingBox().minY)
                    + "  maxY: " + String.format(Locale.ROOT, "%.3f", entity.getBoundingBox().maxY));
            chat(client, "\u00A7bFlags:       \u00A7fonGround=" + entity.onGround()
                    + " invisible=" + entity.isInvisible()
                    + " noGravity=" + entity.isNoGravity());

            if (entity instanceof Display.TextDisplay textDisplay) {
                chat(client, "\u00A7bText:        \u00A7f[" + textDisplay.getText().getString() + "]");
            } else if (entity instanceof Display.BlockDisplay blockDisplay) {
                chat(client, "\u00A7bBlock state: \u00A7f" + blockDisplay.getBlockState());
            } else if (entity instanceof ArmorStand armorStand) {
                chat(client, "\u00A7bArmorStand:  \u00A7fmarker=" + armorStand.isMarker()
                        + " invisible=" + armorStand.isInvisible()
                        + " small=" + armorStand.isSmall());
            }

            if (count >= MAX_DEBUG_ENTRIES) {
                chat(client, "\u00A7c(capped at " + MAX_DEBUG_ENTRIES + " entities)");
                break;
            }
        }

        if (entries.isEmpty()) chat(client, "\u00A7cNo entities within 50 blocks.");
        chat(client, "\u00A7e==============================");
    }

    private static String getMatchTag(Entity entity) {
        if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
            return "MATCH=\u00A78N/A";
        }

        boolean matched = HypixelClientState.matches(living);
        boolean locked = entity.getUUID().equals(HypixelClientState.lockedTarget);
        return "MATCH=" + (matched ? "\u00A7aYES" : "\u00A7cNO") + (locked ? " \u00A7e[LOCKED]" : "");
    }

    private static String getBeachballTag(UUID entityId, UUID lockedBallId, UUID lockedCounterId, UUID lockedBoxId) {
        if (entityId.equals(lockedBallId)) return "\u00A7d[BEACHBALL BALL]";
        if (entityId.equals(lockedCounterId)) return "\u00A7d[BEACHBALL COUNTER]";
        if (entityId.equals(lockedBoxId)) return "\u00A7d[BEACHBALL BOX]";
        return "";
    }

    private static String stripFormatting(String s) {
        if (s == null) return "";
        return s.replaceAll("\u00A7.", "");
    }

    private static String formatVec(Vec3 vec) {
        return String.format(Locale.ROOT, "%.3f, %.3f, %.3f", vec.x, vec.y, vec.z);
    }

    private static void chat(Minecraft client, String msg) {
        if (client.player != null) {
            client.player.displayClientMessage(Component.literal(msg), false);
        }
    }

    private record DebugEntry(Entity entity, double dist) {}
}
