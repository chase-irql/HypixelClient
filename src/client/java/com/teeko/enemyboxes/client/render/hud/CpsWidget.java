package com.teeko.enemyboxes.client.render.hud;

import com.teeko.enemyboxes.client.combat.CpsTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public final class CpsWidget {

    private CpsWidget() {}

    public static void render(GuiGraphics graphics, Minecraft client, float x, float y, float scale, boolean highlight) {
        String text = "CPS: " + CpsTracker.getCps();
        int bgColor = highlight ? 0xCC4444FF : 0xAA000000;
        int textColor = 0xFFFFFF00;

        int width = (int) (client.font.width(text) * scale) + 6;
        int height = (int) (9 * scale) + 4;

        graphics.fill((int) x, (int) y, (int) x + width, (int) y + height, bgColor);

        graphics.pose().pushMatrix();
        graphics.pose().translate(x + 3, y + 2);
        graphics.pose().scale(scale, scale);
        graphics.drawString(client.font, text, 0, 0, textColor, true);
        graphics.pose().popMatrix();
    }

    public static int getWidth(Minecraft client, float scale) {
        return (int) (client.font.width("CPS: " + CpsTracker.getCps()) * scale) + 6;
    }

    public static int getHeight(float scale) {
        return (int) (9 * scale) + 4;
    }
}
