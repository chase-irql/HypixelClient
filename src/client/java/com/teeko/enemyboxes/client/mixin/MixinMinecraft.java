package com.teeko.enemyboxes.client.mixin;

import com.teeko.enemyboxes.client.AutoClicker;
import com.teeko.enemyboxes.client.EnemyBoxesClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class MixinMinecraft {

    @Inject(
            method = "startAttack",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;swing(Lnet/minecraft/world/InteractionHand;)V"
            )
    )
    private void onStartAttack(CallbackInfoReturnable<Boolean> cir) {
        EnemyBoxesClient.recordAttack();
        AutoClicker.pushClickCps();
    }
}
