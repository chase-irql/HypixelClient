package io.github.chaseirql.hypixelclient.client.feature.chat;

import com.mojang.authlib.GameProfile;
import io.github.chaseirql.hypixelclient.client.feature.beachball.BeachballMacro;
import io.github.chaseirql.hypixelclient.client.integration.BotEventClient;
import io.github.chaseirql.hypixelclient.client.state.HypixelClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChatMentionAlerts {

    private static final Pattern ANGLE_BRACKET_CHAT_PATTERN =
            Pattern.compile("^<([A-Za-z0-9_]{1,16})>\\s+(.+)$");
    private static final Pattern COLON_CHAT_PATTERN =
            Pattern.compile("^(.+?)\\s*:\\s+(.+)$");
    private static final Pattern ARROW_CHAT_PATTERN =
            Pattern.compile("^(.+?)\\s+>\\s+(.+)$");
    private static final Pattern USERNAME_TOKEN_PATTERN =
            Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final Pattern MACRO_KEYWORD_PATTERN =
            Pattern.compile("(?i)\\bmacro(?:ing)?\\b");
    private static final Pattern COMMAND_FAILURE_PATTERN =
            Pattern.compile("(?i)\\b(?:failed|error)\\b");
    private static final long IS_RETRY_DELAY_MS = 2500L;
    private static final long IS_RESULT_WINDOW_MS = 5000L;
    private static final long MESSAGE_DEDUP_MS = 1500L;

    private static boolean awaitingIslandWarpResult = false;
    private static long islandWarpResultDeadlineMs = 0L;
    private static long nextIslandWarpAttemptMs = 0L;
    private static String lastForwardedMessageKey = "";
    private static long lastForwardedMessageMs = 0L;

    private ChatMentionAlerts() {}

    public static void onChatMessage(Minecraft client, Component message, GameProfile sender) {
        if (client == null || client.player == null || message == null || sender == null) return;

        String localUsername = sanitize(client.player.getName().getString());
        String senderName = sanitize(sender.name());
        if (localUsername.isEmpty() || senderName.isEmpty()) return;
        if (senderName.equalsIgnoreCase(localUsername)) return;

        String rawMessage = sanitize(stripFormatting(message.getString()));
        if (rawMessage.isEmpty()) return;

        String chatBody = stripSenderPrefix(rawMessage, senderName);
        handleDetectedPlayerMessage(client, localUsername, senderName, chatBody, "chat");
    }

    public static void onGameMessage(Minecraft client, Component message, boolean overlay) {
        if (overlay || message == null) return;

        if (awaitingIslandWarpResult) {
            long now = System.currentTimeMillis();
            if (now > islandWarpResultDeadlineMs) {
                clearIslandWarpState();
            } else {
                String normalized = normalize(stripFormatting(message.getString()));
                if (!normalized.isEmpty() && COMMAND_FAILURE_PATTERN.matcher(normalized).find()) {
                    awaitingIslandWarpResult = false;
                    islandWarpResultDeadlineMs = 0L;
                    nextIslandWarpAttemptMs = now + IS_RETRY_DELAY_MS;
                }
            }
        }

        if (client == null || client.player == null) {
            return;
        }

        ParsedPlayerChat parsedChat = parsePlayerChatMessage(message.getString());
        if (parsedChat == null) {
            return;
        }

        handleDetectedPlayerMessage(
                client,
                sanitize(client.player.getName().getString()),
                parsedChat.senderName(),
                parsedChat.message(),
                "system"
        );
    }

    public static void tick(Minecraft client) {
        long now = System.currentTimeMillis();

        if (awaitingIslandWarpResult && now > islandWarpResultDeadlineMs) {
            clearIslandWarpState();
        }

        if (nextIslandWarpAttemptMs == 0L || now < nextIslandWarpAttemptMs) {
            return;
        }

        attemptIslandWarp(client);
    }

    private static boolean containsLocalUsername(String message, String localUsername) {
        Pattern mentionPattern = Pattern.compile(
                "(?i)(?<![a-z0-9_])" + Pattern.quote(localUsername) + "(?![a-z0-9_])"
        );
        return mentionPattern.matcher(message).find();
    }

    private static boolean containsMacroKeyword(String message) {
        return MACRO_KEYWORD_PATTERN.matcher(message).find();
    }

    private static void handleDetectedPlayerMessage(
            Minecraft client,
            String localUsername,
            String senderName,
            String chatBody,
            String source
    ) {
        String cleanLocalUsername = sanitize(localUsername);
        String cleanSenderName = sanitize(senderName);
        String cleanChatBody = sanitize(chatBody);

        if (cleanLocalUsername.isEmpty() || cleanSenderName.isEmpty() || cleanChatBody.isEmpty()) {
            return;
        }

        boolean containsLocalUsername = containsLocalUsername(cleanChatBody, cleanLocalUsername);
        if (containsLocalUsername) {
            debugLog(
                    "Candidate from " + source
                            + ": sender=[" + cleanSenderName + "] message=[" + cleanChatBody + "]"
            );
        }

        if (cleanSenderName.equalsIgnoreCase(cleanLocalUsername)) {
            if (containsLocalUsername) {
                debugLog("Skipping candidate because sender matches local username.");
            }
            return;
        }

        if (!containsLocalUsername) {
            return;
        }

        if (isDuplicateForward(cleanSenderName, cleanChatBody)) {
            debugLog("Skipping candidate as duplicate within dedupe window.");
            return;
        }

        if (containsMacroKeyword(cleanChatBody) && HypixelClientState.beachballMacroRunning) {
            handleMacroCallout(client, cleanSenderName);
        }

        if (!HypixelClientState.chatNameMentionAlertsEnabled) {
            debugLog("Mention alert matched but Name Mention Alerts are disabled.");
            return;
        }

        DirectChatMessage forwardedMessage = extractForwardedMessage(cleanChatBody, cleanLocalUsername);
        boolean queued = BotEventClient.sendChatMentionEvent(
                cleanLocalUsername,
                cleanSenderName,
                forwardedMessage.message(),
                forwardedMessage.directMessage()
        );
        debugLog(
                "Forward result: queued=" + queued
                        + " directMessage=" + forwardedMessage.directMessage()
                        + " forwardedBody=[" + forwardedMessage.message() + "]"
        );
    }

    private static DirectChatMessage extractForwardedMessage(String message, String localUsername) {
        String cleanMessage = sanitize(message);
        if (cleanMessage.isEmpty()) {
            return new DirectChatMessage("", false);
        }

        Pattern directMessagePattern = Pattern.compile(
                "(?i)^" + Pattern.quote(localUsername)
                        + "(?![a-z0-9_])(?:\\s*[:,>-]?\\s+)(.+)$"
        );
        Matcher matcher = directMessagePattern.matcher(cleanMessage);
        if (!matcher.matches()) {
            return new DirectChatMessage(cleanMessage, false);
        }

        String forwardedBody = sanitize(matcher.group(1));
        if (forwardedBody.isEmpty()) {
            return new DirectChatMessage(cleanMessage, false);
        }

        return new DirectChatMessage(forwardedBody, true);
    }

    private static ParsedPlayerChat parsePlayerChatMessage(String rawMessage) {
        String cleanMessage = sanitize(stripFormatting(rawMessage));
        if (cleanMessage.isEmpty()) {
            return null;
        }

        ParsedPlayerChat parsed = matchPlayerChatPattern(ANGLE_BRACKET_CHAT_PATTERN, cleanMessage);
        if (parsed != null) {
            return parsed;
        }

        parsed = matchDelimitedPlayerChat(COLON_CHAT_PATTERN, cleanMessage);
        if (parsed != null) {
            debugLog(
                    "Parsed colon chat: sender=[" + parsed.senderName()
                            + "] message=[" + parsed.message() + "] raw=[" + cleanMessage + "]"
            );
            return parsed;
        }

        parsed = matchDelimitedPlayerChat(ARROW_CHAT_PATTERN, cleanMessage);
        if (parsed != null) {
            debugLog(
                    "Parsed arrow chat: sender=[" + parsed.senderName()
                            + "] message=[" + parsed.message() + "] raw=[" + cleanMessage + "]"
            );
        }

        return parsed;
    }

    private static ParsedPlayerChat matchPlayerChatPattern(Pattern pattern, String message) {
        Matcher matcher = pattern.matcher(message);
        if (!matcher.matches()) {
            return null;
        }

        String senderName = sanitize(matcher.group(1));
        String chatBody = sanitize(matcher.group(2));
        if (senderName.isEmpty() || chatBody.isEmpty()) {
            return null;
        }

        return new ParsedPlayerChat(senderName, chatBody);
    }

    private static ParsedPlayerChat matchDelimitedPlayerChat(Pattern pattern, String message) {
        Matcher matcher = pattern.matcher(message);
        if (!matcher.matches()) {
            return null;
        }

        String senderPart = sanitize(matcher.group(1));
        String chatBody = sanitize(matcher.group(2));
        String senderName = extractSenderName(senderPart);
        if (senderName.isEmpty() || chatBody.isEmpty()) {
            return null;
        }

        return new ParsedPlayerChat(senderName, chatBody);
    }

    private static void handleMacroCallout(Minecraft client, String senderName) {
        boolean macroStopped = BeachballMacro.stopForChatCallout(
                client,
                "Macro callout from " + fallback(senderName, "chat")
        );
        if (!macroStopped) {
            return;
        }

        if (client.player != null) {
            client.player.displayClientMessage(
                    Component.literal(
                            "[HypixelClient] Macro callout detected from "
                                    + fallback(senderName, "chat")
                                    + ". Stopped the beachball macro and running /is."
                    ),
                    false
            );
        }

        nextIslandWarpAttemptMs = 0L;
        attemptIslandWarp(client);
    }

    private static void attemptIslandWarp(Minecraft client) {
        if (client == null || client.player == null || client.player.connection == null) {
            long now = System.currentTimeMillis();
            nextIslandWarpAttemptMs = now + IS_RETRY_DELAY_MS;
            awaitingIslandWarpResult = false;
            islandWarpResultDeadlineMs = 0L;
            return;
        }

        client.player.connection.sendCommand("is");
        awaitingIslandWarpResult = true;
        islandWarpResultDeadlineMs = System.currentTimeMillis() + IS_RESULT_WINDOW_MS;
        nextIslandWarpAttemptMs = 0L;
    }

    private static void clearIslandWarpState() {
        awaitingIslandWarpResult = false;
        islandWarpResultDeadlineMs = 0L;
        nextIslandWarpAttemptMs = 0L;
    }

    private static boolean isDuplicateForward(String senderName, String message) {
        String messageKey = normalize(senderName) + "|" + normalize(message);
        long now = System.currentTimeMillis();

        if (messageKey.equals(lastForwardedMessageKey) && now - lastForwardedMessageMs <= MESSAGE_DEDUP_MS) {
            return true;
        }

        lastForwardedMessageKey = messageKey;
        lastForwardedMessageMs = now;
        return false;
    }

    private static String stripSenderPrefix(String rawMessage, String senderName) {
        ParsedPlayerChat parsed = parsePlayerChatMessage(rawMessage);
        if (parsed != null && parsed.senderName().equalsIgnoreCase(senderName)) {
            return parsed.message();
        }

        String[] prefixes = {
                "<" + senderName + "> ",
                senderName + ": ",
                senderName + " > "
        };

        for (String prefix : prefixes) {
            if (rawMessage.regionMatches(true, 0, prefix, 0, prefix.length())) {
                return rawMessage.substring(prefix.length()).trim();
            }
        }

        return rawMessage;
    }

    private static String extractSenderName(String senderPart) {
        String cleanSenderPart = sanitize(senderPart);
        if (cleanSenderPart.isEmpty()) {
            return "";
        }

        Matcher matcher = USERNAME_TOKEN_PATTERN.matcher(cleanSenderPart);
        String senderName = "";
        while (matcher.find()) {
            senderName = matcher.group();
        }

        return senderName;
    }

    private static String stripFormatting(String text) {
        if (text == null || text.isEmpty()) return "";
        return text.replaceAll("\u00A7.", "");
    }

    private static String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).trim();
    }

    private static String fallback(String value, String fallback) {
        String clean = sanitize(value);
        return clean.isEmpty() ? fallback : clean;
    }

    private static String sanitize(String value) {
        return value == null ? "" : value.trim();
    }

    private static void debugLog(String message) {
        System.out.println("[HypixelClient] Mention debug: " + message);
    }

    private record DirectChatMessage(String message, boolean directMessage) {}

    private record ParsedPlayerChat(String senderName, String message) {}
}
