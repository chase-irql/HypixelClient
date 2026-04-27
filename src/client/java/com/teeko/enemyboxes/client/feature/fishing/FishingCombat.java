package com.teeko.enemyboxes.client.feature.fishing;

import com.teeko.enemyboxes.client.state.EnemyBoxesState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Detects sea creatures spawned by a fishing reel-in, kills them, and walks
 * the player back to their original fishing spot.
 *
 * State machine:
 *   IDLE → SCANNING → REACTING → KILLING → RETURNING → IDLE
 *
 * REACTING: creature spotted, aimbot tracks it, player hasn't moved yet.
 *           Simulates the human delay between noticing a mob and acting.
 * KILLING:  weapon equipped, walking toward mob, aimbot + autoswing active.
 *           If the creature is behind a block, we close in until LOS is clear.
 * RETURNING: rod re-equipped, walking back to fishing spot, camera smoothly
 *            returns to saved fishing angles (driven by GameRendererMixin).
 */
public final class FishingCombat {

    public enum State { IDLE, SCANNING, REACTING, KILLING, RETURNING }

    private static State state      = State.IDLE;
    private static UUID  creatureId = null;

    // Saved fishing position / look angles (set from AutoFisher at each cast)
    private static Vec3  fishingSpot  = null;
    private static float fishingYaw   = 0f;
    private static float fishingPitch = 0f;

    // Scan window
    private static long         scanStartMs     = 0L;
    private static final long   SCAN_TIMEOUT_MS = 2500L;
    private static final double SCAN_RADIUS     = 20.0;

    // Entities loaded before the reel-in — excluded from creature detection.
    private static final Set<UUID> preReelEntities = new HashSet<>();

    // Pre-react delay — time between first spotting a creature and the aimbot
    // beginning to track it. Gives a brief "what was that?" pause before locking on.
    private static long         preReactDelayUntilMs = 0L;
    private static final int    PRE_REACT_DELAY_MIN  = 150;
    private static final int    PRE_REACT_DELAY_MODE = 270;
    private static final int    PRE_REACT_DELAY_MAX  = 500;

    // Reaction delay — triangular distribution, simulates human "noticing" lag.
    // Range chosen to look natural: half a second to just under 1.5 seconds.
    private static long         reactUntilMs       = 0L;
    private static final int    REACT_DELAY_MIN    = 500;
    private static final int    REACT_DELAY_MODE   = 850;
    private static final int    REACT_DELAY_MAX    = 1400;
    private static final Random RANDOM             = new Random();

    // Movement override state — movementOwned is the single source of truth for
    // whether we should suppress player input. shouldOverrideMovement() returns
    // movementOwned directly, so releasing movement (e.g. on pause/inventory)
    // immediately restores full player control.
    private static boolean movementOwned   = false;
    private static boolean desiredForward  = false;
    private static boolean desiredBackward = false;
    private static boolean desiredLeft     = false;
    private static boolean desiredRight    = false;
    private static boolean desiredSprint   = false;
    private static boolean desiredJump     = false;

    // Navigation thresholds
    private static final double STOP_AT_CREATURE_DIST     = 2.5;
    private static final double STOP_AT_CREATURE_DIST_LOS = 0.5;  // keep closing when no LOS
    private static final double STOP_AT_SPOT_DIST         = 0.35; // tight return accuracy
    private static final double MOVE_AXIS_THRESHOLD       = 0.05;

    // Stuck detection (land only — water handled separately)
    private static long stuckCheckMs  = 0L;
    private static Vec3 lastPlayerPos = null;
    private static final long STUCK_CHECK_INTERVAL_MS = 250L;

    // External reposition — driven by AutoFisher between casts when the player
    // has drifted away from the origin block.
    private static boolean externalRepoActive = false;

    private FishingCombat() {}

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Called by AutoFisher immediately after the reel-in right-click.
     *
     * @param returnSpot  World position to walk back to after combat — always the
     *                    centre of AutoFisher.originBlock (the K-press position).
     * @param forcedYaw   Yaw angle to return to (saved at K-press).
     * @param forcedPitch Pitch angle to return to (saved at K-press).
     */
    public static void startScan(Minecraft client, Vec3 returnSpot, float forcedYaw, float forcedPitch) {
        if (client.player == null) return;
        fishingSpot  = returnSpot;
        fishingYaw           = forcedYaw;
        fishingPitch         = forcedPitch;
        creatureId           = null;
        preReactDelayUntilMs = 0L;
        state                = State.SCANNING;
        scanStartMs          = System.currentTimeMillis();

        preReelEntities.clear();
        if (client.level != null) {
            for (Entity e : client.level.entitiesForRendering()) {
                preReelEntities.add(e.getUUID());
            }
        }
    }

    /** True while any non-IDLE phase is active. AutoFisher pauses while this holds. */
    public static boolean isCombatActive() {
        return state != State.IDLE;
    }

