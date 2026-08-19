package com.teeko.strata.client.mixin.network;

import com.teeko.strata.client.feature.beachball.BeachballMacro;
import com.teeko.strata.client.feature.chat.ChatMentionAlerts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerBeachballMixin {

    @Inject(method = "handleRespawn", at = @At("HEAD"))
    private void strata$stopBeachballOnRespawn(ClientboundRespawnPacket packet, CallbackInfo ci) {
        BeachballMacro.stopIfRunning(Minecraft.getInstance());
    }

    @Inject(method = "setActionBarText", at = @At("HEAD"))
    private void strata$captureBeachballActionBar(
            ClientboundSetActionBarTextPacket packet,
            CallbackInfo ci
    ) {
        BeachballMacro.onPacketTextReceived("actionbar", packet.text());
    }

    @Inject(method = "handleSystemChat", at = @At("HEAD"))
    private void strata$captureBeachballSystemChat(
            ClientboundSystemChatPacket packet,
            CallbackInfo ci
    ) {
        ChatMentionAlerts.onGameMessage(Minecraft.getInstance(), packet.content(), packet.overlay());
        BeachballMacro.onPacketTextReceived(packet.overlay() ? "system_overlay" : "system_chat", packet.content());
    }

    @Inject(method = "setTitleText", at = @At("HEAD"))
    private void strata$captureBeachballTitle(
            ClientboundSetTitleTextPacket packet,
            CallbackInfo ci
    ) {
        BeachballMacro.onPacketTextReceived("title", packet.text());
    }

    @Inject(method = "setSubtitleText", at = @At("HEAD"))
    private void strata$captureBeachballSubtitle(
            ClientboundSetSubtitleTextPacket packet,
            CallbackInfo ci
    ) {
        BeachballMacro.onPacketTextReceived("subtitle", packet.text());
    }
}
