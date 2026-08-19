package io.github.chaseirql.hypixelclient.client.render.hud;

import io.github.chaseirql.hypixelclient.client.combat.AutoClicker;
import io.github.chaseirql.hypixelclient.client.state.HypixelClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public final class ClickGraphOverlay {

    private static final int GRAPH_W = 240;
    private static final int GRAPH_H = 68;
    private static final int GRAPH_PAD = 6;

    private ClickGraphOverlay() {}

    public static void render(GuiGraphics graphics, Minecraft client) {
        int screenH = client.getWindow().getGuiScaledHeight();
        int gx = 4;
        int gy = screenH - 22 - 6 - GRAPH_H;

        graphics.fill(gx, gy, gx + GRAPH_W, gy + GRAPH_H, 0xAA000000);

        float plotX0 = gx + GRAPH_PAD;
        float plotX1 = gx + GRAPH_W - GRAPH_PAD;
        float plotY0 = gy + GRAPH_PAD + 8;
        float plotY1 = gy + GRAPH_H - GRAPH_PAD;
        float plotW = plotX1 - plotX0;
        float plotH = plotY1 - plotY0;

        int minCps = HypixelClientState.acCpsMin;
        int modeCps = HypixelClientState.acCpsMode;
        int maxCps = HypixelClientState.acCpsMax;

        float yLo = minCps * 0.4f;
        float yHi = maxCps * 1.4f;
        float yRange = Math.max(yHi - yLo, 0.1f);

        float minRefY = plotY0 + (1f - (minCps - yLo) / yRange) * plotH;
        float modeRefY = plotY0 + (1f - (modeCps - yLo) / yRange) * plotH;
        float maxRefY = plotY0 + (1f - (maxCps - yLo) / yRange) * plotH;

        HudDrawUtil.drawLine(graphics, (int) plotX0, (int) minRefY, (int) plotX1, (int) minRefY, 0x4A4AB4FF);
        HudDrawUtil.drawLine(graphics, (int) plotX0, (int) modeRefY, (int) plotX1, (int) modeRefY, 0x4AFFC840);
        HudDrawUtil.drawLine(graphics, (int) plotX0, (int) maxRefY, (int) plotX1, (int) maxRefY, 0x4AFF5A5A);

        graphics.drawString(client.font, "Live Activity",
                gx + GRAPH_PAD, gy + GRAPH_PAD - 1, 0xFFAAAAAA, false);

        int count = AutoClicker.historyCount;
        int head = AutoClicker.historyHead;

        if (count > 1) {
            int visible = Math.min(count, 80);
            float step = plotW / (float) (visible - 1);

            int prevPx = -1;
            int prevPy = -1;

            for (int i = 0; i < visible; i++) {
                int idx = (head - visible + i + AutoClicker.HISTORY_SIZE) % AutoClicker.HISTORY_SIZE;
                float cps = AutoClicker.clickHistory[idx];

                int px = (int) (plotX0 + i * step);
                int py = (int) (plotY0 + (1f - (cps - yLo) / yRange) * plotH);
                py = Math.max((int) plotY0, Math.min((int) plotY1, py));

                if (prevPx >= 0) {
                    HudDrawUtil.drawLine(graphics, prevPx, prevPy, px, py, 0xCC78DCA0);
                }
                graphics.fill(px - 1, py - 1, px + 2, py + 2, 0xFF78DCA0);

                prevPx = px;
                prevPy = py;
            }

            int[] labelRefY = {(int) maxRefY, (int) modeRefY, (int) minRefY};
            int[] labelCps = {maxCps, modeCps, minCps};
            int[] labelColor = {0xFFFF5A5A, 0xFFFFC840, 0xFF4AB4FF};
            int lastY = Integer.MIN_VALUE;
            for (int i = 0; i < 3; i++) {
                int ly = labelRefY[i] - 8;
                if (ly - lastY < 9) continue;
                graphics.drawString(client.font, labelCps[i] + "",
                        gx + GRAPH_PAD, ly, labelColor[i], false);
                lastY = ly;
            }
        } else {
            String hint = "Hold LMB to see activity";
            int textWidth = client.font.width(hint);
            graphics.drawString(client.font, hint,
                    gx + (GRAPH_W - textWidth) / 2,
                    gy + GRAPH_H / 2 - 4,
                    0xFF555555, false);
        }
    }
}
