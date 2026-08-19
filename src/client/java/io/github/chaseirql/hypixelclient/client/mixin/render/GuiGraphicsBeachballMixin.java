package io.github.chaseirql.hypixelclient.client.mixin.render;

import io.github.chaseirql.hypixelclient.client.feature.beachball.BeachballMacro;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsBeachballMixin {

    @Inject(
            method = "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V",
            at = @At("HEAD")
    )
    private void hypixelclient$captureBeachballHudText(
            Font font,
            FormattedCharSequence formattedCharSequence,
            int x,
            int y,
            int color,
            boolean shadow,
            CallbackInfo ci
    ) {
        GuiGraphics graphics = (GuiGraphics) (Object) this;
        BeachballMacro.onHudTextRendered(
                flatten(formattedCharSequence),
                x,
                y,
                graphics.guiWidth(),
                graphics.guiHeight()
        );
    }

    private static String flatten(FormattedCharSequence text) {
        if (text == null) return "";

        StringBuilder builder = new StringBuilder();
        text.accept((index, style, codePoint) -> {
            builder.appendCodePoint(codePoint);
            return true;
        });
        return builder.toString();
    }
}
