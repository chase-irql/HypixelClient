package io.github.chaseirql.hypixelclient.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.chaseirql.hypixelclient.client.HypixelClient;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class HypixelClientKeyBindings {

    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.tryParse(HypixelClient.MOD_ID + ":controls"));

    public static final KeyMapping OPEN_MENU_KEY = register(
            "key.hypixelclient.open_menu",
            GLFW.GLFW_KEY_RIGHT_SHIFT
    );

    public static final KeyMapping LOCK_ON_KEY = register(
            "key.hypixelclient.lock_on",
            GLFW.GLFW_KEY_Z
    );

    public static final KeyMapping DEBUG_KEY = register(
            "key.hypixelclient.debug",
            GLFW.GLFW_KEY_F8
    );

    public static final KeyMapping TOGGLE_HUNT_KEY = register(
            "key.hypixelclient.toggle_auto_hunt",
            GLFW.GLFW_KEY_H
    );

    public static final KeyMapping TOGGLE_BEACHBALL_KEY = register(
            "key.hypixelclient.toggle_beachball",
            GLFW.GLFW_KEY_J
    );

    public static final KeyMapping TOGGLE_AUTOFISH_KEY = register(
            "key.hypixelclient.toggle_autofish",
            GLFW.GLFW_KEY_K
    );

    private HypixelClientKeyBindings() {}

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
