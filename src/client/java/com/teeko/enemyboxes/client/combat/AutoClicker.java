package com.teeko.enemyboxes.client.combat;

import com.teeko.enemyboxes.client.EnemyBoxesClient;
import com.teeko.enemyboxes.client.mixin.accessor.MinecraftAccessor;
import com.teeko.enemyboxes.client.state.EnemyBoxesState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.HitResult;

import java.util.Random;

/**
 * Humanized attack timing used by both the held auto-clicker and lock-on auto
 * swing. The swing mixin records successful swings, so this class only decides
 * when to fire vanilla startAttack().
 */
public final class AutoClicker {

    private static final Random RANDOM = new Random();
    private static final ClickState AUTO_CLICK_STATE = new ClickState();
    private static final ClickState AUTO_SWING_STATE = new ClickState();

    public static final int HISTORY_SIZE = 128;
    public static final float[] clickHistory = new float[HISTORY_SIZE];
    public static int historyHead = 0;
    public static int historyCount = 0;

    private static long lastAttackMs = -1L;
    private static float smoothedCps = -1f;

    private AutoClicker() {}

    private static final class ClickState {
        private float rhythm = 0f;
        private float fatigue = 0f;
        private int clicksSinceStart = 0;
        private boolean active = false;
        private long nextClickMs = -1L;
    }

    /**
     * Runs the held auto-clicker while the physical attack key is down.
     * Called from GameRenderer.render() TAIL after hitResult is refreshed.
     */
    public static void tick(Minecraft client) {
        if (!EnemyBoxesState.autoClickerEnabled
                || !EnemyBoxesClient.isPlayerActive(client)
                || client.level == null
                || client.player == null
                || client.gameMode == null
                || !client.options.keyAttack.isDown()
                || (EnemyBoxesState.autoSwingEnabled && EnemyBoxesState.lockedTarget != null)
                || isLookingAtBlock(client)) {
            releaseIfActive(AUTO_CLICK_STATE);
            return;
        }

        long now = System.currentTimeMillis();
        if (!shouldFire(
                AUTO_CLICK_STATE,
                now,
                EnemyBoxesState.acCpsMin,
                EnemyBoxesState.acCpsMode,
                EnemyBoxesState.acCpsMax,
                false
        )) {
            return;
        }

        fireAttack(client);
    }

    /**
     * Advances the lock-on auto-swing timer using the same humanization logic
     * as the held auto-clicker. Swing delay config is converted from ms to CPS.
     */
    public static boolean shouldFireAutoSwing() {
        long now = System.currentTimeMillis();
        float minCps = delayMsToCps(EnemyBoxesState.swingDelayMax);
        float modeCps = delayMsToCps(EnemyBoxesState.swingDelayMode);
        float maxCps = delayMsToCps(EnemyBoxesState.swingDelayMin);
        return shouldFire(AUTO_SWING_STATE, now, minCps, modeCps, maxCps, true);
    }

    public static void releaseAutoSwing() {
        releaseIfActive(AUTO_SWING_STATE);
    }

    public static void resetAutoSwingState() {
        resetClickState(AUTO_SWING_STATE);
    }

    /**
     * Fires a vanilla attack pulse. Clearing missTime makes scheduled pulses
     * behave like fresh clicks instead of inheriting an old air-miss lockout.
     */
    public static void fireAttack(Minecraft client) {
        MinecraftAccessor minecraft = (MinecraftAccessor) client;
        if (minecraft.enemyboxes$getMissTime() > 0) {
            minecraft.enemyboxes$setMissTime(0);
        }
        minecraft.enemyboxes$invokeStartAttack();
    }

    /**
     * Called from the startAttack mixin for any real swing so the HUD can
     * render an instantaneous + smoothed CPS graph.
     */
    private static final float EMA_ALPHA = 0.25f;

    public static void pushClickCps() {
        long now = System.currentTimeMillis();
        float rawCps;
        if (lastAttackMs > 0) {
            long elapsed = now - lastAttackMs;
            rawCps = elapsed > 5 ? (float) (1000.0 / elapsed) : 25f;
            rawCps = Math.min(rawCps, 25f);
        } else {
            rawCps = EnemyBoxesState.acCpsMode;
        }
        lastAttackMs = now;

        smoothedCps = smoothedCps < 0
                ? rawCps
                : EMA_ALPHA * rawCps + (1f - EMA_ALPHA) * smoothedCps;

        clickHistory[historyHead] = smoothedCps;
        historyHead = (historyHead + 1) % HISTORY_SIZE;
        if (historyCount < HISTORY_SIZE) historyCount++;
    }

    public static void resetState() {
        resetClickState(AUTO_CLICK_STATE);
        historyHead = 0;
        historyCount = 0;
        lastAttackMs = -1L;
        smoothedCps = -1f;
    }

    private static boolean shouldFire(
            ClickState state,
            long now,
            float minCps,
            float modeCps,
            float maxCps,
            boolean fireImmediatelyOnStart
    ) {
        float range = Math.max(maxCps - minCps, 0.2f);

        if (!state.active) {
            state.rhythm = gaussian(modeCps * 1.05f, range * 0.08f);
            state.rhythm = clamp(state.rhythm, minCps, maxCps);
            state.fatigue = clamp(state.fatigue, 0f, 1f);
            state.active = true;
            state.nextClickMs = now + (long) (1000.0 / state.rhythm);
            if (!fireImmediatelyOnStart) {
                state.clicksSinceStart = 0;
                return false;
            }
            state.clicksSinceStart = 1;
            return true;
        }

        if (now < state.nextClickMs) return false;

        float drift = gaussian(0f, range * 0.02f);
        float pull = (modeCps - state.rhythm) * 0.03f;
        state.rhythm += drift + pull;

        state.fatigue += 0.0002f;
        if (state.fatigue > 1f) state.fatigue = 1f;

        if (RANDOM.nextFloat() < 0.02f) state.fatigue *= 0.5f;

        float fatigueEffect = state.fatigue * range * 0.15f;
        float effectiveRhythm = clamp(state.rhythm - fatigueEffect, minCps, maxCps);

        float clickCps = gaussian(effectiveRhythm, range * 0.10f);

        float roll = RANDOM.nextFloat();
        if (roll < 0.015f && state.clicksSinceStart > 3) {
            clickCps = effectiveRhythm * gaussian(0.45f, 0.08f);
        } else if (roll < 0.04f && state.clicksSinceStart > 2) {
            clickCps = effectiveRhythm * gaussian(1.2f, 0.05f);
        }

        clickCps = clamp(clickCps, minCps * 0.5f, maxCps * 1.3f);
        if (clickCps < 0.1f) clickCps = 0.1f;

        state.clicksSinceStart++;
        state.nextClickMs = now + (long) (1000.0 / clickCps);
        return true;
    }

    private static void releaseIfActive(ClickState state) {
        if (!state.active) return;
        state.fatigue *= 0.3f;
        state.clicksSinceStart = 0;
        state.active = false;
        state.nextClickMs = -1L;
    }

    private static void resetClickState(ClickState state) {
        state.rhythm = 0f;
        state.fatigue = 0f;
        state.clicksSinceStart = 0;
        state.active = false;
        state.nextClickMs = -1L;
    }

    private static boolean isLookingAtBlock(Minecraft client) {
        return client.hitResult != null
                && client.hitResult.getType() == HitResult.Type.BLOCK;
    }

    private static float delayMsToCps(int delayMs) {
        return 1000.0f / Math.max(delayMs, 1);
    }

    private static float gaussian(float mean, float stddev) {
        return mean + stddev * (float) RANDOM.nextGaussian();
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
