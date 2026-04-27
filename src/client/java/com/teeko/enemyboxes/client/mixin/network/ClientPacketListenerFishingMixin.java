package com.teeko.enemyboxes.client.mixin.network;

import com.teeko.enemyboxes.client.feature.fishing.AutoFisher;
import com.teeko.enemyboxes.client.feature.fishing.PacketLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Intercepts incoming packets for PacketLogger. Enable the logger from the Fishing tab
// to dump packets to logs/enemyboxes-packets.log and the console.
//
// NOTE: handleCustomPayload is defined on ClientCommonPacketListenerImpl (parent of
// ClientPacketListener). If custom payload logging doesn't fire, add a separate
// @Mixin(ClientCommonPacketListenerImpl.class) targeting that method.
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerFishingMixin {

    // Stop the auto-fisher on any respawn packet — this covers dimension changes,
    // server-side teleports that reload the world, etc. Mirrors how BeachballMacro
    // handles world changes via ClientPacketListenerBeachballMixin.
    @Inject(method = "handleRespawn", at = @At("HEAD"))
    private void enemyboxes$stopFishingOnRespawn(ClientboundRespawnPacket packet, CallbackInfo ci) {
        AutoFisher.forceStop(Minecraft.getInstance(), "World change detected.");
    }

    // Entity status events — vanilla fishing uses event id 31 on the FishingHook entity.
    // Custom servers may use different event ids.
    @Inject(method = "handleEntityEvent", at = @At("HEAD"))
    private void enemyboxes$logEntityEvent(ClientboundEntityEventPacket packet, CallbackInfo ci) {
        if (!PacketLogger.enabled) return;
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;
        net.minecraft.world.entity.Entity entity = packet.getEntity(client.level);
        int entityId = entity != null ? entity.getId() : -1;
        PacketLogger.log("EntityEvent",
                PacketLogger.describeEntity(entityId) + " eventId=" + packet.getEventId());
    }

    // Entity velocity — fires when the server pushes velocity onto an entity.
    // Filtered to the player's fishing hook only to reduce noise.
    // Injecting at RETURN so the velocity has already been applied to the entity,
    // letting us read it directly without needing the packet's private encoded fields.
    @Inject(method = "handleSetEntityMotion", at = @At("RETURN"), require = 0)
    private void enemyboxes$logEntityMotion(ClientboundSetEntityMotionPacket packet, CallbackInfo ci) {
        if (!PacketLogger.enabled) return;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.player.fishing == null) return;
        if (client.player.fishing.getId() != packet.getId()) return;
        double vy = client.player.fishing.getDeltaMovement().y;
        PacketLogger.log("EntityMotion",
                PacketLogger.describeEntity(packet.getId())
                        + " vy=" + String.format("%.4f", vy));
    }

    // Entity metadata — fires when the server updates synced entity data fields.
    // Filtered to the fishing hook only.
    @Inject(method = "handleSetEntityData", at = @At("HEAD"), require = 0)
    private void enemyboxes$logEntityData(ClientboundSetEntityDataPacket packet, CallbackInfo ci) {
        if (!PacketLogger.enabled) return;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.player.fishing == null) return;
        if (client.player.fishing.getId() != packet.id()) return;
        PacketLogger.log("EntityData",
                PacketLogger.describeEntity(packet.id()) + " items=" + packet.packedItems());
    }

    // Positional sound events — logs every sound. Useful if the server triggers a
    // specific sound on fish bite.
    @Inject(method = "handleSoundEvent", at = @At("HEAD"), require = 0)
    private void enemyboxes$logSound(ClientboundSoundPacket packet, CallbackInfo ci) {
        if (!PacketLogger.enabled) return;
        String name = packet.getSound().value().location().toString();
        // Position is stored fixed-point (* 8). Divide to recover block coordinates.
        PacketLogger.log("Sound",
                "name=" + name
                        + " x=" + String.format("%.1f", packet.getX() / 8.0)
                        + " y=" + String.format("%.1f", packet.getY() / 8.0)
                        + " z=" + String.format("%.1f", packet.getZ() / 8.0));
    }

    // Entity-attached sound events (sound plays at a specific entity's position).
    @Inject(method = "handleSoundEntityEvent", at = @At("HEAD"), require = 0)
    private void enemyboxes$logEntitySound(ClientboundSoundEntityPacket packet, CallbackInfo ci) {
        if (!PacketLogger.enabled) return;
        String name = packet.getSound().value().location().toString();
        PacketLogger.log("EntitySound",
                "name=" + name + " " + PacketLogger.describeEntity(packet.getId()));
    }

    // Text-based signals — action bar, title, subtitle. Some servers encode "reel in!"
    // style cues as display text rather than structured packets.
    @Inject(method = "setActionBarText", at = @At("HEAD"))
    private void enemyboxes$logActionBar(ClientboundSetActionBarTextPacket packet, CallbackInfo ci) {
        if (!PacketLogger.enabled) return;
        PacketLogger.log("ActionBar", "text=" + packet.text().getString());
    }

    @Inject(method = "setTitleText", at = @At("HEAD"))
    private void enemyboxes$logTitle(ClientboundSetTitleTextPacket packet, CallbackInfo ci) {
        if (!PacketLogger.enabled) return;
        PacketLogger.log("Title", "text=" + packet.text().getString());
    }

    @Inject(method = "setSubtitleText", at = @At("HEAD"))
    private void enemyboxes$logSubtitle(ClientboundSetSubtitleTextPacket packet, CallbackInfo ci) {
        if (!PacketLogger.enabled) return;
        PacketLogger.log("Subtitle", "text=" + packet.text().getString());
    }
}
