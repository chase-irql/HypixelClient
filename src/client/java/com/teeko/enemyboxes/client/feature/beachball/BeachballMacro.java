package com.teeko.enemyboxes.client.feature.beachball;

import com.teeko.enemyboxes.client.EnemyBoxesClient;
import com.teeko.enemyboxes.client.mixin.accessor.MinecraftAccessor;
import com.teeko.enemyboxes.client.state.EnemyBoxesState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BeachballMacro {

    private static final String BEACHBALL_ITEM_ID = "BOUNCY_BEACH_BALL";
    private static final Pattern RESULT_CHAT_PATTERN =
            Pattern.compile("kept the bouncy beach ball in the air for\\s+(\\d+)\\s+bounces", Pattern.CASE_INSENSITIVE);
    private static final Pattern HUD_COUNTER_PATTERN =
            Pattern.compile("\\b(?:bounces?|count(?:er)?)\\s*:\\s*(\\d+)\\b", Pattern.CASE_INSENSITIVE);

    private static final double MAX_LOCK_DISTANCE = 20.0;
    private static final double TRACK_DEADZONE = 0.12;
    private static final double TRACK_RESUME_DISTANCE = 0.40;
    private static final double MOVE_AXIS_THRESHOLD = 0.05;
    private static final double START_REACHED_DISTANCE = 0.45;
    private static final long USE_RETRY_MS = 3500L;
    private static final long HUD_COUNTER_STALE_MS = 1500L;
    private static final int HUD_SCAN_TOP_PADDING = 160;
    private static final int HUD_SCAN_BOTTOM_PADDING = 8;
    private static final int MAX_RECENT_HUD_LINES = 8;

    private static final double BOUNCE_DESCEND_THRESHOLD = 0.035;
    private static final double BOUNCE_ASCEND_THRESHOLD = 0.035;
    private static final double BOUNCE_HORIZONTAL_MAX = 0.90;
    private static final double BOUNCE_HEAD_OFFSET_MIN = -0.30;
    private static final double BOUNCE_HEAD_OFFSET_MAX = 0.95;
    private static final long MIN_BOUNCE_INTERVAL_MS = 180L;

    private static BlockPos startBlock = null;
    private static UUID lockedBallId = null;
    private static Vec3 lastBallPos = null;
    private static boolean waitingForBallSpawn = false;
    private static boolean returningToStart = false;
    private static long nextUseAttemptMs = 0L;
    private static String status = "OFF";

    private static int bounceCount = 0;
    private static int lastChatResultCount = -1;
    private static long lastBounceMs = 0L;
    private static double previousBallY = Double.NaN;
    private static double previousBallDy = 0.0;
    private static double currentBallDy = 0.0;
    private static double lastHorizontalToPlayer = -1.0;
    private static double lastHorizontalToStart = -1.0;
    private static double lastHeadOffset = Double.NaN;
    private static double lastBallAgeMs = 0.0;
    private static long lockAcquiredMs = 0L;
    private static int hudCounterCount = -1;
    private static long hudCounterSeenMs = 0L;
    private static int hudCounterX = -1;
    private static int hudCounterY = -1;
    private static String hudCounterText = "";
    private static final Deque<String> recentHudTexts = new ArrayDeque<>();
    private static final Deque<String> recentPacketTexts = new ArrayDeque<>();
    private static boolean movementOwned = false;
    private static boolean desiredForward = false;
    private static boolean desiredBackward = false;
    private static boolean desiredLeft = false;
    private static boolean desiredRight = false;
    private static boolean desiredSprint = false;

    private BeachballMacro() {}

    public static void onRunningStateChanged(Minecraft client, boolean running) {
        releaseMovement(client);
        clearRuntimeState();
        status = running ? "STARTING" : "OFF";
    }

    public static void stopIfRunning(Minecraft client) {
        if (!EnemyBoxesState.beachballMacroRunning) return;
        EnemyBoxesState.beachballMacroRunning = false;
        onRunningStateChanged(client, false);
    }

    public static void onChatMessage(Component message) {
        if (message == null) return;

        String plain = normalize(message.getString());
        Matcher matcher = RESULT_CHAT_PATTERN.matcher(plain);
        if (!matcher.find()) return;

        try {
            lastChatResultCount = Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            lastChatResultCount = -1;
        }

        waitingForBallSpawn = false;
        returningToStart = true;
        clearLock();
        clearHudCounter();
        status = "RESULT";
    }

    public static void onGameMessage(Component message, boolean overlay) {
        if (message == null) return;

        if (overlay) {
            captureCounterText(message.getString(), -1, -1, "event/overlay");
            return;
        }

        if (EnemyBoxesState.beachballMacroRunning) {
            onChatMessage(message);
        }
    }

    public static void tick(Minecraft client) {
        if (!EnemyBoxesState.beachballMacroRunning) {
            status = "OFF";
            return;
        }

        if (client == null || client.level == null || client.player == null || client.gameMode == null) {
            status = "NO_LEVEL";
            return;
        }

        if (!EnemyBoxesClient.isPlayerActive(client)) {
            releaseMovement(client);
            status = client.isPaused() ? "PAUSED" : "SCREEN_OPEN";
            return;
        }

        if (startBlock == null) {
            startBlock = BlockPos.containing(client.player.position());
        }

        Vec3 startCenter = Vec3.atBottomCenterOf(startBlock);
        long now = System.currentTimeMillis();

        if (returningToStart) {
            waitingForBallSpawn = false;
            clearLock();

            if (moveTowardHorizontal(client, startCenter, true, START_REACHED_DISTANCE, START_REACHED_DISTANCE)) {
                status = "RETURNING";
                return;
            }

            returningToStart = false;
            status = "READY";
        }

        Candidate lockedTarget = resolveLockedTarget(client, startCenter, now);
        if (lockedTarget != null) {
            if (getActiveBounceCount(now) >= 40) {
                returningToStart = true;
                clearLock();
                clearHudCounter();
                releaseMovement(client);
                status = "CAP_REACHED";
                return;
            }

            moveTowardHorizontal(client, lockedTarget.ballPos(), false, TRACK_DEADZONE, TRACK_RESUME_DISTANCE);
            status = "TRACKING";
            return;
        }

        clearLock();

        if (waitingForBallSpawn) {
            Candidate acquired = findBestTarget(client, startCenter);
            if (acquired != null) {
                acquireLock(acquired, now);
                moveTowardHorizontal(client, acquired.ballPos(), false, TRACK_DEADZONE, TRACK_RESUME_DISTANCE);
                status = "TRACKING";
                return;
            }

            if (now < nextUseAttemptMs) {
                releaseMovement(client);
                status = "WAITING_FOR_BALL";
                return;
            }

            waitingForBallSpawn = false;
        }

        Candidate existing = findBestTarget(client, startCenter);
        if (existing != null) {
            acquireLock(existing, now);
            moveTowardHorizontal(client, existing.ballPos(), false, TRACK_DEADZONE, TRACK_RESUME_DISTANCE);
            status = "TRACKING";
            return;
        }

        if (moveTowardHorizontal(client, startCenter, true, START_REACHED_DISTANCE, START_REACHED_DISTANCE)) {
            status = "MOVING_TO_START";
            return;
        }

        if (now < nextUseAttemptMs) {
            releaseMovement(client);
            status = "USE_COOLDOWN";
            return;
        }

        if (!ensureBeachballSelected(client)) {
            status = "OUT_OF_BALLS";
            return;
        }

        ((MinecraftAccessor) client).enemyboxes$invokeStartUseItem();
        waitingForBallSpawn = true;
        nextUseAttemptMs = now + USE_RETRY_MS;
        releaseMovement(client);
        status = "USING";
    }

    public static List<Integer> findBeachballHotbarSlots(Minecraft client) {
        List<Integer> slots = new ArrayList<>();
        if (client == null || client.player == null) return slots;

        List<ItemStack> items = client.player.getInventory().getNonEquipmentItems();
        for (int slot = 0; slot < 9 && slot < items.size(); slot++) {
            if (isBeachballItem(items.get(slot))) {
                slots.add(slot);
            }
        }

        return slots;
    }

    public static boolean isBeachballItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;

        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (!customData.isEmpty()) {
            String id = customData.copyTag().getString("id").orElse("");
            if (BEACHBALL_ITEM_ID.equalsIgnoreCase(id)) {
                return true;
            }
        }

        return normalize(stack.getHoverName().getString()).contains("bouncy beach ball");
    }

    public static List<String> getDebugLines(Minecraft client) {
        List<String> lines = new ArrayList<>();
        lines.add("Beachball running=" + (EnemyBoxesState.beachballMacroRunning ? "ON" : "OFF")
                + " state=" + status);

        if (client == null || client.player == null) return lines;

        List<Integer> slots = findBeachballHotbarSlots(client);
        lines.add("Beachball hotbar slots: " + slots + " selected=" + client.player.getInventory().getSelectedSlot());

        if (startBlock != null) {
            lines.add("Beachball start block: " + formatBlockPos(startBlock)
                    + " startDist=" + fmt(horizontalDistance(client.player.position(), Vec3.atBottomCenterOf(startBlock))));
        }

        lines.add("Beachball count: live=" + bounceCount + " lastChat=" + lastChatResultCount
                + " waiting=" + waitingForBallSpawn + " returning=" + returningToStart);
        lines.add("Beachball counter HUD: active=" + getActiveBounceCount(System.currentTimeMillis())
                + " raw=" + hudCounterCount
                + " ageMs=" + hudCounterAgeMs(System.currentTimeMillis())
                + " pos=" + hudCounterX + ", " + hudCounterY
                + " text=[" + hudCounterText + "]");
        lines.add("Beachball HUD scan: " + (recentHudTexts.isEmpty() ? "[]" : recentHudTexts));
        lines.add("Beachball packet scan: " + (recentPacketTexts.isEmpty() ? "[]" : recentPacketTexts));

        if (lockedBallId != null) {
            lines.add("Beachball lock: ball=" + shortUuid(lockedBallId)
                    + " ageMs=" + (long) lastBallAgeMs
                    + " ballPos=" + (lastBallPos != null ? formatVec(lastBallPos) : "n/a"));
            lines.add("Beachball motion: dy=" + fmt(currentBallDy)
                    + " prevDy=" + fmt(previousBallDy)
                    + " horizToPlayer=" + fmt(lastHorizontalToPlayer)
                    + " horizToStart=" + fmt(lastHorizontalToStart)
                    + " headOffset=" + fmt(lastHeadOffset));
        }

        Vec3 origin = startBlock != null ? Vec3.atBottomCenterOf(startBlock) : client.player.position();
        Candidate best = findBestTarget(client, origin);
        if (best != null) {
            lines.add("Best Beachball candidate: pos=" + formatVec(best.ballPos())
                    + " playerDist=" + fmt(best.playerDistance())
                    + " startDist=" + fmt(best.startDistance())
                    + " dy=" + fmt(best.sampleDy()));
        } else {
            lines.add("Best Beachball candidate: none within 20 blocks");
        }

        return lines;
    }

    public static String getStatus() {
        return status;
    }

    public static int getBounceCount() {
        return getActiveBounceCount(System.currentTimeMillis());
    }

    public static int getLastChatResultCount() {
        return lastChatResultCount;
    }

    public static boolean hasActiveBall() {
        return lockedBallId != null;
    }

    public static double getLastHorizontalToPlayer() {
        return lastHorizontalToPlayer;
    }

    public static double getLastHeadOffset() {
        return lastHeadOffset;
    }

    public static UUID getLockedBallId() {
        return lockedBallId;
    }

    public static UUID getRenderBallId(Minecraft client) {
        if (!EnemyBoxesState.beachballMacroRunning) return null;
        if (lockedBallId != null) return lockedBallId;
        if (client == null || client.level == null || client.player == null) return null;

        Vec3 origin = startBlock != null ? Vec3.atBottomCenterOf(startBlock) : client.player.position();
        Candidate best = findBestTarget(client, origin);
        return best != null ? best.ballId() : null;
    }

    public static UUID getLockedCounterId() {
        return null;
    }

    public static UUID getLockedBoxId() {
        return null;
    }

    public static void onHudTextRendered(String text, int x, int y, int guiWidth, int guiHeight) {
        if (!EnemyBoxesState.beachballMacroRunning) return;

        String raw = text == null ? "" : text.trim();
        if (raw.isEmpty()) return;
        if (y < guiHeight - HUD_SCAN_TOP_PADDING || y > guiHeight - HUD_SCAN_BOTTOM_PADDING) return;

        rememberHudText(raw, x, y);
        captureCounterText(raw, x, y, "gui");
    }

    public static void onPacketTextReceived(String source, Component message) {
        if (!EnemyBoxesState.beachballMacroRunning) return;
        if (message == null) return;

        String raw = message.getString();
        rememberPacketText(source, raw);
        captureCounterText(raw, -1, -1, "packet/" + source);
    }

    private static void captureCounterText(String rawText, int x, int y, String sourceLabel) {
        if (!EnemyBoxesState.beachballMacroRunning) return;

        String raw = rawText == null ? "" : rawText.trim();
        if (raw.isEmpty()) return;

        String plain = normalize(stripFormatting(raw));
        Matcher matcher = HUD_COUNTER_PATTERN.matcher(plain);
        if (!matcher.find()) return;

        try {
            hudCounterCount = Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return;
        }

        hudCounterSeenMs = System.currentTimeMillis();
        hudCounterX = x;
        hudCounterY = y;
        hudCounterText = sourceLabel == null || sourceLabel.isBlank() ? raw : "[" + sourceLabel + "] " + raw;
    }

    private static boolean ensureBeachballSelected(Minecraft client) {
        List<Integer> slots = findBeachballHotbarSlots(client);
        if (slots.isEmpty()) {
            stopMacro(client, "No Bouncy Beach Balls found in the hotbar.");
            return false;
        }

        int selected = client.player.getInventory().getSelectedSlot();
        int desired = slots.contains(selected) ? selected : slots.getFirst();
        if (desired != selected) {
            client.player.getInventory().setSelectedSlot(desired);
        }

        return true;
    }

    private static Candidate resolveLockedTarget(Minecraft client, Vec3 startCenter, long now) {
        if (lockedBallId == null) return null;

        for (Entity entity : client.level.entitiesForRendering()) {
            if (!entity.getUUID().equals(lockedBallId)) continue;
            if (!(entity instanceof Slime slime) || !slime.isAlive()) break;

            Candidate candidate = buildCandidate(client, slime, startCenter);
            if (candidate == null) break;

            updateLiveBallMetrics(client, slime, startCenter, now);
            return candidate;
        }

        returningToStart = true;
        status = "BALL_LOST";
        return null;
    }

    private static Candidate findBestTarget(Minecraft client, Vec3 startCenter) {
        Candidate best = null;
        double bestDistance = Double.MAX_VALUE;

        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof Slime slime) || !slime.isAlive()) continue;

            Candidate candidate = buildCandidate(client, slime, startCenter);
            if (candidate == null) continue;
            if (candidate.playerDistance() > MAX_LOCK_DISTANCE) continue;

            if (candidate.playerDistance() < bestDistance) {
                best = candidate;
                bestDistance = candidate.playerDistance();
            }
        }

        return best;
    }

    private static Candidate buildCandidate(Minecraft client, Slime slime, Vec3 startCenter) {
        AABB box = slime.getBoundingBox();
        Vec3 boxCenter = box.getCenter();
        double playerDistance = horizontalDistance(boxCenter, client.player.position());
        double startDistance = horizontalDistance(boxCenter, startCenter);
        double sampleDy = boxCenter.y - lastBallSampleYFor(slime.getUUID(), boxCenter.y);
        return new Candidate(slime.getUUID(), boxCenter, playerDistance, startDistance, sampleDy);
    }

    private static void acquireLock(Candidate candidate, long now) {
        if (!candidate.ballId().equals(lockedBallId)) {
            resetBounceTracking(candidate.ballPos().y, now);
        }

        lockedBallId = candidate.ballId();
        lastBallPos = candidate.ballPos();
        waitingForBallSpawn = false;
    }

    private static void updateLiveBallMetrics(Minecraft client, Slime slime, Vec3 startCenter, long now) {
        AABB box = slime.getBoundingBox();
        Vec3 boxCenter = box.getCenter();
        double currentY = boxCenter.y;
        currentBallDy = Double.isNaN(previousBallY) ? 0.0 : currentY - previousBallY;
        lastBallPos = boxCenter;
        lastHorizontalToPlayer = horizontalDistance(boxCenter, client.player.position());
        lastHorizontalToStart = horizontalDistance(boxCenter, startCenter);
        lastHeadOffset = box.minY - client.player.getBoundingBox().maxY;
        lastBallAgeMs = Math.max(0L, now - lockAcquiredMs);

        // Count a bounce whenever the locked ball changes from clearly descending
        // to clearly ascending. This avoids depending on HUD text or head alignment.
        boolean signFlipBounce = previousBallDy < -BOUNCE_DESCEND_THRESHOLD && currentBallDy > BOUNCE_ASCEND_THRESHOLD;

        if (signFlipBounce && now - lastBounceMs >= MIN_BOUNCE_INTERVAL_MS) {
            bounceCount++;
            lastBounceMs = now;
        }

        previousBallY = currentY;
        previousBallDy = currentBallDy;
    }

    private static void resetBounceTracking(double currentBallY, long now) {
        bounceCount = 0;
        lockAcquiredMs = now;
        lastBounceMs = 0L;
        previousBallY = currentBallY;
        previousBallDy = 0.0;
        currentBallDy = 0.0;
        lastHorizontalToPlayer = -1.0;
        lastHorizontalToStart = -1.0;
        lastHeadOffset = Double.NaN;
        lastBallAgeMs = 0.0;
        clearHudCounter();
        desiredForward = false;
        desiredBackward = false;
        desiredLeft = false;
        desiredRight = false;
        desiredSprint = false;
        movementOwned = false;
    }

    private static void clearLock() {
        lockedBallId = null;
        lastBallPos = null;
        previousBallY = Double.NaN;
        previousBallDy = 0.0;
        currentBallDy = 0.0;
        lastHorizontalToPlayer = -1.0;
        lastHorizontalToStart = -1.0;
        lastHeadOffset = Double.NaN;
        lastBallAgeMs = 0.0;
    }

    private static void clearRuntimeState() {
        startBlock = null;
        clearLock();
        bounceCount = 0;
        lastChatResultCount = -1;
        waitingForBallSpawn = false;
        returningToStart = false;
        nextUseAttemptMs = 0L;
        lastBounceMs = 0L;
        lockAcquiredMs = 0L;
        clearHudCounter();
    }

    private static boolean moveTowardHorizontal(Minecraft client, Vec3 target, boolean allowSprint, double stopDistance, double resumeDistance) {
        Vec3 playerPos = client.player.position();
        double dx = target.x - playerPos.x;
        double dz = target.z - playerPos.z;
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);

        if (movementOwned) {
            if (horizontalDistance <= stopDistance) {
                releaseMovement(client);
                return false;
            }
        } else if (horizontalDistance <= resumeDistance) {
            return false;
        }

        double yaw = Math.toRadians(client.player.getYRot());
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);
        double rightX = -Math.cos(yaw);
        double rightZ = -Math.sin(yaw);

        double localForward = dx * forwardX + dz * forwardZ;
        double localRight = dx * rightX + dz * rightZ;

        boolean forward = localForward > MOVE_AXIS_THRESHOLD;
        boolean back = localForward < -MOVE_AXIS_THRESHOLD;
        boolean left = localRight < -MOVE_AXIS_THRESHOLD;
        boolean right = localRight > MOVE_AXIS_THRESHOLD;
        boolean sprint = allowSprint && horizontalDistance > 1.35 && forward && !back;

        applyMovement(forward, back, left, right, sprint);
        return true;
    }

    private static void applyMovement(boolean forward, boolean back, boolean left, boolean right, boolean sprint) {
        desiredForward = forward;
        desiredBackward = back;
        desiredLeft = left;
        desiredRight = right;
        desiredSprint = sprint;
        movementOwned = true;
    }

    private static void releaseMovement(Minecraft client) {
        if (!movementOwned) return;
        desiredForward = false;
        desiredBackward = false;
        desiredLeft = false;
        desiredRight = false;
        desiredSprint = false;
        movementOwned = false;
    }

    public static boolean shouldOverrideMovement() {
        return EnemyBoxesState.beachballMacroRunning;
    }

    public static Vec2 getDesiredMoveVector() {
        float forward = calculateImpulse(desiredForward, desiredBackward);
        float sideways = calculateImpulse(desiredLeft, desiredRight);
        return new Vec2(sideways, forward).normalized();
    }

    public static Input getDesiredInput() {
        boolean crouch = EnemyBoxesState.beachballMacroRunning && !returningToStart;
        return new Input(
                desiredForward,
                desiredBackward,
                desiredLeft,
                desiredRight,
                false,
                crouch,
                desiredSprint && !crouch
        );
    }

    private static float calculateImpulse(boolean positive, boolean negative) {
        if (positive == negative) return 0.0F;
        return positive ? 1.0F : -1.0F;
    }

    private static void stopMacro(Minecraft client, String reason) {
        EnemyBoxesState.beachballMacroRunning = false;
        onRunningStateChanged(client, false);
        if (client.player != null) {
            client.player.displayClientMessage(Component.literal("[EnemyBoxes] Beachball stopped (" + reason + ")"), false);
        }
    }

    private static double horizontalDistance(Vec3 a, Vec3 b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static double lastBallSampleYFor(UUID uuid, double fallbackY) {
        return uuid.equals(lockedBallId) && !Double.isNaN(previousBallY) ? previousBallY : fallbackY;
    }

    private static int getActiveBounceCount(long now) {
        return hasFreshHudCounter(now) ? hudCounterCount : bounceCount;
    }

    private static boolean hasFreshHudCounter(long now) {
        return hudCounterCount >= 0 && now - hudCounterSeenMs <= HUD_COUNTER_STALE_MS;
    }

    private static long hudCounterAgeMs(long now) {
        return hudCounterSeenMs == 0L ? -1L : Math.max(0L, now - hudCounterSeenMs);
    }

    private static void clearHudCounter() {
        hudCounterCount = -1;
        hudCounterSeenMs = 0L;
        hudCounterX = -1;
        hudCounterY = -1;
        hudCounterText = "";
        recentHudTexts.clear();
        recentPacketTexts.clear();
    }

    private static void rememberHudText(String raw, int x, int y) {
        String entry = x + ", " + y + " [" + raw + "]";
        if (!recentHudTexts.isEmpty() && entry.equals(recentHudTexts.peekLast())) {
            return;
        }

        recentHudTexts.addLast(entry);
        while (recentHudTexts.size() > MAX_RECENT_HUD_LINES) {
            recentHudTexts.removeFirst();
        }
    }

    private static void rememberPacketText(String source, String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) return;

        String entry = source + " [" + text + "]";
        if (!recentPacketTexts.isEmpty() && entry.equals(recentPacketTexts.peekLast())) {
            return;
        }

        recentPacketTexts.addLast(entry);
        while (recentPacketTexts.size() > MAX_RECENT_HUD_LINES) {
            recentPacketTexts.removeFirst();
        }
    }

    private static String stripFormatting(String text) {
        if (text == null || text.isEmpty()) return "";
        return text.replaceAll("\u00A7.", "");
    }

    private static String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).trim();
    }

    private static String formatBlockPos(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    private static String formatVec(Vec3 vec) {
        return String.format(Locale.ROOT, "%.2f, %.2f, %.2f", vec.x, vec.y, vec.z);
    }

    private static String fmt(double value) {
        return Double.isNaN(value) ? "n/a" : String.format(Locale.ROOT, "%.3f", value);
    }

    private static String shortUuid(UUID uuid) {
        String raw = uuid.toString();
        return raw.substring(0, Math.min(8, raw.length()));
    }

    private record Candidate(
            UUID ballId,
            Vec3 ballPos,
            double playerDistance,
            double startDistance,
            double sampleDy
    ) {}
}
