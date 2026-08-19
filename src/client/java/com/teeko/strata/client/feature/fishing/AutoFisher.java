package com.teeko.strata.client.feature.fishing;

import com.teeko.strata.client.StrataClient;
import com.teeko.strata.client.config.StrataConfig;
import com.teeko.strata.client.integration.BotEventClient;
import com.teeko.strata.client.mixin.accessor.MinecraftAccessor;
import com.teeko.strata.client.state.StrataState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Random;

/** Coordinates casting, bite detection, recovery, and transitions into fishing combat. */
public final class AutoFisher {

    private enum State { IDLE, DELAY_BEFORE_CAST, FISHING, DELAY_BEFORE_REEL, COMBAT }

    // After casting, wait this long before checking for a bite (avoids splash false-positives).
    private static final long BITE_GRACE_MS = 2000L;
    // If the hook entity hasn't appeared within this window the cast probably failed; re-cast.
    private static final long HOOK_APPEAR_TIMEOUT_MS = 3000L;
    private static final Random RANDOM = new Random();

    private static State state = State.IDLE;
    private static long nextActionMs = 0L;
    private static long hookCastMs = 0L;

    /** Block the player was standing on when autofish was first activated. Null when off. */
    public static BlockPos originBlock = null;

    /**
     * Look angles saved when autofish first activated.
     * Always used as the return target after combat — not the angles at reel-in time,
     * so the player consistently looks back at the same fishing position each cast.
     */
    public static float originYaw   = 0f;
    public static float originPitch = 0f;

    private AutoFisher() {}

    public static void tick(Minecraft client) {
        if (!StrataState.autoFisherEnabled) {
            reset();
            return;
        }

        if (client.level == null || client.player == null) return;

        // When paused, inventory is open, or chat is open — release movement so the
        // player has full manual control, but preserve state so combat can resume.
        if (!StrataClient.isPlayerActive(client)) {
            FishingCombat.releaseMovement();
            return;
        }

        long now = System.currentTimeMillis();

        switch (state) {
            case IDLE -> {
                // originBlock/Yaw/Pitch are set at the exact moment K is pressed
                // (see StrataClient.toggleAutoFisher). This fallback only fires
                // if the player somehow reaches IDLE without that capture running.
                if (originBlock == null) {
                    originBlock = client.player.getOnPos();
                    originYaw   = client.player.getYRot();
                    originPitch = client.player.getXRot();
                }
                // Drift correction: walk back to origin before casting if needed.
                Vec3 originCenter = Vec3.atBottomCenterOf(originBlock);
                Vec3 playerPos    = client.player.position();
                double drift = Math.sqrt(
                        Math.pow(playerPos.x - originCenter.x, 2) +
                        Math.pow(playerPos.z - originCenter.z, 2));
                if (drift > 1.2) {
                    FishingCombat.driveReposition(client, originCenter);
                    return; // stay in IDLE until back in position
                }
                FishingCombat.stopExternalReposition();
                state = State.DELAY_BEFORE_CAST;
                nextActionMs = now + randomDelay();
            }
            case DELAY_BEFORE_CAST -> {
                if (now >= nextActionMs) {
                    // Ensure the fishing rod is selected before sending the use input.
                    FishingCombat.swapToRod(client);
                    ((MinecraftAccessor) client).strata$invokeStartUseItem();
                    hookCastMs = now;
                    state = State.FISHING;
                }
            }
            case FISHING -> {
                FishingHook hook = client.player.fishing;
                if (hook == null) {
                    // Wait for the server to send the hook entity. Only re-cast if it
                    // never shows up (e.g. cast was blocked by the server).
                    if (now - hookCastMs > HOOK_APPEAR_TIMEOUT_MS) {
                        state = State.DELAY_BEFORE_CAST;
                        nextActionMs = now + randomDelay();
                    }
                } else if (now - hookCastMs > BITE_GRACE_MS && isBiting(client, hook)) {
                    state = State.DELAY_BEFORE_REEL;
                    nextActionMs = now + randomDelay();
                }
            }
            case DELAY_BEFORE_REEL -> {
                if (now >= nextActionMs) {
                    FishingCombat.stopExternalReposition();
                    ((MinecraftAccessor) client).strata$invokeStartUseItem();
                    // Always return to the exact K-press block, with the K-press angles.
                    FishingCombat.startScan(client,
                            net.minecraft.world.phys.Vec3.atBottomCenterOf(originBlock),
                            originYaw, originPitch);
                    state = State.COMBAT;
                }
            }
            case COMBAT -> {
                FishingCombat.tick(client);
                if (!FishingCombat.isCombatActive()) {
                    state = State.DELAY_BEFORE_CAST;
                    nextActionMs = now + randomDelay();
                }
            }
        }
    }

    /**
     * Full reset — clears all state, origin, and FishingCombat.
     * Called on manual disable and by forceStop().
     */
    public static void reset() {
        state        = State.IDLE;
        nextActionMs = 0L;
        hookCastMs   = 0L;
        originBlock  = null;
        originYaw    = 0f;
        originPitch  = 0f;
        FishingCombat.reset();
    }

    /**
     * Force-stops the auto-fisher due to an external event (server shutdown, world change, etc.)
     * and queues a Discord alert. Does nothing if the fisher is already off.
     * Manual disable (keybind) should call reset() directly instead of this.
     */
    public static void forceStop(Minecraft client, String reason) {
        if (!StrataState.autoFisherEnabled) return;

        String playerName  = (client != null && client.player != null)
                ? client.player.getName().getString() : "";
        String dimensionId = (client != null && client.level != null)
                ? client.level.dimension().toString() : "";
        String stateAtStop = getStatus();

        StrataState.autoFisherEnabled = false;
        reset();
        StrataConfig.save();

        if (client != null && client.player != null) {
            client.player.displayClientMessage(Component.literal(
                    "[Strata] Auto Fish stopped: " + reason
            ), false);
        }

        boolean alertQueued = BotEventClient.sendAutoFishForcedStopEvent(
                playerName, dimensionId, stateAtStop, reason);
        if (!alertQueued && client != null && client.player != null) {
            client.player.displayClientMessage(Component.literal(
                    "[Strata] Fishing forced-stop alert not queued. Check bot alert settings."
            ), false);
        }
    }

    public static String getStatus() {
        return switch (state) {
            case IDLE -> "IDLE";
            case DELAY_BEFORE_CAST -> "CASTING";
            case FISHING -> "FISHING";
            case DELAY_BEFORE_REEL -> "BITING";
            case COMBAT -> FishingCombat.getState().name();
        };
    }

    // The server places an invisible marker ArmorStand at the bobber whose custom name is
    // "?" while waiting and "!!!" the moment the fish is ready to reel. Scanning within
    // 2 blocks of the hook position is tight enough to ignore unrelated stands.
    private static boolean isBiting(Minecraft client, FishingHook hook) {
        AABB search = hook.getBoundingBox().inflate(2.0);
        List<ArmorStand> stands = client.level.getEntitiesOfClass(ArmorStand.class, search);
        for (ArmorStand stand : stands) {
            Component name = stand.getCustomName();
            if (name != null && name.getString().contains("!!!")) return true;
        }
        return false;
    }

    private static int randomDelay() {
        return 50 + RANDOM.nextInt(251); // [50, 300] ms
    }
}
