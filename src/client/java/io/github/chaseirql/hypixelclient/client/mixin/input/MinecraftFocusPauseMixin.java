package io.github.chaseirql.hypixelclient.client.mixin.input;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftFocusPauseMixin {

    // Only suppress the pause when the window genuinely lost focus.
    // Escape still works because isWindowActive() is true while the window is focused.
    @Inject(method = "pauseGame", at = @At("HEAD"), cancellable = true)
    private void onPauseGame(boolean focus, CallbackInfo ci) {
        if (!((Minecraft)(Object)this).isWindowActive()) ci.cancel();
    }
}
