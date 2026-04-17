package com.teeko.enemyboxes.client.debug;

import com.teeko.enemyboxes.client.state.EnemyBoxesState;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public final class DebugDump {

    private DebugDump() {}

    public static void dumpNearbyEntities(Minecraft client) {
        if (client.level == null || client.player == null) return;

        Vec3 playerPos = client.player.position();

        record DebugEntry(LivingEntity entity, double dist) {}
        java.util.List<DebugEntry> entries = new java.util.ArrayList<>();

        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity == client.player) continue;
            if (!(entity instanceof LivingEntity living) || !living.isAlive()) continue;
            double distSq = entity.distanceToSqr(playerPos.x, playerPos.y, playerPos.z);
            if (distSq > 2500) continue;
            entries.add(new DebugEntry(living, Math.sqrt(distSq)));
        }

        entries.sort(java.util.Comparator.comparingDouble(DebugEntry::dist));

        chat(client, "\u00A7e=== EnemyBoxes Debug Dump ===");
        chat(client, "\u00A7eActive filters: \u00A7f" + EnemyBoxesState.targets);
        chat(client, "\u00A7eESP enabled: \u00A7f" + EnemyBoxesState.enabled);
        chat(client, "\u00A7eFound \u00A7f" + entries.size() + " \u00A7eliving entities within 50 blocks (sorted by distance)");

        int count = 0;
        for (DebugEntry entry : entries) {
            LivingEntity living = entry.entity();
            count++;

            String scoreRaw = living.getScoreboardName();
            String dispRaw = living.getDisplayName().getString();
            String custRaw = living.hasCustomName() ? living.getCustomName().getString() : "null";
            String teamName = living.getTeam() != null ? living.getTeam().getName() : "null";
            String scoreStripped = scoreRaw.replaceAll("\u00A7.", "");
            String dispStripped = dispRaw.replaceAll("\u00A7.", "");

            StringBuilder hex = new StringBuilder();
            for (char c : scoreRaw.toCharArray()) {
                hex.append(String.format("%04x ", (int) c));
            }

            boolean matched = EnemyBoxesState.matches(living);
            boolean locked = living.getUUID().equals(EnemyBoxesState.lockedTarget);
            String type = net.minecraft.world.entity.EntityType.getKey(living.getType()).toString();

            chat(client, "\u00A77--- #" + count + " dist=" + String.format("%.1f", entry.dist())
                    + " MATCH=" + (matched ? "\u00A7aYES" : "\u00A7cNO")
                    + (locked ? " \u00A7e[LOCKED]" : "") + " \u00A77---");
            chat(client, "\u00A7bType:        \u00A7f" + type);
            chat(client, "\u00A7bScore raw:   \u00A7f[" + scoreRaw + "]");
            chat(client, "\u00A7bScore strip: \u00A7f[" + scoreStripped + "]");
            chat(client, "\u00A7bDisp raw:    \u00A7f[" + dispRaw + "]");
            chat(client, "\u00A7bDisp strip:  \u00A7f[" + dispStripped + "]");
            chat(client, "\u00A7bCustom raw:  \u00A7f[" + custRaw + "]");
            chat(client, "\u00A7bTeam:        \u00A7f" + teamName);
            chat(client, "\u00A7bHex score:   \u00A7f" + hex);
            chat(client, "\u00A7bBbW/H:       \u00A7f" + living.getBbWidth() + " / " + living.getBbHeight());
            chat(client, "\u00A7bEntityY:     \u00A7f" + String.format("%.3f", living.getY()));
            chat(client, "\u00A7bAABB minY:   \u00A7f" + String.format("%.3f", living.getBoundingBox().minY)
                    + "  maxY: " + String.format("%.3f", living.getBoundingBox().maxY));

            if (count >= 6) {
                chat(client, "\u00A7c(capped at 6 entities)");
                break;
            }
        }

        if (entries.isEmpty()) chat(client, "\u00A7cNo living entities within 50 blocks.");
        chat(client, "\u00A7e==============================");
    }

    private static void chat(Minecraft client, String msg) {
        if (client.player != null) {
            client.player.displayClientMessage(Component.literal(msg), false);
        }
    }
}
