package com.teeko.strata.client;

import com.teeko.strata.client.config.StrataConfig;
import com.teeko.strata.client.debug.DebugDump;
import com.teeko.strata.client.feature.beachball.BeachballMacro;
import com.teeko.strata.client.feature.fishing.AutoFisher;
import com.teeko.strata.client.feature.fishing.FishingCombat;
import com.teeko.strata.client.feature.chat.ChatMentionAlerts;
import com.teeko.strata.client.feature.chat.ServerShutdownAlerts;
import com.teeko.strata.client.feature.hideonleaf.HideonleafHunt;
import com.teeko.strata.client.feature.hideonleaf.HideonleafShardTracker;
import com.teeko.strata.client.feature.lockon.LockOnController;
import com.teeko.strata.client.input.StrataKeyBindings;
import com.teeko.strata.client.render.hud.StrataHud;
import com.teeko.strata.client.state.StrataState;
import com.teeko.strata.client.ui.screen.StrataScreen;
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
public final class StrataClient implements ClientModInitializer {
    public static final String MOD_ID = "strata";

    @Override
    public void onInitializeClient() {
        StrataKeyBindings.init();
        StrataConfig.load();

        HudRenderCallback.EVENT.register((guiGraphics, deltaTracker) ->
                StrataHud.render(guiGraphics, deltaTracker.getGameTimeDeltaPartialTick(false)));

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
            while (StrataKeyBindings.OPEN_MENU_KEY.consumeClick()) {
                client.setScreen(new StrataScreen(client.screen));
            }

            while (StrataKeyBindings.DEBUG_KEY.consumeClick()) {
                DebugDump.dumpNearbyEntities(client);
            }

            while (StrataKeyBindings.TOGGLE_HUNT_KEY.consumeClick()) {
                toggleAutoHunt(client);
            }

            while (StrataKeyBindings.TOGGLE_BEACHBALL_KEY.consumeClick()) {
                toggleBeachball(client);
            }

            while (StrataKeyBindings.TOGGLE_AUTOFISH_KEY.consumeClick()) {
                toggleAutoFisher(client);
            }

            if (!StrataKeyBindings.LOCK_ON_KEY.isDown()) {
                LockOnController.clearLock();
            } else {
                LockOnController.updateLockOn(client);
            }

            HideonleafHunt.tick(client);
            BeachballMacro.tick(client);
            AutoFisher.tick(client);
            ChatMentionAlerts.tick(client);
            LockOnController.tickAutoHuntUse(client);

            if (StrataState.shardTrackerEnabled) {
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
                    "[Strata] Tracking " + StrataState.targets.size() + " target(s)"
            ), false);
        }
    }

    private static void toggleAutoHunt(Minecraft client) {
        StrataState.autoHuntEnabled = !StrataState.autoHuntEnabled;
        if (!StrataState.autoHuntEnabled) {
            HideonleafHunt.resetState();
            LockOnController.resetHuntUseState();
        }
        StrataConfig.save();
        notifyAutoHuntToggled(client);
    }

    private static void notifyAutoHuntToggled(Minecraft client) {
        if (client.player != null) {
            client.player.displayClientMessage(Component.literal(
                    "[Strata] Auto Hunt: " + (StrataState.autoHuntEnabled ? "ON" : "OFF")
            ), false);
        }
    }

    private static void toggleBeachball(Minecraft client) {
        StrataState.beachballMacroRunning = !StrataState.beachballMacroRunning;
        BeachballMacro.onRunningStateChanged(client, StrataState.beachballMacroRunning);
        notifyBeachballToggled(client);
    }

    private static void notifyBeachballToggled(Minecraft client) {
        if (client.player != null) {
            client.player.displayClientMessage(Component.literal(
                    "[Strata] Beachball Macro: " + (StrataState.beachballMacroRunning ? "ON" : "OFF")
            ), false);
        }
    }

    private static void toggleAutoFisher(Minecraft client) {
        StrataState.autoFisherEnabled = !StrataState.autoFisherEnabled;
        if (StrataState.autoFisherEnabled) {
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
        StrataConfig.save();
        if (client.player != null) {
            client.player.displayClientMessage(Component.literal(
                    "[Strata] Auto Fish: " + (StrataState.autoFisherEnabled ? "ON" : "OFF")
            ), false);
        }
    }
}
