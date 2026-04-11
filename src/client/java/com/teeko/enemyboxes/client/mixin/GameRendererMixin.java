package com.teeko.enemyboxes.client.mixin;

import com.teeko.enemyboxes.client.EnemyBoxesAim;
import com.teeko.enemyboxes.client.EnemyBoxesRenderer;
import com.teeko.enemyboxes.client.EnemyBoxesState;
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
        // Bail if aimbot is toggled off
        if (!EnemyBoxesState.aimbotEnabled) return;
        if (EnemyBoxesState.lockedTarget == null) return;

        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) return;

        for (Entity entity : client.level.entitiesForRendering()) {
            if (!entity.getUUID().equals(EnemyBoxesState.lockedTarget)) continue;
            if (!(entity instanceof LivingEntity living) || !living.isAlive()) continue;
            EnemyBoxesAim.aimAtEntity(client, entity);
            break;
        }
    }
}