package io.github.chaseirql.hypixelclient.client.mixin.input;

import io.github.chaseirql.hypixelclient.client.feature.beachball.BeachballMacro;
import io.github.chaseirql.hypixelclient.client.feature.fishing.FishingCombat;
import io.github.chaseirql.hypixelclient.client.mixin.accessor.ClientInputAccessor;
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
    private void hypixelclient$applyMovementOverrides(CallbackInfo ci) {
        ClientInputAccessor accessor = (ClientInputAccessor) this;

        // Fish combat takes priority — it is incompatible with beachball anyway.
        if (FishingCombat.shouldOverrideMovement()) {
            accessor.hypixelclient$setKeyPresses(FishingCombat.getDesiredInput());
            accessor.hypixelclient$setMoveVector(FishingCombat.getDesiredMoveVector());
        } else if (BeachballMacro.shouldOverrideMovement()) {
            accessor.hypixelclient$setKeyPresses(BeachballMacro.getDesiredInput());
            accessor.hypixelclient$setMoveVector(BeachballMacro.getDesiredMoveVector());
        }
    }
}
