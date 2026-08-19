package com.teeko.strata.client.render.hud;

import com.teeko.strata.client.state.StrataState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public final class StrataHud {

    private StrataHud() {}

    public static void render(GuiGraphics graphics, float tickDelta) {
        Minecraft client = Minecraft.getInstance();

        TargetingHudOverlay.render(graphics, client, tickDelta);

        if ((StrataState.autoHuntEnabled
                || StrataState.shardTrackerEnabled
                || StrataState.beachballMacroRunning) && client.level != null) {
            HideonleafHudOverlay.render(graphics, client);
        }

        if (StrataState.showCps) {
            CpsWidget.render(
                    graphics,
                    client,
                    StrataState.cpsX,
                    StrataState.cpsY,
                    StrataState.cpsScale,
                    false
            );
        }

        if (StrataState.showClickGraph) {
            ClickGraphOverlay.render(graphics, client);
        }
    }
}
