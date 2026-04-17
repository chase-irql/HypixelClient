package com.teeko.enemyboxes.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.teeko.enemyboxes.client.mixin.MinecraftAccessor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Random;
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

    private static final KeyMapping TOGGLE_HUNT_KEY =
            KeyBindingHelper.registerKeyBinding(new KeyMapping(
                    "key.enemyboxes.toggle_auto_hunt",
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_H,
                    CATEGORY
            ));

    private static boolean reactionPending    = false;
    private static long    reactionFireMs     = 0;
    private static UUID    lastAutoSwingTarget = null;
    private static long    nextHuntUseMs      = -1;
    private static UUID    lastHuntUseTarget  = null;

    private static final long   HUNT_INITIAL_USE_DELAY_MS = 100;
    private static final double HUNT_USE_DELAY_JITTER     = 0.10;

    private static final Random              swingRandom  = new Random();
    private static final ArrayDeque<Long>    attackTimes  = new ArrayDeque<>();

    @Override
    public void onInitializeClient() {
        EnemyBoxesConfig.load();

        HudRenderCallback.EVENT.register((guiGraphics, deltaTracker) ->
                EnemyBoxesHud.render(guiGraphics, deltaTracker.getGameTimeDeltaPartialTick(false)));

        // Chat listener for shard-drop detection — overlay=true means action bar, skip those
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) HideonleafShardTracker.onChatMessage(message);
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_MENU_KEY.consumeClick()) {
                client.setScreen(new EnemyBoxesScreen(client.screen));
            }

            while (DEBUG_KEY.consumeClick()) {
                dumpNearbyEntities(client);
            }

            while (TOGGLE_HUNT_KEY.consumeClick()) {
                EnemyBoxesState.autoHuntEnabled = !EnemyBoxesState.autoHuntEnabled;
                if (!EnemyBoxesState.autoHuntEnabled) {
                    HideonleafHunt.resetState();
                    resetHuntUseState();
                }
                EnemyBoxesConfig.save();
                notifyAutoHuntToggled(client);
            }

            if (!LOCK_ON_KEY.isDown()) {
                EnemyBoxesState.lockedTarget = null;
                EnemyBoxesAim.reset();
                resetAutoSwingState();
            } else {
                updateLockOn(client);
            }

            HideonleafHunt.tick(client);
            tryAutoHuntUse(client);

            if (EnemyBoxesState.shardTrackerEnabled) {
                HideonleafShardTracker.tickRefresh();
            }
        });
    }

    // -------------------------------------------------------------------------
    // CPS tracking
    // -------------------------------------------------------------------------

    public static void recordAttack() {
        long now = System.currentTimeMillis();
        attackTimes.addLast(now);
        while (!attackTimes.isEmpty() && now - attackTimes.peekFirst() > 1000) {
            attackTimes.pollFirst();
        }
    }

    public static int getCps() {
        long now = System.currentTimeMillis();
        attackTimes.removeIf(t -> now - t > 1000);
        return attackTimes.size();
    }

    // -------------------------------------------------------------------------
    // Auto swing
    // -------------------------------------------------------------------------

    /**
     * Returns true only when the player is fully in-game and able to act:
     * the game is not paused, no chat screen is open, and no inventory/GUI screen is open.
     * All three conditions are covered by checking isPaused() and screen == null.
     */
    public static boolean isPlayerActive(Minecraft client) {
        return !client.isPaused() && client.screen == null;
    }

    public static void tickAutoSwing(Minecraft client) {
        if (client.level == null || client.player == null || client.gameMode == null) {
            resetAutoSwingState();
            return;
        }
        if (!isPlayerActive(client)) {
            pauseAutoSwingState();
            return;
        }

        UUID attackTarget = getAutoSwingTarget();
        if (attackTarget == null) {
            resetAutoSwingState();
            return;
        }

        Entity target = findEntityByUuid(client, attackTarget);
        if (!(target instanceof LivingEntity living) || !living.isAlive()) {
            resetAutoSwingState();
            return;
        }

        boolean isNewTarget = !attackTarget.equals(lastAutoSwingTarget);
        if (isNewTarget) {
            resetAutoSwingState();
            lastAutoSwingTarget = attackTarget;
        }

        // Auto-swing should only pulse when vanilla is actually targeting the
        // locked entity for this frame and the entity is within interaction reach.
        if (!inReach(client, target) || !isCurrentHitTarget(client, target)) {
            pauseAutoSwingState();
            return;
        }

        long now = System.currentTimeMillis();
        if (isNewTarget && EnemyBoxesState.randomizeReactionDelay) {
            reactionPending = true;
            reactionFireMs = now + nextTriangularDelay(
                    EnemyBoxesState.reactionDelayMin,
                    EnemyBoxesState.reactionDelayMode,
                    EnemyBoxesState.reactionDelayMax
            );
        }

        if (reactionPending) {
            if (now < reactionFireMs) return;
            reactionPending = false;
        }

        if (!AutoClicker.shouldFireAutoSwing()) return;

        AutoClicker.fireAttack(client);
    }

    private static void tryAutoHuntUse(Minecraft client) {
        if (client.level == null || client.player == null || client.gameMode == null) return;
        if (!isPlayerActive(client)) return;

        UUID huntTarget = EnemyBoxesState.autoHuntEnabled ? EnemyBoxesState.huntLockedBullet : null;
        if (huntTarget == null) {
            resetHuntUseState();
            return;
        }

        long now = System.currentTimeMillis();
        if (!huntTarget.equals(lastHuntUseTarget)) {
            lastHuntUseTarget = huntTarget;
            nextHuntUseMs = now + nextJitteredDelay(HUNT_INITIAL_USE_DELAY_MS, HUNT_USE_DELAY_JITTER);
            return;
        }

        if (now < nextHuntUseMs) return;

        for (Entity entity : client.level.entitiesForRendering()) {
            if (!entity.getUUID().equals(huntTarget)) continue;
            if (!entity.isAlive()) {
                resetHuntUseState();
                break;
            }
            if (!inHuntUseReach(client, entity)) {
                resetHuntUseState();
                break;
            }

            ((MinecraftAccessor) client).enemyboxes$invokeStartUseItem();
            HideonleafHunt.markBulletUsed(huntTarget);
            resetHuntUseState();
            break;
        }
    }

    private static void resetAutoSwingState() {
        reactionPending = false;
        reactionFireMs = 0;
        lastAutoSwingTarget = null;
        AutoClicker.resetAutoSwingState();
    }

    private static void resetHuntUseState() {
        nextHuntUseMs = -1;
        lastHuntUseTarget = null;
    }

    private static void pauseAutoSwingState() {
        reactionPending = false;
        AutoClicker.releaseAutoSwing();
    }

    private static boolean inReach(Minecraft client, Entity entity) {
        return isWithinReach(client, entity, client.player.entityInteractionRange());
    }

    private static boolean inHuntUseReach(Minecraft client, Entity entity) {
        return isWithinReach(client, entity, client.player.entityInteractionRange() + 3.5);
    }

    private static boolean isWithinReach(Minecraft client, Entity entity, double reach) {
        Vec3 eyePos = client.player.getEyePosition(1.0f);
        var box = entity.getBoundingBox();
        double closestX = clamp(eyePos.x, box.minX, box.maxX);
        double closestY = clamp(eyePos.y, box.minY, box.maxY);
        double closestZ = clamp(eyePos.z, box.minZ, box.maxZ);
        return eyePos.distanceToSqr(closestX, closestY, closestZ) <= reach * reach;
    }

    private static boolean isCurrentHitTarget(Minecraft client, Entity entity) {
        if (!(client.hitResult instanceof EntityHitResult hit)) return false;
        return hit.getEntity().getUUID().equals(entity.getUUID());
    }

    private static boolean hasLineOfSight(Minecraft client, Entity entity) {
        Vec3 eyePos    = client.player.getEyePosition(1.0f);
        Vec3 targetPos = entity.getEyePosition(1.0f);

        BlockHitResult hit = client.level.clip(new ClipContext(
                eyePos, targetPos,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                client.player
        ));

        return hit.getType() == HitResult.Type.MISS;
    }

    /** Returns true if any part of the entity's bounding box (corners, edges,
     *  or eye position) projects within the FOV circle radius. */
    private static boolean isInFovCircle(Minecraft client, Entity entity) {
        Vec3 camPos     = client.player.getEyePosition(1.0f);
        Vec3 camForward = client.player.getViewVector(1.0f);
        Vec3 worldUp    = new Vec3(0, 1, 0);
        Vec3 camRight   = camForward.cross(worldUp).normalize();
        Vec3 camUp      = camRight.cross(camForward).normalize();

        double halfH       = client.getWindow().getGuiScaledHeight() / 2.0;
        double fovY        = client.options.fov().get();
        double tanHalfFovY = Math.tan(Math.toRadians(fovY / 2.0));
        double fovR        = EnemyBoxesState.lockFov;

        net.minecraft.world.phys.AABB box = entity.getBoundingBox();

        // Project a world point to pixel-space; null = behind camera
        java.util.function.Function<Vec3, double[]> project = (pt) -> {
            Vec3 rel = pt.subtract(camPos);
            if (rel.normalize().dot(camForward) <= 0) return null;
            double fwd = rel.dot(camForward);
            double sx  = rel.dot(camRight) / fwd * halfH / tanHalfFovY;
            double sy  = -rel.dot(camUp)   / fwd * halfH / tanHalfFovY;
            return new double[]{ sx, sy };
        };

        Vec3[] corners = {
                new Vec3(box.minX, box.minY, box.minZ), new Vec3(box.maxX, box.minY, box.minZ),
                new Vec3(box.minX, box.maxY, box.minZ), new Vec3(box.maxX, box.maxY, box.minZ),
                new Vec3(box.minX, box.minY, box.maxZ), new Vec3(box.maxX, box.minY, box.maxZ),
                new Vec3(box.minX, box.maxY, box.maxZ), new Vec3(box.maxX, box.maxY, box.maxZ),
        };
        double[][] sc = new double[8][];
        for (int i = 0; i < 8; i++) sc[i] = project.apply(corners[i]);

        // Check corners
        for (double[] p : sc) {
            if (p != null && Math.sqrt(p[0]*p[0] + p[1]*p[1]) <= fovR) return true;
        }

        // Check edge closest points
        int[][] edges = {
                {0,1},{2,3},{4,5},{6,7},
                {0,2},{1,3},{4,6},{5,7},
                {0,4},{1,5},{2,6},{3,7}
        };
        for (int[] edge : edges) {
            double[] a = sc[edge[0]], b = sc[edge[1]];
            if (a == null || b == null) continue;
            double dx = b[0]-a[0], dy = b[1]-a[1];
            double lenSq = dx*dx + dy*dy;
            double t = lenSq > 0 ? Math.max(0, Math.min(1, (-a[0]*dx + -a[1]*dy) / lenSq)) : 0;
            double cx = a[0] + t*dx, cy = a[1] + t*dy;
            if (Math.sqrt(cx*cx + cy*cy) <= fovR) return true;
        }

        // Check eye position
        double[] eye = project.apply(entity.getEyePosition(1.0f));
        return eye != null && Math.sqrt(eye[0]*eye[0] + eye[1]*eye[1]) <= fovR;
    }

    private static long nextTriangularDelay(int min, int mode, int max) {
        if (min >= max) return min;
        float fMin  = min;
        float fMax  = max;
        float fMode = Math.max(fMin, Math.min(fMax, mode));
        float fc    = (fMode - fMin) / (fMax - fMin);
        float u     = swingRandom.nextFloat();
        float val;
        if (u < fc) {
            val = fMin + (float) Math.sqrt(u * (fMax - fMin) * (fMode - fMin));
        } else {
            val = fMax - (float) Math.sqrt((1f - u) * (fMax - fMin) * (fMax - fMode));
        }
        return Math.max(min, (long) val);
    }

    private static long nextJitteredDelay(long baseDelayMs, double jitterFraction) {
        if (baseDelayMs <= 0) return 0;
        double spread = baseDelayMs * jitterFraction;
        double offset = (swingRandom.nextDouble() * 2.0 - 1.0) * spread;
        return Math.max(0L, Math.round(baseDelayMs + offset));
    }

    // -------------------------------------------------------------------------
    // Lock-on
    // -------------------------------------------------------------------------

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
                    && EnemyBoxesState.matches(living)
                    && hasLineOfSight(client, lockedEntity)
                    && isInFovCircle(client, lockedEntity)) {
                return;
            } else {
                EnemyBoxesState.lockedTarget = null;
                EnemyBoxesAim.reset();
                reactionPending = false;
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

        double bestScore  = Double.MAX_VALUE;
        UUID   bestTarget = null;

        // First pass: collect raw values so we can normalize them
        record Candidate(UUID uuid, double screenDist, double worldDist) {}
        java.util.List<Candidate> candidates = new java.util.ArrayList<>();

        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity == client.player) continue;
            if (!(entity instanceof LivingEntity living) || !living.isAlive()) continue;
            if (!EnemyBoxesState.matches(living)) continue;
            if (!hasLineOfSight(client, entity)) continue;

            net.minecraft.world.phys.AABB box = entity.getBoundingBox();

            // Project a world point into 2D pixel-space relative to crosshair center.
            // Returns null if the point is behind the camera.
            record ScreenPt(double px, double py) {}
            java.util.function.Function<Vec3, ScreenPt> project = (pt) -> {
                Vec3 rel = pt.subtract(camPos);
                if (rel.normalize().dot(camForward) <= 0) return null;
                double fwd = rel.dot(camForward);
                double sx  = rel.dot(camRight) / fwd;
                double sy  = -rel.dot(camUp)   / fwd;
                return new ScreenPt(sx * halfH / tanHalfFovY, sy * halfH / tanHalfFovY);
            };

            // All 8 corners as screen points (null = behind camera, skip)
            Vec3[] corners = {
                    new Vec3(box.minX, box.minY, box.minZ),
                    new Vec3(box.maxX, box.minY, box.minZ),
                    new Vec3(box.minX, box.maxY, box.minZ),
                    new Vec3(box.maxX, box.maxY, box.minZ),
                    new Vec3(box.minX, box.minY, box.maxZ),
                    new Vec3(box.maxX, box.minY, box.maxZ),
                    new Vec3(box.minX, box.maxY, box.maxZ),
                    new Vec3(box.maxX, box.maxY, box.maxZ),
            };
            ScreenPt[] sc = new ScreenPt[8];
            for (int i = 0; i < 8; i++) sc[i] = project.apply(corners[i]);

            // The 12 edges of the box defined by corner index pairs
            int[][] edges = {
                    {0,1},{2,3},{4,5},{6,7}, // bottom/top along X
                    {0,2},{1,3},{4,6},{5,7}, // along Y
                    {0,4},{1,5},{2,6},{3,7}  // along Z
            };

            double fovR = EnemyBoxesState.lockFov;
            double bestScreenDist = Double.MAX_VALUE;
            boolean anyInCircle = false;

            // Check each corner point
            for (ScreenPt p : sc) {
                if (p == null) continue;
                double d = Math.sqrt(p.px() * p.px() + p.py() * p.py());
                if (d <= fovR) {
                    anyInCircle = true;
                    if (d < bestScreenDist) bestScreenDist = d;
                }
            }

            // Check closest point on each projected edge to the crosshair (0,0).
            // This catches the case where an edge passes through the circle but
            // neither endpoint lands inside it.
            for (int[] edge : edges) {
                ScreenPt a = sc[edge[0]];
                ScreenPt b = sc[edge[1]];
                if (a == null || b == null) continue;

                double ax = a.px(), ay = a.py();
                double bx = b.px(), by = b.py();
                double dx = bx - ax,  dy = by - ay;
                double lenSq = dx * dx + dy * dy;

                // t = dot(origin - a, b - a) / |b-a|^2, clamped to [0,1]
                double t = lenSq > 0 ? Math.max(0, Math.min(1, (-ax * dx + -ay * dy) / lenSq)) : 0;
                double cx = ax + t * dx;
                double cy = ay + t * dy;
                double d  = Math.sqrt(cx * cx + cy * cy);

                if (d <= fovR) {
                    anyInCircle = true;
                    if (d < bestScreenDist) bestScreenDist = d;
                }
            }

            // Also check eye position as a bonus point
            ScreenPt eye = project.apply(entity.getEyePosition(1.0f));
            if (eye != null) {
                double d = Math.sqrt(eye.px() * eye.px() + eye.py() * eye.py());
                if (d <= fovR) {
                    anyInCircle = true;
                    if (d < bestScreenDist) bestScreenDist = d;
                }
            }

            if (anyInCircle) {
                double worldDist = client.player.distanceTo(entity);
                candidates.add(new Candidate(entity.getUUID(), bestScreenDist, worldDist));
            }
        }

        if (!candidates.isEmpty()) {
            // Normalize screen distance linearly across the candidate set
            double maxScreen = candidates.stream().mapToDouble(c -> c.screenDist()).max().orElse(1.0);
            if (maxScreen == 0) maxScreen = 1.0;

            // World distance uses exponential decay so proximity bias is strongly
            // felt — a target at 5 blocks scores much better than one at 20 blocks,
            // rather than a gentle linear slope. Falloff ~10 blocks feels natural
            // for typical Minecraft PvP ranges.
            final double FALLOFF = 10.0;

            float t = EnemyBoxesState.aimPriorityBlend;

            for (Candidate c : candidates) {
                double normScreen = c.screenDist() / maxScreen;
                // exp decay: 0 at distance=0, approaches 1 as distance → ∞
                double normWorld  = 1.0 - Math.exp(-c.worldDist() / FALLOFF);
                double score      = (1.0 - t) * normScreen + t * normWorld;
                if (score < bestScore) {
                    bestScore  = score;
                    bestTarget = c.uuid();
                }
            }
        }

        EnemyBoxesState.lockedTarget = bestTarget;
    }

    // -------------------------------------------------------------------------
    // Debug dump
    // -------------------------------------------------------------------------

    private static void dumpNearbyEntities(Minecraft client) {
        if (client.level == null || client.player == null) return;

        Vec3 playerPos = client.player.position();

        // Collect all living entities within 50 blocks, sorted closest first
        record DebugEntry(LivingEntity entity, double dist) {}
        java.util.List<DebugEntry> entries = new java.util.ArrayList<>();

        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity == client.player) continue;
            if (!(entity instanceof LivingEntity living) || !living.isAlive()) continue;
            double distSq = entity.distanceToSqr(playerPos.x, playerPos.y, playerPos.z);
            if (distSq > 2500) continue;
            entries.add(new DebugEntry(living, Math.sqrt(distSq)));
        }

        entries.sort(java.util.Comparator.comparingDouble(e -> e.dist()));

        chat(client, "§e=== EnemyBoxes Debug Dump ===");
        chat(client, "§eActive filters: §f" + EnemyBoxesState.targets);
        chat(client, "§eESP enabled: §f" + EnemyBoxesState.enabled);
        chat(client, "§eFound §f" + entries.size() + " §eliving entities within 50 blocks (sorted by distance)");

        int count = 0;
        for (DebugEntry e : entries) {
            LivingEntity living = e.entity();
            count++;

            String scoreRaw      = living.getScoreboardName();
            String dispRaw       = living.getDisplayName().getString();
            String custRaw       = living.hasCustomName() ? living.getCustomName().getString() : "null";
            String teamName      = living.getTeam() != null ? living.getTeam().getName() : "null";
            String scoreStripped = scoreRaw.replaceAll("§.", "");
            String dispStripped  = dispRaw.replaceAll("§.", "");

            StringBuilder hex = new StringBuilder();
            for (char c : scoreRaw.toCharArray()) hex.append(String.format("%04x ", (int) c));

            boolean matched = EnemyBoxesState.matches(living);
            boolean locked  = living.getUUID().equals(EnemyBoxesState.lockedTarget);
            String type     = net.minecraft.world.entity.EntityType.getKey(living.getType()).toString();

            chat(client, "§7--- #" + count + " dist=" + String.format("%.1f", e.dist())
                    + " MATCH=" + (matched ? "§aYES" : "§cNO")
                    + (locked ? " §e[LOCKED]" : "") + " §7---");
            chat(client, "§bType:        §f" + type);
            chat(client, "§bScore raw:   §f[" + scoreRaw + "]");
            chat(client, "§bScore strip: §f[" + scoreStripped + "]");
            chat(client, "§bDisp raw:    §f[" + dispRaw + "]");
            chat(client, "§bDisp strip:  §f[" + dispStripped + "]");
            chat(client, "§bCustom raw:  §f[" + custRaw + "]");
            chat(client, "§bTeam:        §f" + teamName);
            chat(client, "§bHex score:   §f" + hex);
            chat(client, "§bBbW/H:       §f" + living.getBbWidth() + " / " + living.getBbHeight());
            chat(client, "§bEntityY:     §f" + String.format("%.3f", living.getY()));
            chat(client, "§bAABB minY:   §f" + String.format("%.3f", living.getBoundingBox().minY)
                    + "  maxY: " + String.format("%.3f", living.getBoundingBox().maxY));

            if (count >= 6) { chat(client, "§c(capped at 6 entities)"); break; }
        }

        if (entries.isEmpty()) chat(client, "§cNo living entities within 50 blocks.");
        chat(client, "§e==============================");
    }

    private static void chat(Minecraft client, String msg) {
        if (client.player != null)
            client.player.displayClientMessage(Component.literal(msg), false);
    }

    public static void notifyTargetSaved() {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.displayClientMessage(Component.literal(
                    "[EnemyBoxes] Tracking " + EnemyBoxesState.targets.size() + " target(s)"
            ), false);
        }
    }

    private static UUID getAutoSwingTarget() {
        if (EnemyBoxesState.autoSwingEnabled && EnemyBoxesState.lockedTarget != null) {
            return EnemyBoxesState.lockedTarget;
        }

        return null;
    }

    private static Entity findEntityByUuid(Minecraft client, UUID uuid) {
        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity.getUUID().equals(uuid)) return entity;
        }
        return null;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void notifyAutoHuntToggled(Minecraft client) {
        if (client.player != null) {
            client.player.displayClientMessage(Component.literal(
                    "[EnemyBoxes] Auto Hunt: " + (EnemyBoxesState.autoHuntEnabled ? "ON" : "OFF")
            ), false);
        }
    }
}
