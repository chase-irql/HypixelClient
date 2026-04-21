package com.teeko.enemyboxes.client.mixin.accessor;

import net.minecraft.client.player.ClientInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientInput.class)
public interface ClientInputAccessor {

    @Accessor("keyPresses")
    void enemyboxes$setKeyPresses(Input input);

    @Accessor("moveVector")
    void enemyboxes$setMoveVector(Vec2 moveVector);
}
