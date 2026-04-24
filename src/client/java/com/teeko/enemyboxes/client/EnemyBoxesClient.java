package com.teeko.enemyboxes.client;

import com.teeko.enemyboxes.client.config.EnemyBoxesConfig;
import com.teeko.enemyboxes.client.debug.DebugDump;
import com.teeko.enemyboxes.client.feature.beachball.BeachballMacro;
import com.teeko.enemyboxes.client.feature.chat.ChatMentionAlerts;
import com.teeko.enemyboxes.client.feature.chat.ServerShutdownAlerts;
import com.teeko.enemyboxes.client.feature.hideonleaf.HideonleafHunt;
import com.teeko.enemyboxes.client.feature.hideonleaf.HideonleafShardTracker;
import com.teeko.enemyboxes.client.feature.lockon.LockOnController;
import com.teeko.enemyboxes.client.input.EnemyBoxesKeyBindings;
import com.teeko.enemyboxes.client.render.hud.EnemyBoxesHud;
import com.teeko.enemyboxes.client.state.EnemyBoxesState;
import com.teeko.enemyboxes.client.ui.screen.EnemyBoxesScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class EnemyBoxesClient implements ClientModInitializer {
    public static final String MOD_ID = "enemyboxes";

    @Override
    public void onInitializeClient() {
        EnemyBoxesKeyBindings.init();
        EnemyBoxesConfig.load();

        HudRenderCallback.EVENT.register((guiGraphics, deltaTracker) ->
                EnemyBoxesHud.render(guiGraphics, deltaTracker.getGameTimeDeltaPartialTick(false)));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                BeachballMacro.stopIfRunning(client));

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
            while (EnemyBoxesKeyBindings.OPEN_MENU_KEY.consumeClick()) {
                client.setScreen(new EnemyBoxesScreen(client.screen));
            }

            while (EnemyBoxesKeyBindings.DEBUG_KEY.consumeClick()) {
                DebugDump.dumpNearbyEntities(client);
            }

            while (EnemyBoxesKeyBindings.TOGGLE_HUNT_KEY.consumeClick()) {
                toggleAutoHunt(client);
            }

            while (EnemyBoxesKeyBindings.TOGGLE_BEACHBALL_KEY.consumeClick()) {
                toggleBeachball(client);
            }

            if (!EnemyBoxesKeyBindings.LOCK_ON_KEY.isDown()) {
                LockOnController.clearLock();
            } else {
                LockOnController.updateLockOn(client);
            }

            HideonleafHunt.tick(client);
            BeachballMacro.tick(client);
            ChatMentionAlerts.tick(client);
            LockOnController.tickAutoHuntUse(client);

            if (EnemyBoxesState.shardTrackerEnabled) {
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
                    "[EnemyBoxes] Tracking " + EnemyBoxesState.targets.size() + " target(s)"
            ), false);
        }
    }

    private static void toggleAutoHunt(Minecraft client) {
        EnemyBoxesState.autoHuntEnabled = !EnemyBoxesState.autoHuntEnabled;
        if (!EnemyBoxesState.autoHuntEnabled) {
            HideonleafHunt.resetState();
            LockOnController.resetHuntUseState();
        }
        EnemyBoxesConfig.save();
        notifyAutoHuntToggled(client);
    }

    private static void notifyAutoHuntToggled(Minecraft client) {
        if (client.player != null) {
            client.player.displayClientMessage(Component.literal(
                    "[EnemyBoxes] Auto Hunt: " + (EnemyBoxesState.autoHuntEnabled ? "ON" : "OFF")
            ), false);
        }
    }

    private static void toggleBeachball(Minecraft client) {
        EnemyBoxesState.beachballMacroRunning = !EnemyBoxesState.beachballMacroRunning;
        BeachballMacro.onRunningStateChanged(client, EnemyBoxesState.beachballMacroRunning);
        notifyBeachballToggled(client);
    }

    private static void notifyBeachballToggled(Minecraft client) {
        if (client.player != null) {
            client.player.displayClientMessage(Component.literal(
                    "[EnemyBoxes] Beachball Macro: " + (EnemyBoxesState.beachballMacroRunning ? "ON" : "OFF")
            ), false);
        }
    }
}
