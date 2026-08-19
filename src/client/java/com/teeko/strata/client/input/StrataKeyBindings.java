package com.teeko.strata.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import com.teeko.strata.client.StrataClient;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class StrataKeyBindings {

    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.tryParse(StrataClient.MOD_ID + ":controls"));

    public static final KeyMapping OPEN_MENU_KEY = register(
            "key.strata.open_menu",
            GLFW.GLFW_KEY_RIGHT_SHIFT
    );

    public static final KeyMapping LOCK_ON_KEY = register(
            "key.strata.lock_on",
            GLFW.GLFW_KEY_Z
    );

    public static final KeyMapping DEBUG_KEY = register(
            "key.strata.debug",
            GLFW.GLFW_KEY_F8
    );

    public static final KeyMapping TOGGLE_HUNT_KEY = register(
            "key.strata.toggle_auto_hunt",
            GLFW.GLFW_KEY_H
    );

    public static final KeyMapping TOGGLE_BEACHBALL_KEY = register(
            "key.strata.toggle_beachball",
            GLFW.GLFW_KEY_J
    );

    public static final KeyMapping TOGGLE_AUTOFISH_KEY = register(
            "key.strata.toggle_autofish",
            GLFW.GLFW_KEY_K
    );

    private StrataKeyBindings() {}

    public static void init() {
        // Static initialization registers all keybindings.
    }

    private static KeyMapping register(String translationKey, int keyCode) {
        return KeyBindingHelper.registerKeyBinding(new KeyMapping(
                translationKey,
                InputConstants.Type.KEYSYM,
                keyCode,
                CATEGORY
        ));
    }
}
