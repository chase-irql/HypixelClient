package com.teeko.enemyboxes.client.render.hud;

import com.teeko.enemyboxes.client.state.EnemyBoxesState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public final class EnemyBoxesHud {

    private EnemyBoxesHud() {}

    public static void render(GuiGraphics graphics, float tickDelta) {
        Minecraft client = Minecraft.getInstance();

        TargetingHudOverlay.render(graphics, client, tickDelta);

        if ((EnemyBoxesState.autoHuntEnabled || EnemyBoxesState.shardTrackerEnabled) && client.level != null) {
            HideonleafHudOverlay.render(graphics, client);
        }

        if (EnemyBoxesState.showCps) {
            CpsWidget.render(
                    graphics,
                    client,
                    EnemyBoxesState.cpsX,
                    EnemyBoxesState.cpsY,
                    EnemyBoxesState.cpsScale,
                    false
            );
        }

        if (EnemyBoxesState.showClickGraph) {
            ClickGraphOverlay.render(graphics, client);
        }
    }
}
