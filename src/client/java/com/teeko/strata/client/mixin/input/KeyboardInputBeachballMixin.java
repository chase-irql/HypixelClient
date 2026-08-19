package com.teeko.strata.client.mixin.input;

import com.teeko.strata.client.feature.beachball.BeachballMacro;
import com.teeko.strata.client.feature.fishing.FishingCombat;
import com.teeko.strata.client.mixin.accessor.ClientInputAccessor;
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
    private void strata$applyMovementOverrides(CallbackInfo ci) {
        ClientInputAccessor accessor = (ClientInputAccessor) this;

        // Fish combat takes priority — it is incompatible with beachball anyway.
        if (FishingCombat.shouldOverrideMovement()) {
            accessor.strata$setKeyPresses(FishingCombat.getDesiredInput());
            accessor.strata$setMoveVector(FishingCombat.getDesiredMoveVector());
        } else if (BeachballMacro.shouldOverrideMovement()) {
            accessor.strata$setKeyPresses(BeachballMacro.getDesiredInput());
            accessor.strata$setMoveVector(BeachballMacro.getDesiredMoveVector());
        }
    }
}
