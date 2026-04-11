package com.teeko.enemyboxes.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public final class EnemyBoxesHud {

    private static final int SEGMENTS = 128;
    private static final int COLOR = 0xFFFFFF00; // yellow, ARGB

    public static void render(GuiGraphics graphics) {
        if (!EnemyBoxesState.enabled) return;
        if (!EnemyBoxesState.showFovCircle) return;
        if (!EnemyBoxesState.hasTarget()) return;

        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;

        float cx = client.getWindow().getGuiScaledWidth() / 2f;
        float cy = client.getWindow().getGuiScaledHeight() / 2f;
        float radius = EnemyBoxesState.lockFov;

        for (int i = 0; i < SEGMENTS; i++) {
            double a0 = 2 * Math.PI * i / SEGMENTS;
            double a1 = 2 * Math.PI * (i + 1) / SEGMENTS;

            int x0 = (int)(cx + Math.cos(a0) * radius);
            int y0 = (int)(cy + Math.sin(a0) * radius);
            int x1 = (int)(cx + Math.cos(a1) * radius);
            int y1 = (int)(cy + Math.sin(a1) * radius);

            // Draw line segment between (x0,y0) and (x1,y1) using fill
            drawLine(graphics, x0, y0, x1, y1, COLOR);
        }
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
            if (e2 < dx)  { err += dx; y += sy; }
        }
    }
}