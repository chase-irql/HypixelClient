package com.teeko.enemyboxes.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HideonleafShardTracker {

    private static final long   BAZAAR_REFRESH_MS = 15 * 60 * 1_000L;
    private static final String BAZAAR_URL =
            "https://api.hypixel.net/skyblock/bazaar";

    // "You caught a Hideonleaf Shard!"     → +1
    // "You caught x2 Hideonleaf Shards!"   → +N
    private static final Pattern SINGLE = Pattern.compile(
            "You caught a Hideonleaf Shard!");
    private static final Pattern MULTI  = Pattern.compile(
            "You caught x(\\d+) Hideonleaf Shards?!");

    // Session counters — only updated on game thread
    public static long sessionStartTime = System.currentTimeMillis();
    public static int  totalShards      = 0;

    // Bazaar price — updated from async HTTP thread, so volatile
    public static volatile double  sellPrice          = 0.0;
    public static volatile boolean priceLoaded        = false;
    public static          long    lastBazaarFetchTime = 0L;

    // Computed display values — updated on game thread before display
    public static double sessionCoins = 0.0;
    public static double coinsPerHour = 0.0;

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private HideonleafShardTracker() {}

    // -------------------------------------------------------------------------

    public static void resetSession() {
        sessionStartTime = System.currentTimeMillis();
        totalShards      = 0;
        sessionCoins     = 0.0;
        coinsPerHour     = 0.0;
        // Intentionally keep cached sellPrice / priceLoaded — no reason to
        // discard a valid Bazaar price just because the session resets.
    }

    /** Called from the Fabric GAME chat event on the game thread. */
    public static void onChatMessage(Component message) {
        if (!EnemyBoxesState.shardTrackerEnabled) return;

        String text   = stripFormatting(message.getString());
        int    shards = parseShards(text);
        if (shards <= 0) return;

        totalShards += shards;
        updateComputed();
        maybeRefreshBazaar();
    }

    /** Called each client tick to keep the Bazaar price fresh. */
    public static void tickRefresh() {
        maybeRefreshBazaar();
    }

    /** Recomputes sessionCoins / coinsPerHour from current state.
     *  Must be called on the game/render thread. */
    public static void updateComputed() {
        double price = sellPrice; // single volatile read
        sessionCoins = totalShards * price;
        double elapsedHours = Math.max(
                (System.currentTimeMillis() - sessionStartTime) / 3_600_000.0,
                1.0e-9
        );
        coinsPerHour = sessionCoins / elapsedHours;
    }

    // ── Bazaar fetch ──────────────────────────────────────────────────────────

    private static void maybeRefreshBazaar() {
        long now = System.currentTimeMillis();
        if (priceLoaded && now - lastBazaarFetchTime < BAZAAR_REFRESH_MS) return;

        // Stamp the time before firing so concurrent ticks don't stack requests.
        lastBazaarFetchTime = now;

        HTTP.sendAsync(
                HttpRequest.newBuilder(URI.create(BAZAAR_URL)).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        ).thenAccept(resp -> {
            if (resp.statusCode() == 200) parseBazaarPrice(resp.body());
        }).exceptionally(ex -> null); // swallow — keep previous price on failure
    }

    private static void parseBazaarPrice(String json) {
        try {
            JsonObject root  = JsonParser.parseString(json).getAsJsonObject();
            JsonObject shard = root.getAsJsonObject("products")
                                   .getAsJsonObject("SHARD_HIDEONLEAF");
            double price = shard.getAsJsonObject("quick_status")
                               .get("sellPrice").getAsDouble();
            sellPrice   = price;  // volatile write — visible to game thread
            priceLoaded = true;
        } catch (Exception ignored) {
            // Keep previous cached price on any parse or network error.
        }
    }

    // ── Parsing ───────────────────────────────────────────────────────────────

    private static int parseShards(String text) {
        if (SINGLE.matcher(text).find()) return 1;
        Matcher m = MULTI.matcher(text);
        if (m.find()) {
            try { return Integer.parseInt(m.group(1)); }
            catch (NumberFormatException ignored) {}
        }
        return 0;
    }

    // ── Display formatting helpers ────────────────────────────────────────────

    public static String fmtPrice(double v) {
        return String.format(Locale.US, "%,.1f", v);
    }

    public static String fmtCoins(double v) {
        return String.format(Locale.US, "%,.0f", v);
    }

    private static String stripFormatting(String s) {
        return s == null ? "" : s.replaceAll("§.", "");
    }
}
