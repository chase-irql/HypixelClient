package com.teeko.enemyboxes.client.mixin.input;

import com.teeko.enemyboxes.client.feature.beachball.BeachballMacro;
import com.teeko.enemyboxes.client.feature.fishing.FishingCombat;
import com.teeko.enemyboxes.client.mixin.accessor.ClientInputAccessor;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public class KeyboardInputBeachballMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void enemyboxes$applyMovementOverrides(CallbackInfo ci) {
        ClientInputAccessor accessor = (ClientInputAccessor) this;

        // Fish combat takes priority — it is incompatible with beachball anyway.
        if (FishingCombat.shouldOverrideMovement()) {
            accessor.enemyboxes$setKeyPresses(FishingCombat.getDesiredInput());
            accessor.enemyboxes$setMoveVector(FishingCombat.getDesiredMoveVector());
        } else if (BeachballMacro.shouldOverrideMovement()) {
            accessor.enemyboxes$setKeyPresses(BeachballMacro.getDesiredInput());
            accessor.enemyboxes$setMoveVector(BeachballMacro.getDesiredMoveVector());
        }
    }
}
