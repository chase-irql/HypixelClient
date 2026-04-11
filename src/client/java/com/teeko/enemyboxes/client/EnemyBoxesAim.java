package com.teeko.enemyboxes.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

public final class EnemyBoxesAim {

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

        Vec3 camPos = client.player.getEyePosition(1.0f);

        double chestY = entity.getBoundingBox().minY +
                (entity.getBoundingBox().maxY - entity.getBoundingBox().minY) * 0.65;
        Vec3 chestPos = new Vec3(entity.getX(), chestY, entity.getZ());

        updateDrift();

        Vec3 target = chestPos.add(
                driftX + (random.nextFloat() - 0.5f) * EnemyBoxesState.jitterStrength,
                driftY + (random.nextFloat() - 0.5f) * EnemyBoxesState.jitterStrength,
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

        float speed = 1.0f - EnemyBoxesState.aimSmoothing;

        float yawDiff = targetYaw - currentYaw;
        while (yawDiff >  180f) yawDiff -= 360f;
        while (yawDiff < -180f) yawDiff += 360f;

        currentYaw   = currentYaw + yawDiff * speed;
        currentPitch = currentPitch + (targetPitch - currentPitch) * speed;
        currentPitch = Math.max(-90f, Math.min(90f, currentPitch));

        client.player.setYRot(currentYaw);
        client.player.setXRot(currentPitch);
        client.player.yRotO = currentYaw;
        client.player.xRotO = currentPitch;
    }

    private static void updateDrift() {
        long now = System.currentTimeMillis();

        if (now >= nextDriftRetargetMs) {
            driftTargetX = (random.nextFloat() - 0.5f) * 2f * EnemyBoxesState.driftStrength;
            driftTargetY = (random.nextFloat() - 0.5f) * 2f * EnemyBoxesState.driftStrength;
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
}