package com.teeko.enemyboxes.client.feature.fishing;

import com.teeko.enemyboxes.client.EnemyBoxesClient;
import com.teeko.enemyboxes.client.mixin.accessor.MinecraftAccessor;
import com.teeko.enemyboxes.client.state.EnemyBoxesState;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Random;

public final class AutoFisher {

    private enum State { IDLE, DELAY_BEFORE_CAST, FISHING, DELAY_BEFORE_REEL }

    // After casting, wait this long before checking for a bite (avoids splash false-positives).
    private static final long BITE_GRACE_MS = 2000L;
    // If the hook entity hasn't appeared within this window the cast probably failed; re-cast.
    private static final long HOOK_APPEAR_TIMEOUT_MS = 3000L;
    private static final Random RANDOM = new Random();

    private static State state = State.IDLE;
    private static long nextActionMs = 0L;
    private static long hookCastMs = 0L;

    private AutoFisher() {}

    public static void tick(Minecraft client) {
        if (!EnemyBoxesState.autoFisherEnabled) {
            reset();
            return;
        }

        if (client.level == null || client.player == null) return;
        if (!EnemyBoxesClient.isPlayerActive(client)) return;

        long now = System.currentTimeMillis();

        switch (state) {
            case IDLE -> {
                state = State.DELAY_BEFORE_CAST;
                nextActionMs = now + randomDelay();
            }
            case DELAY_BEFORE_CAST -> {
                if (now >= nextActionMs) {
                    ((MinecraftAccessor) client).enemyboxes$invokeStartUseItem();
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
                    ((MinecraftAccessor) client).enemyboxes$invokeStartUseItem();
                    state = State.DELAY_BEFORE_CAST;
                    nextActionMs = now + randomDelay();
                }
            }
        }
    }

    public static void reset() {
        state = State.IDLE;
        nextActionMs = 0L;
        hookCastMs = 0L;
    }

    public static String getStatus() {
        return switch (state) {
            case IDLE -> "IDLE";
            case DELAY_BEFORE_CAST -> "CASTING";
            case FISHING -> "FISHING";
            case DELAY_BEFORE_REEL -> "BITING";
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
