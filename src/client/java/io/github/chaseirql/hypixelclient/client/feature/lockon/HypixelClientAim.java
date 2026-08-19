package io.github.chaseirql.hypixelclient.client.feature.lockon;

import io.github.chaseirql.hypixelclient.client.state.HypixelClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/** Applies smoothed camera rotation toward an entity or world-space target. */
public final class HypixelClientAim {

    private static float currentYaw = 0f;
    private static float currentPitch = 0f;

    private static float driftX = 0f;
    private static float driftY = 0f;
    private static float driftVelX = 0f;
    private static float driftVelY = 0f;
    private static float driftTargetX = 0f;
    private static float driftTargetY = 0f;
    private static long nextDriftRetargetMs = 0;

    private static final Random random = new Random();

    public static void reset() {
        driftX = 0f;
        driftY = 0f;
        driftVelX = 0f;
        driftVelY = 0f;
    }

    public static void aimAtEntity(Minecraft client, Entity entity) {
        if (client.player == null) return;

        // Always enforce line-of-sight — aimbot never shoots through walls
        if (!hasLineOfSight(client, entity)) return;

        Vec3 camPos = client.player.getEyePosition(1.0f);

        // Primary: aim at head (eye position = center of head hitbox in vanilla)
        Vec3 headPos = entity.getEyePosition(1.0f);
        Vec3 toHead  = headPos.subtract(camPos);

        // Fallback: chest position (65% up the bounding box) if head vector is degenerate
        Vec3 aimPos;
        if (toHead.lengthSqr() > 0.0001) {
            aimPos = headPos;
        } else {
            double chestY = entity.getBoundingBox().minY +
                    (entity.getBoundingBox().maxY - entity.getBoundingBox().minY) * 0.65;
            aimPos = new Vec3(entity.getX(), chestY, entity.getZ());
        }

        applyAim(client, aimPos);
    }

    /**
     * Smoothly rotates the camera toward a fixed yaw/pitch target.
     * Used to return to the saved fishing look angles after combat.
     * No drift or jitter — pure interpolation using the same triangular smoothing.
     */
    public static void smoothAimToAngles(Minecraft client, float targetYaw, float targetPitch) {
        if (client.player == null) return;

        float curYaw   = client.player.getYRot();
        float curPitch = client.player.getXRot();

        float smoothing = nextTriangularSmoothing();
        float speed = 1.0f - smoothing;

        float yawDiff = targetYaw - curYaw;
        while (yawDiff >  180f) yawDiff -= 360f;
        while (yawDiff < -180f) yawDiff += 360f;

        float newYaw   = curYaw + yawDiff * speed;
        float newPitch = curPitch + (targetPitch - curPitch) * speed;
        newPitch = Math.max(-90f, Math.min(90f, newPitch));

        applyViewRotation(client, newYaw, newPitch);
    }

    /**
     * Aim at an arbitrary world position with no line-of-sight check.
     * Used for the hunt mode to track projectiles without LOS enforcement.
     */
    public static void aimAtPosition(Minecraft client, Vec3 aimPos) {
        if (client.player == null) return;
        applyAim(client, aimPos);
    }

    private static void applyAim(Minecraft client, Vec3 aimPos) {
        Vec3 camPos = client.player.getEyePosition(1.0f);

        updateDrift();

        Vec3 target = aimPos.add(
                driftX + (random.nextFloat() - 0.5f) * HypixelClientState.jitterStrength,
                driftY + (random.nextFloat() - 0.5f) * HypixelClientState.jitterStrength,
                0
        );

        Vec3 toTarget = target.subtract(camPos);
        double dx = toTarget.x;
        double dy = toTarget.y;
        double dz = toTarget.z;

        float targetYaw   = (float)(Math.toDegrees(Math.atan2(-dx, dz)));
        float targetPitch = (float)(Math.toDegrees(Math.atan2(-dy, Math.sqrt(dx * dx + dz * dz))));

        // Always seed from vanilla's current angles so mouse input blends naturally
        currentYaw   = client.player.getYRot();
        currentPitch = client.player.getXRot();

        // Sample smoothing from the triangular distribution each frame
        float smoothing = nextTriangularSmoothing();
        float speed = 1.0f - smoothing;

        float yawDiff = targetYaw - currentYaw;
        while (yawDiff >  180f) yawDiff -= 360f;
        while (yawDiff < -180f) yawDiff += 360f;

        currentYaw   = currentYaw + yawDiff * speed;
        currentPitch = currentPitch + (targetPitch - currentPitch) * speed;
        currentPitch = Math.max(-90f, Math.min(90f, currentPitch));

        applyViewRotation(client, currentYaw, currentPitch);
    }

    private static boolean hasLineOfSight(Minecraft client, Entity entity) {
        if (client.level == null || client.player == null) return false;

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

    /**
     * Returns a smoothing value sampled from the triangular distribution
     * defined by [aimSmoothingMin, aimSmoothingMode, aimSmoothingMax].
     * Mirrors the same logic used for swing/reaction delays.
     */
    private static float nextTriangularSmoothing() {
        float lo   = HypixelClientState.aimSmoothingMin;
        float hi   = HypixelClientState.aimSmoothingMax;
        float mode = HypixelClientState.aimSmoothingMode;

        // Clamp mode into [lo, hi] in case state gets out of order
        mode = Math.max(lo, Math.min(hi, mode));

        if (lo >= hi) return lo;

        float fc = (mode - lo) / (hi - lo);
        float u  = random.nextFloat();
        if (u < fc) {
            return lo + (float) Math.sqrt(u * (hi - lo) * (mode - lo));
        } else {
            return hi - (float) Math.sqrt((1f - u) * (hi - lo) * (hi - mode));
        }
    }

    private static void updateDrift() {
        long now = System.currentTimeMillis();

        if (now >= nextDriftRetargetMs) {
            driftTargetX = (random.nextFloat() - 0.5f) * 2f * HypixelClientState.driftStrength;
            driftTargetY = (random.nextFloat() - 0.5f) * 2f * HypixelClientState.driftStrength;
            nextDriftRetargetMs = now + 400 + random.nextInt(800);
        }

        float stiffness = 0.04f;
        float damping   = 0.75f;

        driftVelX += (driftTargetX - driftX) * stiffness;
        driftVelY += (driftTargetY - driftY) * stiffness;
        driftVelX *= damping;
        driftVelY *= damping;
        driftX += driftVelX;
        driftY += driftVelY;
    }

    private static void applyViewRotation(Minecraft client, float yaw, float pitch) {
        currentYaw = yaw;
        currentPitch = pitch;
        client.player.setYRot(yaw);
        client.player.setXRot(pitch);
        client.player.yRotO = yaw;
        client.player.xRotO = pitch;
    }
}
