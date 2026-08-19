package com.teeko.strata.client.mixin.render;

import com.teeko.strata.client.StrataClient;
import com.teeko.strata.client.combat.AutoClicker;
import com.teeko.strata.client.feature.fishing.FishingCombat;
import com.teeko.strata.client.feature.lockon.StrataAim;
import com.teeko.strata.client.feature.lockon.LockOnController;
import com.teeko.strata.client.render.world.StrataRenderer;
import com.teeko.strata.client.state.StrataState;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Inject(method = "close", at = @At("RETURN"))
    private void onClose(CallbackInfo ci) {
        StrataRenderer.close();
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void onRenderHead(DeltaTracker deltaTracker, boolean bl, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) return;

        if (!StrataClient.isPlayerActive(client)) return;

        // Regular aimbot — aims at the locked living entity
        if (StrataState.aimbotEnabled && StrataState.lockedTarget != null) {
            for (Entity entity : client.level.entitiesForRendering()) {
                if (!entity.getUUID().equals(StrataState.lockedTarget)) continue;
                if (!(entity instanceof LivingEntity living) || !living.isAlive()) continue;
                StrataAim.aimAtEntity(client, entity);
                break;
            }
        }

        // Fish combat aim — same smoothing settings as regular aimbot, always on
        // during the kill phase regardless of the aimbotEnabled toggle.
        if (FishingCombat.isAimActive() && StrataState.lockedTarget != null) {
            for (Entity entity : client.level.entitiesForRendering()) {
                if (!entity.getUUID().equals(StrataState.lockedTarget)) continue;
                if (!(entity instanceof LivingEntity living) || !living.isAlive()) continue;
                StrataAim.aimAtEntity(client, entity);
                break;
            }
        }

        // Fish combat return — smoothly rotates the camera back to the saved fishing
        // angles whenever we are not actively chasing a creature.  Running this
        // continuously (not just during RETURNING) means the correction keeps going
        // even after FishingCombat enters IDLE / DELAY_BEFORE_CAST, so the camera
        // is always aimed at the fishing spot before the next cast.
        if (StrataState.autoFisherEnabled
                && FishingCombat.hasFishingTarget()
                && !FishingCombat.isAimActive()) {
            StrataAim.smoothAimToAngles(client, FishingCombat.getFishingYaw(), FishingCombat.getFishingPitch());
        }

        // Hunt aim — aims at the locked shulker bullet without LOS check
        if (StrataState.autoHuntEnabled && StrataState.huntLockedBullet != null) {
            for (Entity entity : client.level.entitiesForRendering()) {
                if (!entity.getUUID().equals(StrataState.huntLockedBullet)) continue;
                StrataAim.aimAtPosition(client, entity.getBoundingBox().getCenter());
                break;
            }
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void onRenderTail(DeltaTracker deltaTracker, boolean bl, CallbackInfo ci) {
        // Run attack automation only after vanilla refreshes pick()/hitResult
        // so both paths see the same target state the player sees this frame.
        Minecraft client = Minecraft.getInstance();
        LockOnController.tickAutoSwing(client);
        AutoClicker.tick(client);
    }
}
