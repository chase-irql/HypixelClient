package com.teeko.enemyboxes.client.mixin.accessor;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {

    @Accessor("missTime")
    int enemyboxes$getMissTime();

    @Accessor("missTime")
    void enemyboxes$setMissTime(int missTime);

    @Invoker("startUseItem")
    void enemyboxes$invokeStartUseItem();

    @Invoker("startAttack")
    boolean enemyboxes$invokeStartAttack();
}
