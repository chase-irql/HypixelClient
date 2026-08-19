package io.github.chaseirql.hypixelclient.client.feature.fishing;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public final class PacketLogger {

    public static volatile boolean enabled = false;

    private static final Path LOG_PATH = FabricLoader.getInstance()
            .getGameDir()
            .resolve("logs")
            .resolve("hypixelclient-packets.log");

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private PacketLogger() {}

    public static void log(String type, String details) {
        if (!enabled) return;
        String line = "[" + LocalTime.now().format(TIME_FMT) + "] " + type + " | " + details;
        System.out.println("[HypixelClient/PacketLog] " + line);
        appendToFile(line);
    }

    // Resolves the entity from the client world and labels it if it is the player's fishing hook.
    public static String describeEntity(int entityId) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return "id=" + entityId;

        Entity entity = client.level.getEntity(entityId);
        String typeName = entity != null ? entity.getType().toShortString() : "unknown";

        boolean isHook = client.player != null
                && client.player.fishing != null
                && client.player.fishing.getId() == entityId;

        return "entityId=" + entityId + " type=" + typeName + (isHook ? " <<FISHING_HOOK>>" : "");
    }

    private static void appendToFile(String line) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(LOG_PATH.toFile(), true))) {
            pw.println(line);
        } catch (IOException ignored) {
        }
    }
}
