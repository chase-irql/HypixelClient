package com.teeko.enemyboxes.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.UUID;

public final class EnemyBoxesClient implements ClientModInitializer {
    public static final String MOD_ID = "enemyboxes";

    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.tryParse(MOD_ID + ":controls"));

    private static final KeyMapping OPEN_MENU_KEY =
            KeyBindingHelper.registerKeyBinding(new KeyMapping(
                    "key.enemyboxes.open_menu",
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_RIGHT_SHIFT,
                    CATEGORY
            ));

    private static final KeyMapping LOCK_ON_KEY =
            KeyBindingHelper.registerKeyBinding(new KeyMapping(
                    "key.enemyboxes.lock_on",
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_Z,
                    CATEGORY
            ));

    private static final KeyMapping DEBUG_KEY =
            KeyBindingHelper.registerKeyBinding(new KeyMapping(
                    "key.enemyboxes.debug",
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_F8,
                    CATEGORY
            ));

    @Override
    public void onInitializeClient() {
        HudRenderCallback.EVENT.register((guiGraphics, deltaTracker) -> {
            EnemyBoxesHud.render(guiGraphics);
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_MENU_KEY.consumeClick()) {
                client.setScreen(new EnemyBoxesScreen(client.screen));
            }

            while (DEBUG_KEY.consumeClick()) {
                dumpNearbyEntities(client);
            }

            if (!LOCK_ON_KEY.isDown()) {
                EnemyBoxesState.lockedTarget = null;
                EnemyBoxesAim.reset();
            } else {
                updateLockOn(client);
            }
        });
    }

    private static void dumpNearbyEntities(Minecraft client) {
        if (client.level == null || client.player == null) return;

        Vec3 playerPos = client.player.position();
        int count = 0;

        chat(client, "§e=== EnemyBoxes Debug Dump ===");
        chat(client, "§eActive filters: §f" + EnemyBoxesState.targetNames);
        chat(client, "§eESP enabled: §f" + EnemyBoxesState.enabled);
        chat(client, "§eTotal entities in render list: §f" + client.level.entitiesForRendering().spliterator().getExactSizeIfKnown());

        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity == client.player) continue;
            if (!(entity instanceof LivingEntity living) || !living.isAlive()) continue;

            double dist = entity.distanceToSqr(playerPos.x, playerPos.y, playerPos.z);
            if (dist > 2500) continue; // 50 block radius

            count++;

            // Raw strings (unstripped) to catch hidden chars
            String scoreRaw   = entity.getScoreboardName();
            String dispRaw    = entity.getDisplayName().getString();
            String custRaw    = entity.hasCustomName() ? entity.getCustomName().getString() : "null";
            String teamName   = entity.getTeam() != null ? entity.getTeam().getName() : "null";

            // Stripped versions
            String scoreStripped = scoreRaw.replaceAll("§.", "");
            String dispStripped  = dispRaw.replaceAll("§.", "");

            // Hex dump of scoreboard name to catch invisible/non-ASCII chars
            StringBuilder hex = new StringBuilder();
            for (char c : scoreRaw.toCharArray()) {
                hex.append(String.format("%04x ", (int) c));
            }

            boolean matched = EnemyBoxesState.matches(living);

            float  bbW     = entity.getBbWidth();
            float  bbH     = entity.getBbHeight();
            double entityY = entity.getY();
            double aabbMinY = entity.getBoundingBox().minY;
            double aabbMaxY = entity.getBoundingBox().maxY;
            String type    = net.minecraft.world.entity.EntityType.getKey(entity.getType()).toString();

            chat(client, "§7--- #" + count + " dist=" + String.format("%.1f", Math.sqrt(dist)) + " MATCH=" + (matched ? "§aYES" : "§cNO") + " ---");
            chat(client, "§bType:        §f" + type);
            chat(client, "§bScore raw:   §f[" + scoreRaw + "]");
            chat(client, "§bScore strip: §f[" + scoreStripped + "]");
            chat(client, "§bDisp raw:    §f[" + dispRaw + "]");
            chat(client, "§bDisp strip:  §f[" + dispStripped + "]");
            chat(client, "§bCustom raw:  §f[" + custRaw + "]");
            chat(client, "§bTeam:        §f" + teamName);
            chat(client, "§bHex score:   §f" + hex);
            chat(client, "§bBbW/H:       §f" + bbW + " / " + bbH);
            chat(client, "§bEntityY:     §f" + String.format("%.3f", entityY));
            chat(client, "§bAABB minY:   §f" + String.format("%.3f", aabbMinY) + "  maxY: " + String.format("%.3f", aabbMaxY));

            if (count >= 6) {
                chat(client, "§c(capped at 6 entities)");
                break;
            }
        }

        if (count == 0) {
            chat(client, "§cNo living entities within 50 blocks.");
        }

        chat(client, "§e==============================");
    }

    private static void chat(Minecraft client, String msg) {
        if (client.player != null) {
            client.player.displayClientMessage(Component.literal(msg), false);
        }
    }

    private static void updateLockOn(Minecraft client) {
        if (client.level == null || client.player == null) return;
        if (!EnemyBoxesState.enabled || !EnemyBoxesState.hasTarget()) return;

        if (EnemyBoxesState.lockedTarget != null) {
            Entity lockedEntity = null;
            for (Entity entity : client.level.entitiesForRendering()) {
                if (entity.getUUID().equals(EnemyBoxesState.lockedTarget)) {
                    lockedEntity = entity;
                    break;
                }
            }

            if (lockedEntity instanceof LivingEntity living && living.isAlive()
                    && EnemyBoxesState.matches(living)) {
                return;
            } else {
                EnemyBoxesState.lockedTarget = null;
                EnemyBoxesAim.reset();
            }
        }

        Vec3 camPos     = client.player.getEyePosition(1.0f);
        Vec3 camForward = client.player.getViewVector(1.0f);
        Vec3 worldUp    = new Vec3(0, 1, 0);
        Vec3 camRight   = camForward.cross(worldUp).normalize();
        Vec3 camUp      = camRight.cross(camForward).normalize();

        double halfH       = client.getWindow().getGuiScaledHeight() / 2.0;
        double fovY        = client.options.fov().get();
        double tanHalfFovY = Math.tan(Math.toRadians(fovY / 2.0));

        double bestScreenDist = Double.MAX_VALUE;
        UUID bestTarget = null;

        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity == client.player) continue;
            if (!(entity instanceof LivingEntity living) || !living.isAlive()) continue;
            if (!EnemyBoxesState.matches(living)) continue;

            net.minecraft.world.phys.AABB box = entity.getBoundingBox();
            Vec3[] points = {
                    new Vec3(box.minX, box.minY, box.minZ),
                    new Vec3(box.maxX, box.minY, box.minZ),
                    new Vec3(box.minX, box.maxY, box.minZ),
                    new Vec3(box.maxX, box.maxY, box.minZ),
                    new Vec3(box.minX, box.minY, box.maxZ),
                    new Vec3(box.maxX, box.minY, box.maxZ),
                    new Vec3(box.minX, box.maxY, box.maxZ),
                    new Vec3(box.maxX, box.maxY, box.maxZ),
                    entity.getEyePosition(1.0f)
            };

            double bestPointScreenDist = Double.MAX_VALUE;
            boolean anyPointInCircle = false;

            for (Vec3 point : points) {
                Vec3 toPoint = point.subtract(camPos);
                double dot = toPoint.normalize().dot(camForward);
                if (dot <= 0) continue;

                double forward = toPoint.dot(camForward);
                double screenX = toPoint.dot(camRight) / forward;
                double screenY = -toPoint.dot(camUp) / forward;

                double pixelX = screenX * halfH / tanHalfFovY;
                double pixelY = screenY * halfH / tanHalfFovY;
                double pixelDist = Math.sqrt(pixelX * pixelX + pixelY * pixelY);

                if (pixelDist <= EnemyBoxesState.lockFov) {
                    anyPointInCircle = true;
                    if (pixelDist < bestPointScreenDist) {
                        bestPointScreenDist = pixelDist;
                    }
                }
            }

            if (anyPointInCircle && bestPointScreenDist < bestScreenDist) {
                bestScreenDist = bestPointScreenDist;
                bestTarget = entity.getUUID();
            }
        }

        EnemyBoxesState.lockedTarget = bestTarget;
    }

    public static void notifyTargetSaved() {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.displayClientMessage(Component.literal(
                    "[EnemyBoxes] Tracking " + EnemyBoxesState.targetNames.size() + " target(s)"
            ), false);
        }
    }
}