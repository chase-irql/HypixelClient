package io.github.chaseirql.hypixelclient.client.integration;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import io.github.chaseirql.hypixelclient.client.config.HypixelClientConfig;
import io.github.chaseirql.hypixelclient.client.state.HypixelClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

import java.net.Proxy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/**
 * Optional asynchronous client for forwarding selected in-game events to a user-configured
 * HTTPS service. Calls are disabled unless both an event type and a server URL are explicitly
 * enabled in the configuration.
 *
 * <p>Authentication uses Minecraft's session join flow and never includes the Minecraft access
 * token in a request to the configured event service.</p>
 */
public final class BotEventClient {

    private static final Gson GSON = new Gson();
    private static final Duration HTTP_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration HTTP_REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(HTTP_CONNECT_TIMEOUT)
            .build();
    private static final int AUTH_REQUEST_RETRY_ATTEMPTS = 5;
    private static final long AUTH_REQUEST_RETRY_DELAY_MS = 300L;
    private static final MinecraftSessionService SESSION_SERVICE =
            new YggdrasilAuthenticationService(Proxy.NO_PROXY).createMinecraftSessionService();
    private static final AtomicInteger EVENT_SEQUENCE = new AtomicInteger();
    private static final ThreadPoolExecutor EVENT_EXECUTOR = new ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(),
            task -> {
                Thread thread = new Thread(task, "HypixelClient-BotEvents");
                thread.setDaemon(true);
                return thread;
            }
    );

    private BotEventClient() {}

    public static boolean sendBeachballForcedStopEvent(
            String reason,
            String playerName,
            String dimensionId,
            int bounceCount,
            String stateAtStop
    ) {
        AlertSettings settings = loadAlertSettings(() -> HypixelClientState.beachballForcedStopAlertsEnabled);
        if (!settings.enabled || settings.eventEndpointUrl.isEmpty()) {
            return false;
        }

        PlayerIdentity playerIdentity = capturePlayerIdentity();
        if (playerIdentity == null) {
            System.err.println("[HypixelClient] Bot event not sent: unable to read Minecraft session.");
            return false;
        }

        JsonObject data = new JsonObject();
        data.addProperty("playerName", fallback(playerName, "unknown"));
        data.addProperty("bounceCount", Math.max(0, bounceCount));
        data.addProperty("stateAtStop", fallback(stateAtStop, "unknown"));
        data.addProperty("dimensionId", sanitize(dimensionId));
        data.addProperty("reason", fallback(reason, "Beachball macro forced stopped."));
        queueEvent(settings, "beachball_stop", data, playerIdentity);
        return true;
    }

    public static boolean sendAutoFishForcedStopEvent(
            String playerName,
            String dimensionId,
            String stateAtStop,
            String reason
    ) {
        AlertSettings settings = loadAlertSettings(() -> HypixelClientState.autoFisherForcedStopAlertsEnabled);
        if (!settings.enabled || settings.eventEndpointUrl.isEmpty()) {
            return false;
        }

        PlayerIdentity playerIdentity = capturePlayerIdentity();
        if (playerIdentity == null) {
            System.err.println("[HypixelClient] Bot event not sent: unable to read Minecraft session.");
            return false;
        }

        JsonObject data = new JsonObject();
        data.addProperty("playerName", fallback(playerName, "unknown"));
        data.addProperty("stateAtStop", fallback(stateAtStop, "unknown"));
        data.addProperty("dimensionId", sanitize(dimensionId));
        data.addProperty("reason", fallback(reason, "Auto fish macro force stopped."));
        queueEvent(settings, "fishing_stop", data, playerIdentity);
        return true;
    }

    public static boolean sendChatMentionEvent(String localUsername, String senderName, String messageText) {
        return sendChatMentionEvent(localUsername, senderName, messageText, false);
    }

    public static boolean sendChatMentionEvent(
            String localUsername,
            String senderName,
            String messageText,
            boolean directMessage
    ) {
        AlertSettings settings = loadAlertSettings(() -> HypixelClientState.chatNameMentionAlertsEnabled);
        if (!settings.enabled || settings.eventEndpointUrl.isEmpty()) {
            return false;
        }

        PlayerIdentity playerIdentity = capturePlayerIdentity();
        if (playerIdentity == null) {
            System.err.println("[HypixelClient] Bot event not sent: unable to read Minecraft session.");
            return false;
        }

        JsonObject data = new JsonObject();
        data.addProperty("localUsername", fallback(localUsername, "you"));
        data.addProperty("senderName", fallback(senderName, "unknown"));
        data.addProperty("message", fallback(messageText, "n/a"));
        data.addProperty("directMessage", directMessage);
        queueEvent(settings, "chat_mention", data, playerIdentity);
        return true;
    }

    public static boolean sendServerShutdownEvent(String playerName, String triggerMessage) {
        AlertSettings settings = loadAlertSettings(() -> HypixelClientState.serverShutdownAlertsEnabled);
        if (!settings.enabled || settings.eventEndpointUrl.isEmpty()) {
            return false;
        }

        PlayerIdentity playerIdentity = capturePlayerIdentity();
        if (playerIdentity == null) {
            System.err.println("[HypixelClient] Bot event not sent: unable to read Minecraft session.");
            return false;
        }

        JsonObject data = new JsonObject();
        data.addProperty("playerName", fallback(playerName, "unknown"));
        data.addProperty("triggerMessage", fallback(triggerMessage, "Server shutdown detected."));
        queueEvent(settings, "server_shutdown", data, playerIdentity);
        return true;
    }

    private static void queueEvent(
            AlertSettings settings,
            String type,
            JsonObject data,
            PlayerIdentity playerIdentity
    ) {
        int eventId = EVENT_SEQUENCE.incrementAndGet();
        EVENT_EXECUTOR.execute(() -> sendEvent(eventId, settings, type, data, playerIdentity));
    }

    private static void sendEvent(
            int eventId,
            AlertSettings settings,
            String type,
            JsonObject data,
            PlayerIdentity playerIdentity
    ) {
        try {
            String bearerToken = resolveEventToken(eventId, settings.eventEndpointUrl, playerIdentity);
            if (bearerToken.isEmpty() && settings.sharedSecret.isEmpty()) {
                System.err.println("[HypixelClient] Bot event #" + eventId
                        + " not sent: Discord link auth is required.");
                return;
            }

            HttpResponse<String> response = sendEventRequest(
                    settings.eventEndpointUrl,
                    settings.sharedSecret,
                    bearerToken,
                    type,
                    data
            );

            if (response.statusCode() == 401 && !bearerToken.isEmpty()) {
                clearCachedAuthToken();
                String refreshedToken = resolveEventToken(eventId, settings.eventEndpointUrl, playerIdentity);
                if (!refreshedToken.isEmpty()) {
                    response = sendEventRequest(
                            settings.eventEndpointUrl,
                            settings.sharedSecret,
                            refreshedToken,
                            type,
                            data
                    );
                }
            }

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                System.err.println("[HypixelClient] Bot event #" + eventId
                        + " request failed: " + response.statusCode() + " " + response.body());
                return;
            }

            System.out.println("[HypixelClient] Bot event #" + eventId
                    + " delivered successfully as [" + type + "] with status " + response.statusCode() + ".");
        } catch (HttpTimeoutException error) {
            System.err.println("[HypixelClient] Bot event #" + eventId
                    + " request timed out after " + HTTP_REQUEST_TIMEOUT.toSeconds() + "s.");
        } catch (Exception error) {
            System.err.println("[HypixelClient] Bot event #" + eventId + " request error: " + error.getMessage());
        }
    }

    private static String fallback(String value, String fallback) {
        String clean = sanitize(value);
        return clean.isEmpty() ? fallback : clean;
    }

    private static AlertSettings loadAlertSettings(BooleanSupplier enabledSupplier) {
        boolean enabled = enabledSupplier.getAsBoolean();
        String serverUrl = sanitize(HypixelClientState.alertServerUrl);
        String sharedSecret = sanitize(HypixelClientState.alertServerSecret);

        if (!enabled || serverUrl.isEmpty()) {
            HypixelClientConfig.reloadAlertSettings();
            enabled = enabledSupplier.getAsBoolean();
            serverUrl = sanitize(HypixelClientState.alertServerUrl);
            sharedSecret = sanitize(HypixelClientState.alertServerSecret);
        }

        return new AlertSettings(enabled, resolveEventEndpoint(serverUrl), sharedSecret);
    }

    private static PlayerIdentity capturePlayerIdentity() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return null;
        }

        User user = minecraft.getUser();
        if (user == null || user.getProfileId() == null) {
            return null;
        }

        String ign = sanitize(user.getName());
        String accessToken = sanitize(user.getAccessToken());
        String minecraftUuid = user.getProfileId().toString();

        if (ign.isEmpty() || accessToken.isEmpty() || minecraftUuid.isEmpty()) {
            return null;
        }

        return new PlayerIdentity(ign, minecraftUuid, accessToken);
    }

    private static String resolveEventToken(int eventId, String eventEndpointUrl, PlayerIdentity playerIdentity) {
        String cachedToken = getCachedAuthToken(playerIdentity);
        if (!cachedToken.isEmpty()) {
            return cachedToken;
        }

        return requestEventToken(eventId, resolveAuthEndpoint(eventEndpointUrl), playerIdentity);
    }

    private static synchronized String getCachedAuthToken(PlayerIdentity playerIdentity) {
        String cachedToken = sanitize(HypixelClientState.alertAuthToken);
        String cachedMinecraftUuid = sanitize(HypixelClientState.alertAuthMinecraftUuid);

        if (cachedToken.isEmpty()) {
            return "";
        }

        if (!playerIdentity.minecraftUuid.equalsIgnoreCase(cachedMinecraftUuid)) {
            clearCachedAuthToken();
            return "";
        }

        return cachedToken;
    }

    private static String requestEventToken(
            int eventId,
            String authEndpointUrl,
            PlayerIdentity playerIdentity
    ) {
        if (authEndpointUrl.isEmpty()) {
            return "";
        }

        String serverId = UUID.randomUUID().toString().replace("-", "");

        try {
            SESSION_SERVICE.joinServer(
                    UUID.fromString(playerIdentity.minecraftUuid),
                    playerIdentity.accessToken,
                    serverId
            );

            JsonObject payload = new JsonObject();
            payload.addProperty("ign", playerIdentity.minecraftIgn);
            payload.addProperty("minecraftUuid", playerIdentity.minecraftUuid);
            payload.addProperty("serverId", serverId);

            for (int attempt = 1; attempt <= AUTH_REQUEST_RETRY_ATTEMPTS; attempt++) {
                HttpRequest request = HttpRequest.newBuilder(URI.create(authEndpointUrl))
                        .header("Content-Type", "application/json")
                        .timeout(HTTP_REQUEST_TIMEOUT)
                        .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(payload)))
                        .build();

                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    JsonObject responseJson = JsonParser.parseString(response.body()).getAsJsonObject();
                    String token = getJsonString(responseJson, "token");
                    String minecraftUuid = fallback(
                            getJsonString(responseJson, "minecraftUuid"),
                            playerIdentity.minecraftUuid
                    );

                    if (token.isEmpty()) {
                        System.err.println("[HypixelClient] Bot event #" + eventId
                                + " auth request failed: token missing from response.");
                        return "";
                    }

                    cacheAuthToken(minecraftUuid, token);
                    return token;
                }

                boolean shouldRetry = response.statusCode() == 401 && attempt < AUTH_REQUEST_RETRY_ATTEMPTS;
                if (!shouldRetry) {
                    System.err.println("[HypixelClient] Bot event #" + eventId
                            + " auth request failed: " + response.statusCode() + " " + response.body());
                    return "";
                }

                try {
                    Thread.sleep(AUTH_REQUEST_RETRY_DELAY_MS);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    System.err.println("[HypixelClient] Bot event #" + eventId + " auth retry interrupted.");
                    return "";
                }
            }

            return "";
        } catch (AuthenticationException error) {
            System.err.println("[HypixelClient] Bot event #" + eventId
                    + " auth session verification failed: " + error.getMessage());
            return "";
        } catch (HttpTimeoutException error) {
            System.err.println("[HypixelClient] Bot event #" + eventId
                    + " auth request timed out after " + HTTP_REQUEST_TIMEOUT.toSeconds() + "s.");
            return "";
        } catch (Exception error) {
            System.err.println("[HypixelClient] Bot event #" + eventId + " auth request error: " + error.getMessage());
            return "";
        }
    }

    private static HttpResponse<String> sendEventRequest(
            String endpointUrl,
            String sharedSecret,
            String bearerToken,
            String type,
            JsonObject data
    ) throws Exception {
        JsonObject payload = new JsonObject();
        payload.addProperty("type", fallback(type, "unknown"));
        if (bearerToken.isEmpty() && !sharedSecret.isEmpty()) {
            payload.addProperty("secret", sharedSecret);
        }
        payload.add("data", data);

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(endpointUrl))
                .header("Content-Type", "application/json")
                .timeout(HTTP_REQUEST_TIMEOUT);

        if (!bearerToken.isEmpty()) {
            requestBuilder.header("Authorization", "Bearer " + bearerToken);
        }

        HttpRequest request = requestBuilder
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(payload)))
                .build();

        return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static synchronized void cacheAuthToken(String minecraftUuid, String token) {
        String cleanMinecraftUuid = sanitize(minecraftUuid);
        String cleanToken = sanitize(token);

        if (cleanMinecraftUuid.isEmpty() || cleanToken.isEmpty()) {
            return;
        }

        if (cleanToken.equals(HypixelClientState.alertAuthToken)
                && cleanMinecraftUuid.equalsIgnoreCase(HypixelClientState.alertAuthMinecraftUuid)) {
            return;
        }

        HypixelClientState.alertAuthToken = cleanToken;
        HypixelClientState.alertAuthMinecraftUuid = cleanMinecraftUuid;
        HypixelClientConfig.save();
    }

    private static synchronized void clearCachedAuthToken() {
        if (HypixelClientState.alertAuthToken.isEmpty()
                && HypixelClientState.alertAuthMinecraftUuid.isEmpty()) {
            return;
        }

        HypixelClientState.alertAuthToken = "";
        HypixelClientState.alertAuthMinecraftUuid = "";
        HypixelClientConfig.save();
    }

    private static String resolveEventEndpoint(String value) {
        String clean = sanitize(value);
        if (clean.isEmpty()) {
            return "";
        }

        String lower = clean.toLowerCase(Locale.ROOT);
        if (lower.endsWith("/webhook")) {
            return clean.substring(0, clean.length() - "/webhook".length()) + "/events";
        }

        if (lower.endsWith("/events")) {
            return clean;
        }

        try {
            URI uri = URI.create(clean);
            String path = uri.getPath();

            if (path == null || path.isBlank() || "/".equals(path)) {
                return clean.endsWith("/") ? clean + "events" : clean + "/events";
            }
        } catch (IllegalArgumentException ignored) {
            return clean;
        }

        return clean;
    }

    private static String resolveAuthEndpoint(String eventEndpointUrl) {
        String clean = sanitize(eventEndpointUrl);
        if (clean.isEmpty()) {
            return "";
        }

        String lower = clean.toLowerCase(Locale.ROOT);
        if (lower.endsWith("/events")) {
            return clean.substring(0, clean.length() - "/events".length()) + "/auth/session";
        }

        if (lower.endsWith("/auth/session")) {
            return clean;
        }

        try {
            URI uri = URI.create(clean);
            String path = uri.getPath();

            if (path == null || path.isBlank() || "/".equals(path)) {
                return clean.endsWith("/") ? clean + "auth/session" : clean + "/auth/session";
            }
        } catch (IllegalArgumentException ignored) {
            return clean;
        }

        return clean.endsWith("/") ? clean + "auth/session" : clean + "/auth/session";
    }

    private static String getJsonString(JsonObject jsonObject, String key) {
        if (jsonObject == null || !jsonObject.has(key) || jsonObject.get(key).isJsonNull()) {
            return "";
        }

        try {
            return sanitize(jsonObject.get(key).getAsString());
        } catch (UnsupportedOperationException | ClassCastException | IllegalStateException ignored) {
            return "";
        }
    }

    private static String sanitize(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class AlertSettings {
        private final boolean enabled;
        private final String eventEndpointUrl;
        private final String sharedSecret;

        private AlertSettings(boolean enabled, String eventEndpointUrl, String sharedSecret) {
            this.enabled = enabled;
            this.eventEndpointUrl = eventEndpointUrl;
            this.sharedSecret = sharedSecret;
        }
    }

    private static final class PlayerIdentity {
        private final String minecraftIgn;
        private final String minecraftUuid;
        private final String accessToken;

        private PlayerIdentity(String minecraftIgn, String minecraftUuid, String accessToken) {
            this.minecraftIgn = minecraftIgn;
            this.minecraftUuid = minecraftUuid;
            this.accessToken = accessToken;
        }
    }
}
