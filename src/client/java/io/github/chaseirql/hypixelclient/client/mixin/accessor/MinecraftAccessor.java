package io.github.chaseirql.hypixelclient.client.mixin.accessor;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {

    @Accessor("missTime")
    int hypixelclient$getMissTime();

    @Accessor("missTime")
    void hypixelclient$setMissTime(int missTime);

    @Invoker("startUseItem")
    void hypixelclient$invokeStartUseItem();

    @Invoker("startAttack")
    boolean hypixelclient$invokeStartAttack();
}
