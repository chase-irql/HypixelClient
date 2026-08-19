package com.teeko.strata.client.mixin.accessor;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {

    @Accessor("missTime")
    int strata$getMissTime();

    @Accessor("missTime")
    void strata$setMissTime(int missTime);

    @Invoker("startUseItem")
    void strata$invokeStartUseItem();

    @Invoker("startAttack")
    boolean strata$invokeStartAttack();
}
