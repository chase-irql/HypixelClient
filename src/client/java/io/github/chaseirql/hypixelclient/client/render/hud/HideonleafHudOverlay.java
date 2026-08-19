package io.github.chaseirql.hypixelclient.client.render.hud;

import io.github.chaseirql.hypixelclient.client.feature.beachball.BeachballMacro;
import io.github.chaseirql.hypixelclient.client.feature.hideonleaf.HideonleafHunt;
import io.github.chaseirql.hypixelclient.client.feature.hideonleaf.HideonleafShardTracker;
import io.github.chaseirql.hypixelclient.client.state.HypixelClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

public final class HideonleafHudOverlay {

    private HideonleafHudOverlay() {}

    public static void render(GuiGraphics graphics, Minecraft client) {
        List<HudLine> lines = new ArrayList<>();

        if (HypixelClientState.autoHuntEnabled) {
            String shulker = HideonleafHunt.debugShulkerDist >= 0
                    ? "Shulker: " + String.format("%.1f", HideonleafHunt.debugShulkerDist) + "m"
                    : "Shulker: none";
            String bullet = HideonleafHunt.debugBulletDist >= 0
                    ? "Bullet: " + String.format("%.1f", HideonleafHunt.debugBulletDist)
                    + "m [" + HideonleafHunt.debugBulletState + "]"
                    : "Bullet: none";
            lines.add(new HudLine("[Hunt] " + HideonleafHunt.debugState, 0xFFFFFF00));
            lines.add(new HudLine(shulker, 0xFFFFFFFF));
            lines.add(new HudLine(bullet, 0xFFFFFFFF));
        }

        if (HypixelClientState.shardTrackerEnabled) {
            HideonleafShardTracker.updateComputed();
            String price = HideonleafShardTracker.priceLoaded
                    ? HideonleafShardTracker.fmtPrice(HideonleafShardTracker.sellPrice)
                    : "...";
            lines.add(new HudLine("Shards: " + HideonleafShardTracker.totalShards, 0xFF00FF00));
            lines.add(new HudLine("Shard Price: " + price, 0xFFFFFFFF));
            lines.add(new HudLine("Session Value: " + HideonleafShardTracker.fmtCoins(HideonleafShardTracker.sessionCoins), 0xFFFFFFFF));
            lines.add(new HudLine("Coins/hr: " + HideonleafShardTracker.fmtCoins(HideonleafShardTracker.coinsPerHour), 0xFFFFFFFF));
        }

        if (HypixelClientState.beachballMacroRunning) {
            int statusColor = HypixelClientState.beachballMacroRunning ? 0xFFFFCC33 : 0xFFAAAAAA;
            int bounceColor = BeachballMacro.getBounceCount() >= 40 ? 0xFF55FF55 : 0xFFFFFFFF;

            lines.add(new HudLine("[Beachball] " + BeachballMacro.getStatus(), statusColor));
            lines.add(new HudLine("Bounces: " + BeachballMacro.getBounceCount() + "/40", bounceColor));

            if (BeachballMacro.hasActiveBall()) {
                lines.add(new HudLine(
                        "Ball: dist=" + fmt(BeachballMacro.getLastHorizontalToPlayer())
                                + " head=" + fmt(BeachballMacro.getLastHeadOffset()),
                        0xFFFFFFFF
                ));
            } else if (BeachballMacro.getLastChatResultCount() >= 0) {
                lines.add(new HudLine("Last Result: " + BeachballMacro.getLastChatResultCount(), 0xFFFFFFFF));
            }
        }

        if (lines.isEmpty()) return;

        int lineHeight = 10;
        int maxWidth = 0;
        for (HudLine line : lines) {
            maxWidth = Math.max(maxWidth, client.font.width(line.text()));
        }

        int boxX = client.getWindow().getGuiScaledWidth() - maxWidth - 8;
        int boxY = 4;

        graphics.fill(boxX - 2, boxY - 2, boxX + maxWidth + 4, boxY + lineHeight * lines.size() + 2, 0xAA000000);
        for (int i = 0; i < lines.size(); i++) {
            HudLine line = lines.get(i);
            graphics.drawString(client.font, line.text(), boxX, boxY + lineHeight * i, line.color(), true);
        }
    }

    private static String fmt(double value) {
        return Double.isNaN(value) ? "n/a" : String.format("%.2f", value);
    }

    private record HudLine(String text, int color) {}
}
