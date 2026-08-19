package com.teeko.strata.client.feature.chat;

import com.teeko.strata.client.feature.beachball.BeachballMacro;
import com.teeko.strata.client.feature.fishing.AutoFisher;
import com.teeko.strata.client.integration.BotEventClient;
import com.teeko.strata.client.state.StrataState;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.regex.Pattern;

public final class ServerShutdownAlerts {

    private static final long ALERT_DEDUP_MS = 90_000L;
    private static final Pattern WARP_OUT_WARNING_PATTERN = Pattern.compile(
            "^you have \\d+ seconds to warp out! click to warp now!$",
            Pattern.CASE_INSENSITIVE
    );

    private static long lastAlertSentMs = 0L;

    private ServerShutdownAlerts() {}

    public static void onGameMessage(Minecraft client, Component message, boolean overlay) {
        if (overlay || client == null || message == null) return;

        String rawMessage = sanitize(message.getString());
        if (rawMessage.isEmpty()) return;

        if (!isShutdownWarning(rawMessage)) {
            return;
        }

        BeachballMacro.requestStopAfterCurrentRound(client, "Server shutdown warning detected.");
        AutoFisher.forceStop(client, "Server shutdown warning detected.");

        if (!StrataState.serverShutdownAlertsEnabled || client.player == null) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastAlertSentMs < ALERT_DEDUP_MS) {
            return;
        }

        lastAlertSentMs = now;
        BotEventClient.sendServerShutdownEvent(client.player.getName().getString(), rawMessage);
    }

    private static boolean isShutdownWarning(String message) {
        String normalized = stripFormatting(message).toLowerCase(Locale.ROOT).trim();
        if (normalized.isEmpty()) {
            return false;
        }

        return normalized.equals("[important] this server will restart soon: scheduled reboot")
                || WARP_OUT_WARNING_PATTERN.matcher(normalized).matches()
                || normalized.equals("evacuating to hub...");
    }

    private static String stripFormatting(String text) {
        if (text == null || text.isEmpty()) return "";
        return text.replaceAll("\u00A7.", "");
    }

    private static String sanitize(String value) {
        return value == null ? "" : value.trim();
    }
}
