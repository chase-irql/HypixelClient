package com.teeko.enemyboxes.client.mixin.render;

import com.teeko.enemyboxes.client.EnemyBoxesClient;
import com.teeko.enemyboxes.client.combat.AutoClicker;
import com.teeko.enemyboxes.client.feature.lockon.EnemyBoxesAim;
import com.teeko.enemyboxes.client.feature.lockon.LockOnController;
import com.teeko.enemyboxes.client.render.world.EnemyBoxesRenderer;
import com.teeko.enemyboxes.client.state.EnemyBoxesState;
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
        EnemyBoxesRenderer.close();
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void onRenderHead(DeltaTracker deltaTracker, boolean bl, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) return;

        if (!EnemyBoxesClient.isPlayerActive(client)) return;

        // Regular aimbot — aims at the locked living entity
        if (EnemyBoxesState.aimbotEnabled && EnemyBoxesState.lockedTarget != null) {
            for (Entity entity : client.level.entitiesForRendering()) {
                if (!entity.getUUID().equals(EnemyBoxesState.lockedTarget)) continue;
                if (!(entity instanceof LivingEntity living) || !living.isAlive()) continue;
                EnemyBoxesAim.aimAtEntity(client, entity);
                break;
            }
        }

        // Hunt aim — aims at the locked shulker bullet without LOS check
        if (EnemyBoxesState.autoHuntEnabled && EnemyBoxesState.huntLockedBullet != null) {
            for (Entity entity : client.level.entitiesForRendering()) {
                if (!entity.getUUID().equals(EnemyBoxesState.huntLockedBullet)) continue;
                EnemyBoxesAim.aimAtPosition(client, entity.getBoundingBox().getCenter());
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