    /**
     * True during REACTING or KILLING — aimbot should track the creature.
     * Distinct from isKilling() so the view smoothly turns toward the mob
     * during the reaction pause before the player starts moving.
     */
    public static boolean isAimActive() {
        return state == State.REACTING || state == State.KILLING;
    }

    /** True only during KILLING — gates autoswing. No swings during reaction. */
    public static boolean isKilling() {
        return state == State.KILLING;
    }

    public static State getState() {
        return state;
    }

    /** The UUID of the creature being targeted, or null if none. */
    public static UUID getCreatureId() {
        return creatureId;
    }

    /** The saved yaw angle to return to after combat. */
    public static float getFishingYaw() {
        return fishingYaw;
    }

    /** The saved pitch angle to return to after combat. */
    public static float getFishingPitch() {
        return fishingPitch;
    }

    /**
     * True once a fishing spot has been recorded (i.e. after the first reel-in).
     * Used by GameRendererMixin to decide whether to run smooth aim back to the
     * fishing angles when the aimbot is not actively tracking a creature.
     */
    public static boolean hasFishingTarget() {
        return fishingSpot != null;
    }

    /** Main tick — AutoFisher delegates here while isCombatActive(). */
    public static void tick(Minecraft client) {
        if (client.level == null || client.player == null) {
            reset();
            return;
        }
        switch (state) {
            case SCANNING  -> tickScanning(client);
            case REACTING  -> tickReacting(client);
            case KILLING   -> tickKilling(client);
            case RETURNING -> tickReturning(client);
            case IDLE      -> {}
        }
    }

    /**
     * Called by AutoFisher each tick when the player has drifted from the
     * origin block and is not currently in combat. Drives movement back.
     * AutoFisher should skip casting while this returns true.
     */
    public static boolean driveReposition(Minecraft client, Vec3 target) {
        externalRepoActive = true;
        Vec3   pos  = client.player.position();
        double dist = Math.sqrt(Math.pow(pos.x - target.x, 2) + Math.pow(pos.z - target.z, 2));
        if (dist <= 0.3) {
            stopExternalReposition();
            return false; // reached — no longer repositioning
        }
        moveToward(client, target, true, 0.3);
        handleVerticalMovement(client);
        return true; // still moving
    }

    /** Stop any active external reposition and release movement. */
    public static void stopExternalReposition() {
        if (!externalRepoActive) return;
        externalRepoActive = false;
        releaseMovement();
    }

    /**
     * Releases all movement override without touching combat state.
     * Called by AutoFisher when the player is inactive (paused/inventory/chat)
     * so vanilla input resumes immediately without losing KILLING/RETURNING state.
     */
    public static void releaseMovement() {
        desiredForward  = false;
        desiredBackward = false;
        desiredLeft     = false;
        desiredRight    = false;
        desiredSprint   = false;
        desiredJump     = false;
        movementOwned   = false;
    }

    /** Hard reset — releases movement and clears any locked target we own. */
    public static void reset() {
        if (state == State.REACTING || state == State.KILLING) {
            EnemyBoxesState.lockedTarget = null;
        }
        state                = State.IDLE;
        creatureId           = null;
        fishingSpot          = null;
        fishingYaw           = 0f;
        fishingPitch         = 0f;
        preReactDelayUntilMs = 0L;
        externalRepoActive   = false;
        preReelEntities.clear();
        releaseMovement();
    }

    // -------------------------------------------------------------------------
    // State ticks
    // -------------------------------------------------------------------------

    private static void tickScanning(Minecraft client) {
        long now = System.currentTimeMillis();

        // Phase 2: creature already spotted — waiting for the brief pre-react pause
        // before the aimbot starts tracking and the weapon is swapped.
        if (creatureId != null) {
            LivingEntity known = findCreature(client);
            if (known == null || !known.isAlive()) {
                // Vanished during the delay — drop the ID and keep scanning
                creatureId           = null;
                preReactDelayUntilMs = 0L;
            } else if (now >= preReactDelayUntilMs) {
                // Delay expired — lock aimbot, equip weapon, enter REACTING
                EnemyBoxesState.lockedTarget = creatureId;
                swapToSlot(client, EnemyBoxesState.fishingWeaponSlot);
                reactUntilMs = now + triangularDelay(REACT_DELAY_MIN, REACT_DELAY_MODE, REACT_DELAY_MAX);
                state = State.REACTING;
            }
            // else: still in the pre-react window — do nothing this tick
            return;
        }

        // Phase 1: scan for a newly spawned creature
        LivingEntity closest     = null;
        double       closestDist = Double.MAX_VALUE;

        for (Entity e : client.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity living)) continue;
            if (!living.isAlive()) continue;
            if (living == client.player) continue;
            if (living instanceof ArmorStand) continue;
            // Don't filter by the Enemy interface — custom server mobs like
            // Squid variants are passive in vanilla but hostile here.
            // The pre-reel snapshot is what prevents false positives.
            if (preReelEntities.contains(living.getUUID())) continue;

