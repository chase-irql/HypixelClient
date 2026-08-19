package com.teeko.strata.client.ui.screen;

import com.teeko.strata.client.config.StrataConfig;
import com.teeko.strata.client.render.hud.CpsWidget;
import com.teeko.strata.client.state.StrataState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class CpsDragScreen extends Screen {

    private final Screen parent;

    private boolean dragging  = false;
    private float   dragOffX  = 0;
    private float   dragOffY  = 0;

    private static final float SCALE_MIN  = 0.5f;
    private static final float SCALE_MAX  = 4.0f;
    private static final float SCALE_STEP = 0.1f;

    public CpsDragScreen(Screen parent) {
        super(Component.literal("Position CPS"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        // Done button bottom center
        this.addRenderableWidget(
                Button.builder(Component.literal("Done"), btn -> onClose())
                        .bounds(this.width / 2 - 50, this.height - 28, 100, 20)
                        .build()
        );
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        // Render world behind screen (no darken)
        Minecraft client = Minecraft.getInstance();

        // Instruction text
        graphics.drawString(client.font,
                "Drag to reposition  |  Scroll to resize  |  Done to save",
                4, 4, 0xFFAAAAAA, true);

        // CPS widget
        CpsWidget.render(graphics, client,
                StrataState.cpsX, StrataState.cpsY,
                StrataState.cpsScale, isHoveringWidget(mouseX, mouseY));

        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean bl) {
        if (event.buttonInfo().button() == 0 && isHoveringWidget((int)event.x(), (int)event.y())) {
            dragging = true;
            dragOffX = (float)event.x() - StrataState.cpsX;
            dragOffY = (float)event.y() - StrataState.cpsY;
            return true;
        }
        return super.mouseClicked(event, bl);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        if (event.buttonInfo().button() == 0) {
            dragging = false;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy) {
        if (dragging) {
            Minecraft client = Minecraft.getInstance();
            int w = CpsWidget.getWidth(client, StrataState.cpsScale);
            int h = CpsWidget.getHeight(StrataState.cpsScale);

            float newX = (float)event.x() - dragOffX;
            float newY = (float)event.y() - dragOffY;

            // Clamp to screen bounds
            newX = Math.max(0, Math.min(this.width  - w, newX));
            newY = Math.max(0, Math.min(this.height - h, newY));

            StrataState.cpsX = newX;
            StrataState.cpsY = newY;
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        float newScale = StrataState.cpsScale + (float)(scrollY * SCALE_STEP);
        StrataState.cpsScale = Math.max(SCALE_MIN, Math.min(SCALE_MAX, newScale));
        return true;
    }

    private boolean isHoveringWidget(int mouseX, int mouseY) {
        Minecraft client = Minecraft.getInstance();
        int w = CpsWidget.getWidth(client, StrataState.cpsScale);
        int h = CpsWidget.getHeight(StrataState.cpsScale);
        return mouseX >= StrataState.cpsX && mouseX <= StrataState.cpsX + w
                && mouseY >= StrataState.cpsY && mouseY <= StrataState.cpsY + h;
    }

    @Override
    public void onClose() {
        StrataConfig.save();
        if (this.minecraft != null) this.minecraft.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
