package com.teeko.enemyboxes.client.feature.lockon;

import com.teeko.enemyboxes.client.EnemyBoxesClient;
import com.teeko.enemyboxes.client.combat.AutoClicker;
import com.teeko.enemyboxes.client.feature.fishing.FishingCombat;
import com.teeko.enemyboxes.client.feature.hideonleaf.HideonleafHunt;
import com.teeko.enemyboxes.client.mixin.accessor.MinecraftAccessor;
import com.teeko.enemyboxes.client.state.EnemyBoxesState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Random;
import java.util.UUID;

public final class LockOnController {

    private static boolean reactionPending = false;
    private static long reactionFireMs = 0;
    private static UUID lastAutoSwingTarget = null;
    private static long nextHuntUseMs = -1;
    private static UUID lastHuntUseTarget = null;

    private static final long HUNT_INITIAL_USE_DELAY_MS = 100;
    private static final double HUNT_USE_DELAY_JITTER = 0.10;

    private static final Random SWING_RANDOM = new Random();

    private LockOnController() {}

    public static void clearLock() {
        EnemyBoxesState.lockedTarget = null;
        EnemyBoxesAim.reset();
        resetAutoSwingState();
    }

    public static void updateLockOn(Minecraft client) {
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

        Vec3 camPos = client.player.getEyePosition(1.0f);
        Vec3 camForward = client.player.getViewVector(1.0f);
        Vec3 worldUp = new Vec3(0, 1, 0);
        Vec3 camRight = camForward.cross(worldUp).normalize();
        Vec3 camUp = camRight.cross(camForward).normalize();

        double halfH = client.getWindow().getGuiScaledHeight() / 2.0;
        double fovY = client.options.fov().get();
        double tanHalfFovY = Math.tan(Math.toRadians(fovY / 2.0));

        double bestScore = Double.MAX_VALUE;
        UUID bestTarget = null;

        record Candidate(UUID uuid, double screenDist, double worldDist) {}
        java.util.List<Candidate> candidates = new java.util.ArrayList<>();

        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity == client.player) continue;
            if (!(entity instanceof LivingEntity living) || !living.isAlive()) continue;
            if (!EnemyBoxesState.matches(living)) continue;
            if (!hasLineOfSight(client, entity)) continue;

            net.minecraft.world.phys.AABB box = entity.getBoundingBox();

            record ScreenPt(double px, double py) {}
            java.util.function.Function<Vec3, ScreenPt> project = (pt) -> {
                Vec3 rel = pt.subtract(camPos);
                if (rel.normalize().dot(camForward) <= 0) return null;
                double fwd = rel.dot(camForward);
                double sx = rel.dot(camRight) / fwd;
                double sy = -rel.dot(camUp) / fwd;
                return new ScreenPt(sx * halfH / tanHalfFovY, sy * halfH / tanHalfFovY);
            };

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

            int[][] edges = {
                    {0, 1}, {2, 3}, {4, 5}, {6, 7},
                    {0, 2}, {1, 3}, {4, 6}, {5, 7},
                    {0, 4}, {1, 5}, {2, 6}, {3, 7}
            };

            double fovR = EnemyBoxesState.lockFov;
            double bestScreenDist = Double.MAX_VALUE;
            boolean anyInCircle = false;

            for (ScreenPt point : sc) {
                if (point == null) continue;
                double dist = Math.sqrt(point.px() * point.px() + point.py() * point.py());
                if (dist <= fovR) {
                    anyInCircle = true;
                    if (dist < bestScreenDist) bestScreenDist = dist;
                }
            }

            for (int[] edge : edges) {
                ScreenPt a = sc[edge[0]];
                ScreenPt b = sc[edge[1]];
                if (a == null || b == null) continue;

                double ax = a.px();
                double ay = a.py();
                double bx = b.px();
                double by = b.py();
                double dx = bx - ax;
                double dy = by - ay;
                double lenSq = dx * dx + dy * dy;

                double t = lenSq > 0 ? Math.max(0, Math.min(1, (-ax * dx + -ay * dy) / lenSq)) : 0;
                double cx = ax + t * dx;
                double cy = ay + t * dy;
                double dist = Math.sqrt(cx * cx + cy * cy);

                if (dist <= fovR) {
                    anyInCircle = true;
                    if (dist < bestScreenDist) bestScreenDist = dist;
                }
            }

            ScreenPt eye = project.apply(entity.getEyePosition(1.0f));
            if (eye != null) {
                double dist = Math.sqrt(eye.px() * eye.px() + eye.py() * eye.py());
                if (dist <= fovR) {
                    anyInCircle = true;
                    if (dist < bestScreenDist) bestScreenDist = dist;
                }
            }

