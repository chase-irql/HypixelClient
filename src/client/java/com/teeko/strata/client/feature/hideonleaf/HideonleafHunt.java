package com.teeko.strata.client.feature.hideonleaf;

import com.teeko.strata.client.state.StrataState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Tracks Hideonleaf targets and advances the automated hunt sequence each client tick. */
public final class HideonleafHunt {

    // Debug state exposed to HUD overlay
    public static String debugState       = "IDLE";
    public static double debugShulkerDist = -1;
    public static double debugBulletDist  = -1;
    public static String debugBulletState = "";
    private static final Set<UUID> handledBullets = new HashSet<>();

    private HideonleafHunt() {}

    public static void resetState() {
        StrataState.huntLockedShulker = null;
        StrataState.huntTrackedBullet = null;
        StrataState.huntLockedBullet  = null;
        handledBullets.clear();
        debugState       = "OFF";
        debugShulkerDist = -1;
        debugBulletDist  = -1;
        debugBulletState = "";
    }

    public static void markBulletUsed(UUID bulletId) {
        if (bulletId == null) return;
        handledBullets.add(bulletId);
        if (bulletId.equals(StrataState.huntTrackedBullet)) {
            StrataState.huntTrackedBullet = null;
        }
        if (bulletId.equals(StrataState.huntLockedBullet)) {
            StrataState.huntLockedBullet = null;
        }
        debugState = "USED";
        debugBulletState = "clicked";
    }

    public static void tick(Minecraft client) {
        if (!StrataState.autoHuntEnabled) {
            resetState();
            return;
        }

        if (client.level == null || client.player == null) return;

        // 1. Find nearest alive Shulker
        Shulker nearestShulker    = null;
        double nearestShulkerDist = Double.MAX_VALUE;

        for (Entity e : client.level.entitiesForRendering()) {
            if (!(e instanceof Shulker s) || !s.isAlive()) continue;
            double d = client.player.distanceTo(s);
            if (d < nearestShulkerDist) {
                nearestShulkerDist = d;
                nearestShulker = s;
            }
        }

        if (nearestShulker == null) {
            StrataState.huntLockedShulker = null;
            StrataState.huntTrackedBullet = null;
            StrataState.huntLockedBullet  = null;
            debugState       = "NO SHULKER";
            debugShulkerDist = -1;
            debugBulletDist  = -1;
            debugBulletState = "";
            return;
        }

        StrataState.huntLockedShulker = nearestShulker.getUUID();
        debugShulkerDist = nearestShulkerDist;
        Vec3 shulkerPos = nearestShulker.position();

        // 2. Find the nearest shulker bullet moving away from the shulker.
        ShulkerBullet targetBullet    = null;
        double nearestBulletDist      = Double.MAX_VALUE;
        Set<UUID> activeBullets       = new HashSet<>();

        for (Entity e : client.level.entitiesForRendering()) {
            if (!(e instanceof ShulkerBullet bullet)) continue;
            activeBullets.add(bullet.getUUID());

            Entity owner = bullet.getOwner();
            if (owner != null && !owner.getUUID().equals(nearestShulker.getUUID())) continue;

            Vec3 bulletPos = bullet.position();
            Vec3 vel       = bullet.getDeltaMovement();
            Vec3 toShulker = shulkerPos.subtract(bulletPos);
            double dot     = vel.dot(toShulker);

            if (dot >= 0) {
                // Once the same bullet is heading back toward the shulker again,
                // treat the next outward bounce as a fresh cycle we can click.
                handledBullets.remove(bullet.getUUID());
                continue;
            }

            if (handledBullets.contains(bullet.getUUID())) continue;

            double d = client.player.distanceTo(bullet);
            if (d < nearestBulletDist) {
                nearestBulletDist = d;
                targetBullet = bullet;
            }
        }

        handledBullets.retainAll(activeBullets);

        if (targetBullet == null) {
            StrataState.huntTrackedBullet = null;
            StrataState.huntLockedBullet  = null;
            debugState       = "WAITING";
            debugBulletDist  = -1;
            debugBulletState = "none";
            return;
        }

        StrataState.huntTrackedBullet = targetBullet.getUUID();

        double aimReach = client.player.entityInteractionRange() - 0.5d;
        boolean bulletInAimRange = nearestBulletDist <= aimReach;

        StrataState.huntLockedBullet = bulletInAimRange ? targetBullet.getUUID() : null;
        debugBulletDist  = nearestBulletDist;
        debugBulletState = bulletInAimRange ? "INCOMING [LOCK]" : "INCOMING [WAIT]";
        debugState       = bulletInAimRange ? "AIMING" : "TRACKING";
    }
}
