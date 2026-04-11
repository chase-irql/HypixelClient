package com.teeko.enemyboxes.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public final class EnemyBoxesHud {

    private static final int SEGMENTS = 128;
    private static final int COLOR    = 0xFFFFFF00;

    public static void render(GuiGraphics graphics) {
        Minecraft client = Minecraft.getInstance();

        // FOV circle
        if (EnemyBoxesState.enabled && EnemyBoxesState.showFovCircle && EnemyBoxesState.hasTarget()
                && client.level != null) {
            float cx     = client.getWindow().getGuiScaledWidth()  / 2f;
            float cy     = client.getWindow().getGuiScaledHeight() / 2f;
            float radius = EnemyBoxesState.lockFov;

            for (int i = 0; i < SEGMENTS; i++) {
                double a0 = 2 * Math.PI * i       / SEGMENTS;
                double a1 = 2 * Math.PI * (i + 1) / SEGMENTS;

                int x0 = (int)(cx + Math.cos(a0) * radius);
                int y0 = (int)(cy + Math.sin(a0) * radius);
                int x1 = (int)(cx + Math.cos(a1) * radius);
                int y1 = (int)(cy + Math.sin(a1) * radius);

                drawLine(graphics, x0, y0, x1, y1, COLOR);
            }
        }

        // CPS counter
        if (EnemyBoxesState.showCps) {
            renderCps(graphics, client, EnemyBoxesState.cpsX, EnemyBoxesState.cpsY, EnemyBoxesState.cpsScale, false);
        }
    }

    public static void renderCps(GuiGraphics graphics, Minecraft client, float x, float y, float scale, boolean highlight) {
        String text = "CPS: " + EnemyBoxesClient.getCps();
        int bgColor   = highlight ? 0xCC4444FF : 0xAA000000;
        int textColor = 0xFFFFFF00;

        int w = (int)(client.font.width(text) * scale) + 6;
        int h = (int)(9 * scale) + 4;

        graphics.fill((int)x, (int)y, (int)x + w, (int)y + h, bgColor);

        graphics.pose().pushMatrix();
        graphics.pose().translate(x + 3, y + 2);
        graphics.pose().scale(scale, scale);
        graphics.drawString(client.font, text, 0, 0, textColor, true);
        graphics.pose().popMatrix();
    }

    public static int getCpsWidth(Minecraft client, float scale) {
        return (int)(client.font.width("CPS: " + EnemyBoxesClient.getCps()) * scale) + 6;
    }

    public static int getCpsHeight(float scale) {
        return (int)(9 * scale) + 4;
    }

    private static void drawLine(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        int x = x0, y = y0;

        while (true) {
            graphics.fill(x, y, x + 1, y + 1, color);
            if (x == x1 && y == y1) break;
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; x += sx; }
            if (e2 <  dx) { err += dx; y += sy; }
        }
    }
}