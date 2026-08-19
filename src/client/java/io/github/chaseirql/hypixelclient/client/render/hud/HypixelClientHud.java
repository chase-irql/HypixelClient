package io.github.chaseirql.hypixelclient.client.render.hud;

import io.github.chaseirql.hypixelclient.client.state.HypixelClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public final class HypixelClientHud {

    private HypixelClientHud() {}

    public static void render(GuiGraphics graphics, float tickDelta) {
        Minecraft client = Minecraft.getInstance();

        TargetingHudOverlay.render(graphics, client, tickDelta);

        if ((HypixelClientState.autoHuntEnabled
                || HypixelClientState.shardTrackerEnabled
                || HypixelClientState.beachballMacroRunning) && client.level != null) {
            HideonleafHudOverlay.render(graphics, client);
        }

        if (HypixelClientState.showCps) {
            CpsWidget.render(
                    graphics,
                    client,
                    HypixelClientState.cpsX,
                    HypixelClientState.cpsY,
                    HypixelClientState.cpsScale,
                    false
            );
        }

        if (HypixelClientState.showClickGraph) {
            ClickGraphOverlay.render(graphics, client);
        }
    }
}
