package com.teeko.enemyboxes.client.mixin.input;

import com.teeko.enemyboxes.client.feature.beachball.BeachballMacro;
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
    private void enemyboxes$applyBeachballMovement(CallbackInfo ci) {
        if (!BeachballMacro.shouldOverrideMovement()) return;

        ClientInputAccessor accessor = (ClientInputAccessor) this;
        Vec2 moveVector = BeachballMacro.getDesiredMoveVector();
        Input input = BeachballMacro.getDesiredInput();
        accessor.enemyboxes$setKeyPresses(input);
        accessor.enemyboxes$setMoveVector(moveVector);
    }
}
