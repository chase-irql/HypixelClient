package io.github.chaseirql.hypixelclient.client;

import io.github.chaseirql.hypixelclient.client.config.HypixelClientConfig;
import io.github.chaseirql.hypixelclient.client.debug.DebugDump;
import io.github.chaseirql.hypixelclient.client.feature.beachball.BeachballMacro;
import io.github.chaseirql.hypixelclient.client.feature.fishing.AutoFisher;
import io.github.chaseirql.hypixelclient.client.feature.fishing.FishingCombat;
import io.github.chaseirql.hypixelclient.client.feature.chat.ChatMentionAlerts;
import io.github.chaseirql.hypixelclient.client.feature.chat.ServerShutdownAlerts;
import io.github.chaseirql.hypixelclient.client.feature.hideonleaf.HideonleafHunt;
import io.github.chaseirql.hypixelclient.client.feature.hideonleaf.HideonleafShardTracker;
import io.github.chaseirql.hypixelclient.client.feature.lockon.LockOnController;
import io.github.chaseirql.hypixelclient.client.input.HypixelClientKeyBindings;
import io.github.chaseirql.hypixelclient.client.render.hud.HypixelClientHud;
import io.github.chaseirql.hypixelclient.client.state.HypixelClientState;
import io.github.chaseirql.hypixelclient.client.ui.screen.HypixelClientScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Fabric client entry point. Registers key bindings, lifecycle callbacks, chat listeners,
 * rendering hooks, and the per-tick feature coordinators.
 */
public final class HypixelClient implements ClientModInitializer {
    public static final String MOD_ID = "hypixelclient";

    @Override
    public void onInitializeClient() {
        HypixelClientKeyBindings.init();
        HypixelClientConfig.load();

        HudRenderCallback.EVENT.register((guiGraphics, deltaTracker) ->
                HypixelClientHud.render(guiGraphics, deltaTracker.getGameTimeDeltaPartialTick(false)));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            BeachballMacro.stopIfRunning(client);
            AutoFisher.forceStop(client, "Disconnected from server.");
        });

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) {
                HideonleafShardTracker.onChatMessage(message);
            }
            ChatMentionAlerts.onGameMessage(Minecraft.getInstance(), message, overlay);
            ServerShutdownAlerts.onGameMessage(Minecraft.getInstance(), message, overlay);
            BeachballMacro.onGameMessage(message, overlay);
        });

        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) ->
                ChatMentionAlerts.onChatMessage(Minecraft.getInstance(), message, sender));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (HypixelClientKeyBindings.OPEN_MENU_KEY.consumeClick()) {
                client.setScreen(new HypixelClientScreen(client.screen));
            }

            while (HypixelClientKeyBindings.DEBUG_KEY.consumeClick()) {
                DebugDump.dumpNearbyEntities(client);
            }

            while (HypixelClientKeyBindings.TOGGLE_HUNT_KEY.consumeClick()) {
                toggleAutoHunt(client);
            }

            while (HypixelClientKeyBindings.TOGGLE_BEACHBALL_KEY.consumeClick()) {
                toggleBeachball(client);
            }

            while (HypixelClientKeyBindings.TOGGLE_AUTOFISH_KEY.consumeClick()) {
                toggleAutoFisher(client);
            }

            if (!HypixelClientKeyBindings.LOCK_ON_KEY.isDown()) {
                LockOnController.clearLock();
            } else {
                LockOnController.updateLockOn(client);
            }

            HideonleafHunt.tick(client);
            BeachballMacro.tick(client);
            AutoFisher.tick(client);
            ChatMentionAlerts.tick(client);
            LockOnController.tickAutoHuntUse(client);

            if (HypixelClientState.shardTrackerEnabled) {
                HideonleafShardTracker.tickRefresh();
            }
        });
    }

    public static boolean isPlayerActive(Minecraft client) {
        return !client.isPaused() && client.screen == null;
    }

    public static void notifyTargetSaved() {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.displayClientMessage(Component.literal(
                    "[HypixelClient] Tracking " + HypixelClientState.targets.size() + " target(s)"
            ), false);
        }
    }

    private static void toggleAutoHunt(Minecraft client) {
        HypixelClientState.autoHuntEnabled = !HypixelClientState.autoHuntEnabled;
        if (!HypixelClientState.autoHuntEnabled) {
            HideonleafHunt.resetState();
            LockOnController.resetHuntUseState();
        }
        HypixelClientConfig.save();
        notifyAutoHuntToggled(client);
    }

    private static void notifyAutoHuntToggled(Minecraft client) {
        if (client.player != null) {
            client.player.displayClientMessage(Component.literal(
                    "[HypixelClient] Auto Hunt: " + (HypixelClientState.autoHuntEnabled ? "ON" : "OFF")
            ), false);
        }
    }

    private static void toggleBeachball(Minecraft client) {
        HypixelClientState.beachballMacroRunning = !HypixelClientState.beachballMacroRunning;
        BeachballMacro.onRunningStateChanged(client, HypixelClientState.beachballMacroRunning);
        notifyBeachballToggled(client);
    }

    private static void notifyBeachballToggled(Minecraft client) {
        if (client.player != null) {
            client.player.displayClientMessage(Component.literal(
                    "[HypixelClient] Beachball Macro: " + (HypixelClientState.beachballMacroRunning ? "ON" : "OFF")
            ), false);
        }
    }

    private static void toggleAutoFisher(Minecraft client) {
        HypixelClientState.autoFisherEnabled = !HypixelClientState.autoFisherEnabled;
        if (HypixelClientState.autoFisherEnabled) {
            // Capture origin block and look angles at the exact moment K is pressed.
            // These are locked in for the entire session — every return after combat
            // aims back at these same angles regardless of where the camera drifted.
            if (client.player != null) {
                AutoFisher.originBlock = client.player.getOnPos();
                AutoFisher.originYaw   = client.player.getYRot();
                AutoFisher.originPitch = client.player.getXRot();
            }
            // Immediately equip the rod so the first cast doesn't need a manual swap.
            FishingCombat.swapToRod(client);
        } else {
            // Manual disable — reset everything without sending a forced-stop alert.
            AutoFisher.reset();
        }
        HypixelClientConfig.save();
        if (client.player != null) {
            client.player.displayClientMessage(Component.literal(
                    "[HypixelClient] Auto Fish: " + (HypixelClientState.autoFisherEnabled ? "ON" : "OFF")
            ), false);
        }
    }
}