            double dist = client.player.distanceTo(living);
            if (dist < SCAN_RADIUS && dist < closestDist) {
                closestDist = dist;
                closest     = living;
            }
        }

        if (closest != null) {
            // Record the creature but don't lock on yet — start the pre-react timer.
            creatureId           = closest.getUUID();
            preReactDelayUntilMs = now + triangularDelay(
                    PRE_REACT_DELAY_MIN, PRE_REACT_DELAY_MODE, PRE_REACT_DELAY_MAX);
            return;
        }

        if (now - scanStartMs > SCAN_TIMEOUT_MS) {
            state = State.IDLE;
        }
    }

    private static void tickReacting(Minecraft client) {
        // Make sure the creature is still alive during the reaction window.
        LivingEntity creature = findCreature(client);
        if (creature == null || !creature.isAlive()) {
            EnemyBoxesState.lockedTarget = null;
            creatureId = null;
            state      = State.IDLE;
            return;
        }

        // Re-assert lock so it survives clearLock() each tick.
        EnemyBoxesState.lockedTarget = creatureId;

        // Aimbot tracks the creature (via isAimActive()) but no movement yet.
        if (System.currentTimeMillis() >= reactUntilMs) {
            lastPlayerPos = client.player.position();
            stuckCheckMs  = System.currentTimeMillis() + 500L;
            state         = State.KILLING;
        }
    }

    private static void tickKilling(Minecraft client) {
        LivingEntity creature = findCreature(client);
        if (creature == null || !creature.isAlive()) {
            EnemyBoxesState.lockedTarget = null;
            creatureId    = null;
            lastPlayerPos = null;
            stuckCheckMs  = 0L;
            releaseMovement();
            swapToRod(client);
            state = State.RETURNING;
            return;
        }

        EnemyBoxesState.lockedTarget = creatureId;

        // If we have line of sight, stop at comfortable melee range.
        // If not, keep closing in so we can pathfind around any blocking block.
        double stopDist = hasLineOfSight(client, creature)
                ? STOP_AT_CREATURE_DIST
                : STOP_AT_CREATURE_DIST_LOS;

        moveToward(client, creature.position(), true, stopDist);
        handleVerticalMovement(client);
    }

    private static void tickReturning(Minecraft client) {
        if (fishingSpot == null) {
            state = State.IDLE;
            return;
        }

        Vec3   pos  = client.player.position();
        double dx   = fishingSpot.x - pos.x;
        double dz   = fishingSpot.z - pos.z;
        double dist = Math.sqrt(dx * dx + dz * dz);

        if (dist > STOP_AT_SPOT_DIST) {
            // Still walking back — keep moving.
            // Smooth aim toward fishing angles runs continuously in GameRendererMixin.
            moveToward(client, fishingSpot, true, STOP_AT_SPOT_DIST);
            handleVerticalMovement(client);
            return;
        }

        // Reached the spot — release movement and hand off to IDLE.
        // GameRendererMixin will keep calling smoothAimToAngles until the camera
        // has fully returned to the saved fishing angles (hasFishingTarget() stays
        // true across IDLE and DELAY_BEFORE_CAST, so no correction is lost).
        releaseMovement();
        state = State.IDLE;
    }

    // -------------------------------------------------------------------------
    // Movement helpers
    // -------------------------------------------------------------------------

    private static void moveToward(Minecraft client, Vec3 target, boolean sprint, double stopDist) {
        Vec3   pos  = client.player.position();
        double dx   = target.x - pos.x;
        double dz   = target.z - pos.z;
        double dist = Math.sqrt(dx * dx + dz * dz);

        if (dist <= stopDist) {
            releaseMovement();
            return;
        }

        double yaw      = Math.toRadians(client.player.getYRot());
        double forwardX = -Math.sin(yaw);
        double forwardZ =  Math.cos(yaw);
        double rightX   = -Math.cos(yaw);
        double rightZ   = -Math.sin(yaw);

        double localForward = dx * forwardX + dz * forwardZ;
        double localRight   = dx * rightX   + dz * rightZ;

        desiredForward  = localForward >  MOVE_AXIS_THRESHOLD;
        desiredBackward = localForward < -MOVE_AXIS_THRESHOLD;
        desiredLeft     = localRight   < -MOVE_AXIS_THRESHOLD;
        desiredRight    = localRight   >  MOVE_AXIS_THRESHOLD;
        desiredSprint   = sprint && dist > 2.5 && desiredForward && !desiredBackward;
        movementOwned   = true;
    }

    /**
     * Handles vertical navigation separately from horizontal:
     *
     * - In water: hold jump continuously so the player swims upward.
     *   Minecraft's swim mechanic uses Space to ascend, so this naturally
     *   carries the player to the surface and through shallow water.
     *
     * - On land: measure horizontal progress every STUCK_CHECK_INTERVAL_MS.
     *   If the player has barely moved despite having movement input they've
     *   hit a block edge — jump to climb it. Check frequently (250 ms) so
     *   multi-block staircases are handled without long pauses.
     */
    private static void handleVerticalMovement(Minecraft client) {
        if (!movementOwned) {
            desiredJump = false;
            return;
        }

        if (client.player.isInWater()) {
            // Swim up — reset land-stuck state so it doesn't fire stale data
            // the moment the player surfaces.
            desiredJump   = true;
            lastPlayerPos = null;
            stuckCheckMs  = 0L;
            return;
        }

        // Land path: check for stuck condition on a short interval.
        long now = System.currentTimeMillis();
        if (now < stuckCheckMs) {
            // Leave desiredJump at whatever it was set to last check.
            return;
        }

        Vec3 cur = client.player.position();
        if (lastPlayerPos != null && client.player.onGround()) {
            double moved = Math.sqrt(
                    Math.pow(cur.x - lastPlayerPos.x, 2) +
                    Math.pow(cur.z - lastPlayerPos.z, 2));
            desiredJump = moved < 0.1; // stuck — jump to climb
        } else {
            desiredJump = false;
        }

        lastPlayerPos = cur;
        stuckCheckMs  = now + STUCK_CHECK_INTERVAL_MS;
    }

    // -------------------------------------------------------------------------
    // Movement override interface — called from KeyboardInput mixin
    // -------------------------------------------------------------------------

    /**
     * Returns true only when we actually hold movement directions.
     * Releasing movement (e.g. on player inactive) clears movementOwned,
     * immediately restoring full manual control without losing combat state.
     */
    public static boolean shouldOverrideMovement() {
        return movementOwned;
    }

    public static Vec2 getDesiredMoveVector() {
        float forward  = calculateImpulse(desiredForward,  desiredBackward);
        float sideways = calculateImpulse(desiredLeft,     desiredRight);
        Vec2  v        = new Vec2(sideways, forward);
        float len      = v.length();
        return len > 0 ? new Vec2(v.x / len, v.y / len) : Vec2.ZERO;
    }

    public static Input getDesiredInput() {
        return new Input(
                desiredForward,
                desiredBackward,
                desiredLeft,
                desiredRight,
                desiredJump,
                false,
                desiredSprint
        );
    }

    // -------------------------------------------------------------------------
    // Slot helpers
    // -------------------------------------------------------------------------

    private static void swapToSlot(Minecraft client, int slot) {
        if (client.player == null || slot < 0 || slot > 8) return;
        client.player.getInventory().setSelectedSlot(slot);
    }

    public static void swapToRod(Minecraft client) {
        if (client.player == null) return;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = client.player.getInventory().getItem(i);
            if (stack.getItem() instanceof FishingRodItem) {
                swapToSlot(client, i);
                return;
            }
        }
    }

    // -------------------------------------------------------------------------
    // LOS helper
    // -------------------------------------------------------------------------

    /**
     * Returns true if the player's eye has a clear line of sight to the
     * creature's eye position (no solid block in between).
     */
    private static boolean hasLineOfSight(Minecraft client, LivingEntity creature) {
        if (client.level == null || client.player == null) return false;
        Vec3 eyePos    = client.player.getEyePosition(1.0f);
        Vec3 targetPos = creature.getEyePosition(1.0f);
        BlockHitResult hit = client.level.clip(new ClipContext(
                eyePos, targetPos,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                client.player
        ));
        return hit.getType() == HitResult.Type.MISS;
    }

    // -------------------------------------------------------------------------
    // Misc helpers
    // -------------------------------------------------------------------------

    private static LivingEntity findCreature(Minecraft client) {
        if (creatureId == null || client.level == null) return null;
        for (Entity e : client.level.entitiesForRendering()) {
            if (e.getUUID().equals(creatureId) && e instanceof LivingEntity living) return living;
        }
        return null;
    }

    private static float calculateImpulse(boolean positive, boolean negative) {
        if (positive == negative) return 0f;
        return positive ? 1f : -1f;
    }

    /** Triangular distribution — same humanization used for swing and reaction delays. */
    private static long triangularDelay(int min, int mode, int max) {
        if (min >= max) return min;
        float fc = (float)(mode - min) / (max - min);
        float u  = RANDOM.nextFloat();
        float v;
        if (u < fc) {
            v = min + (float) Math.sqrt(u * (max - min) * (mode - min));
        } else {
            v = max - (float) Math.sqrt((1f - u) * (max - min) * (max - mode));
        }
        return Math.max(min, (long) v);
    }
}