            if (anyInCircle) {
                double worldDist = client.player.distanceTo(entity);
                candidates.add(new Candidate(entity.getUUID(), bestScreenDist, worldDist));
            }
        }

        if (!candidates.isEmpty()) {
            double maxScreen = candidates.stream().mapToDouble(Candidate::screenDist).max().orElse(1.0);
            if (maxScreen == 0) maxScreen = 1.0;

            final double falloff = 10.0;
            float blend = EnemyBoxesState.aimPriorityBlend;

            for (Candidate candidate : candidates) {
                double normScreen = candidate.screenDist() / maxScreen;
                double normWorld = 1.0 - Math.exp(-candidate.worldDist() / falloff);
                double score = (1.0 - blend) * normScreen + blend * normWorld;
                if (score < bestScore) {
                    bestScore = score;
                    bestTarget = candidate.uuid();
                }
            }
        }

        EnemyBoxesState.lockedTarget = bestTarget;
    }

    public static void tickAutoSwing(Minecraft client) {
        if (client.level == null || client.player == null || client.gameMode == null) {
            resetAutoSwingState();
            return;
        }
        if (!EnemyBoxesClient.isPlayerActive(client)) {
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

    public static void tickAutoHuntUse(Minecraft client) {
        if (client.level == null || client.player == null || client.gameMode == null) return;
        if (!EnemyBoxesClient.isPlayerActive(client)) return;

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

    public static void resetHuntUseState() {
        nextHuntUseMs = -1;
        lastHuntUseTarget = null;
    }

    private static void resetAutoSwingState() {
        reactionPending = false;
        reactionFireMs = 0;
        lastAutoSwingTarget = null;
        AutoClicker.resetAutoSwingState();
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
        Vec3 eyePos = client.player.getEyePosition(1.0f);
        Vec3 targetPos = entity.getEyePosition(1.0f);

        BlockHitResult hit = client.level.clip(new ClipContext(
                eyePos, targetPos,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                client.player
        ));

        return hit.getType() == HitResult.Type.MISS;
    }

    private static boolean isInFovCircle(Minecraft client, Entity entity) {
        Vec3 camPos = client.player.getEyePosition(1.0f);
        Vec3 camForward = client.player.getViewVector(1.0f);
        Vec3 worldUp = new Vec3(0, 1, 0);
        Vec3 camRight = camForward.cross(worldUp).normalize();
        Vec3 camUp = camRight.cross(camForward).normalize();

        double halfH = client.getWindow().getGuiScaledHeight() / 2.0;
        double fovY = client.options.fov().get();
        double tanHalfFovY = Math.tan(Math.toRadians(fovY / 2.0));
        double fovR = EnemyBoxesState.lockFov;

        net.minecraft.world.phys.AABB box = entity.getBoundingBox();

        java.util.function.Function<Vec3, double[]> project = (pt) -> {
            Vec3 rel = pt.subtract(camPos);
            if (rel.normalize().dot(camForward) <= 0) return null;
            double fwd = rel.dot(camForward);
            double sx = rel.dot(camRight) / fwd * halfH / tanHalfFovY;
            double sy = -rel.dot(camUp) / fwd * halfH / tanHalfFovY;
            return new double[]{sx, sy};
        };

        Vec3[] corners = {
                new Vec3(box.minX, box.minY, box.minZ), new Vec3(box.maxX, box.minY, box.minZ),
                new Vec3(box.minX, box.maxY, box.minZ), new Vec3(box.maxX, box.maxY, box.minZ),
                new Vec3(box.minX, box.minY, box.maxZ), new Vec3(box.maxX, box.minY, box.maxZ),
                new Vec3(box.minX, box.maxY, box.maxZ), new Vec3(box.maxX, box.maxY, box.maxZ),
        };
        double[][] projectedCorners = new double[8][];
        for (int i = 0; i < 8; i++) projectedCorners[i] = project.apply(corners[i]);

        for (double[] point : projectedCorners) {
            if (point != null && Math.sqrt(point[0] * point[0] + point[1] * point[1]) <= fovR) return true;
        }

        int[][] edges = {
                {0, 1}, {2, 3}, {4, 5}, {6, 7},
                {0, 2}, {1, 3}, {4, 6}, {5, 7},
                {0, 4}, {1, 5}, {2, 6}, {3, 7}
        };
        for (int[] edge : edges) {
            double[] a = projectedCorners[edge[0]];
            double[] b = projectedCorners[edge[1]];
            if (a == null || b == null) continue;
            double dx = b[0] - a[0];
            double dy = b[1] - a[1];
            double lenSq = dx * dx + dy * dy;
            double t = lenSq > 0 ? Math.max(0, Math.min(1, (-a[0] * dx + -a[1] * dy) / lenSq)) : 0;
            double cx = a[0] + t * dx;
            double cy = a[1] + t * dy;
            if (Math.sqrt(cx * cx + cy * cy) <= fovR) return true;
        }

        double[] eye = project.apply(entity.getEyePosition(1.0f));
        return eye != null && Math.sqrt(eye[0] * eye[0] + eye[1] * eye[1]) <= fovR;
    }

    private static long nextTriangularDelay(int min, int mode, int max) {
        if (min >= max) return min;
        float fMin = min;
        float fMax = max;
        float fMode = Math.max(fMin, Math.min(fMax, mode));
        float fc = (fMode - fMin) / (fMax - fMin);
        float u = SWING_RANDOM.nextFloat();
        float value;
        if (u < fc) {
            value = fMin + (float) Math.sqrt(u * (fMax - fMin) * (fMode - fMin));
        } else {
            value = fMax - (float) Math.sqrt((1f - u) * (fMax - fMin) * (fMax - fMode));
        }
        return Math.max(min, (long) value);
    }

    private static long nextJitteredDelay(long baseDelayMs, double jitterFraction) {
        if (baseDelayMs <= 0) return 0;
        double spread = baseDelayMs * jitterFraction;
        double offset = (SWING_RANDOM.nextDouble() * 2.0 - 1.0) * spread;
        return Math.max(0L, Math.round(baseDelayMs + offset));
    }

    private static UUID getAutoSwingTarget() {
        // Fish combat always gets auto-swing regardless of the toggle setting.
        if (FishingCombat.isKilling() && EnemyBoxesState.lockedTarget != null) {
            return EnemyBoxesState.lockedTarget;
        }
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
}
