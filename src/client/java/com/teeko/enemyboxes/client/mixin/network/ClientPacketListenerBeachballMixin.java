package com.teeko.enemyboxes.client.mixin.network;

import com.teeko.enemyboxes.client.feature.beachball.BeachballMacro;
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
    private void enemyboxes$stopBeachballOnRespawn(ClientboundRespawnPacket packet, CallbackInfo ci) {
        BeachballMacro.stopIfRunning(Minecraft.getInstance());
    }

    @Inject(method = "setActionBarText", at = @At("HEAD"))
    private void enemyboxes$captureBeachballActionBar(
            ClientboundSetActionBarTextPacket packet,
            CallbackInfo ci
    ) {
        BeachballMacro.onPacketTextReceived("actionbar", packet.text());
    }

    @Inject(method = "handleSystemChat", at = @At("HEAD"))
    private void enemyboxes$captureBeachballSystemChat(
            ClientboundSystemChatPacket packet,
            CallbackInfo ci
    ) {
        BeachballMacro.onPacketTextReceived(packet.overlay() ? "system_overlay" : "system_chat", packet.content());
    }

    @Inject(method = "setTitleText", at = @At("HEAD"))
    private void enemyboxes$captureBeachballTitle(
            ClientboundSetTitleTextPacket packet,
            CallbackInfo ci
    ) {
        BeachballMacro.onPacketTextReceived("title", packet.text());
    }

    @Inject(method = "setSubtitleText", at = @At("HEAD"))
    private void enemyboxes$captureBeachballSubtitle(
            ClientboundSetSubtitleTextPacket packet,
            CallbackInfo ci
    ) {
        BeachballMacro.onPacketTextReceived("subtitle", packet.text());
    }
}
