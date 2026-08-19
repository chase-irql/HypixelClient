package com.teeko.strata.client.mixin.input;

import com.teeko.strata.client.combat.AutoClicker;
import com.teeko.strata.client.combat.CpsTracker;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class MinecraftAttackMixin {

    @Inject(
            method = "startAttack",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;swing(Lnet/minecraft/world/InteractionHand;)V"
            )
    )
    private void onStartAttack(CallbackInfoReturnable<Boolean> cir) {
        CpsTracker.recordAttack();
        AutoClicker.pushClickCps();
    }
}
