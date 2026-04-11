package com.teeko.enemyboxes.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;

public final class EnemyBoxesScreen extends Screen {

    private final Screen parent;
    private EditBox addBox;
    private TargetList targetList;

    private static final int PANEL_W = 260;
    private static final int LIST_H  = 100;
    private static final int ROW_H   = 18;
    private static final int BTN_H   = 20;
    private static final int MARGIN  = 4;

    public EnemyBoxesScreen(Screen parent) {
        super(Component.literal("EnemyBoxes"));
        this.parent = parent;
    }

    private int panelTop()  { return this.height / 2 - 170; }
    private int panelLeft() { return this.width  / 2 - PANEL_W / 2; }

    @Override
    protected void init() {
        int top   = panelTop();
        int left  = panelLeft();
        int right = left + PANEL_W;

        // ---- Add-target row -------------------------------------------------
        int addBtnW = 40;
        this.addBox = new EditBox(
                this.font,
                left, top, PANEL_W - addBtnW - MARGIN, BTN_H,
                Component.literal("Add target…")
        );
        this.addBox.setMaxLength(64);
        this.addBox.setHint(Component.literal("Enter name filter…"));
        this.addRenderableWidget(this.addBox);

        this.addRenderableWidget(
                Button.builder(Component.literal("Add"), btn -> addCurrentEntry())
                        .bounds(right - addBtnW, top, addBtnW, BTN_H)
                        .build()
        );
        top += BTN_H + MARGIN;

        // ---- Scrollable target list -----------------------------------------
        this.targetList = new TargetList(left, top, PANEL_W, LIST_H);
        this.addRenderableWidget(this.targetList);
        top += LIST_H + MARGIN;

        // ---- ESP | FOV circle | Aimbot toggles (three equal columns) --------
        int thirdW = (PANEL_W - MARGIN * 2) / 3;

        this.addRenderableWidget(
                Button.builder(
                        Component.literal(EnemyBoxesState.enabled ? "ESP: ON" : "ESP: OFF"),
                        btn -> {
                            EnemyBoxesState.enabled = !EnemyBoxesState.enabled;
                            btn.setMessage(Component.literal(EnemyBoxesState.enabled ? "ESP: ON" : "ESP: OFF"));
                        }
                ).bounds(left, top, thirdW, BTN_H).build()
        );

        this.addRenderableWidget(
                Button.builder(
                        Component.literal(EnemyBoxesState.showFovCircle ? "FOV Circle: ON" : "FOV Circle: OFF"),
                        btn -> {
                            EnemyBoxesState.showFovCircle = !EnemyBoxesState.showFovCircle;
                            btn.setMessage(Component.literal(EnemyBoxesState.showFovCircle ? "FOV Circle: ON" : "FOV Circle: OFF"));
                        }
                ).bounds(left + thirdW + MARGIN, top, thirdW, BTN_H).build()
        );

        this.addRenderableWidget(
                Button.builder(
                        Component.literal(EnemyBoxesState.aimbotEnabled ? "Aimbot: ON" : "Aimbot: OFF"),
                        btn -> {
                            EnemyBoxesState.aimbotEnabled = !EnemyBoxesState.aimbotEnabled;
                            btn.setMessage(Component.literal(EnemyBoxesState.aimbotEnabled ? "Aimbot: ON" : "Aimbot: OFF"));
                        }
                ).bounds(left + (thirdW + MARGIN) * 2, top, thirdW, BTN_H).build()
        );

        top += BTN_H + MARGIN;

        // ---- FOV size slider ------------------------------------------------
        this.addRenderableWidget(new AbstractSliderButton(
                left, top, PANEL_W, BTN_H,
                Component.literal("FOV Size: " + (int) EnemyBoxesState.lockFov),
                (EnemyBoxesState.lockFov - 10f) / 290f
        ) {
            @Override protected void updateMessage() { setMessage(Component.literal("FOV Size: " + (int) EnemyBoxesState.lockFov)); }
            @Override protected void applyValue()    { EnemyBoxesState.lockFov = (float)(10 + this.value * 290); }
        });
        top += BTN_H + MARGIN;

        // ---- Aim smoothing slider -------------------------------------------
        this.addRenderableWidget(new AbstractSliderButton(
                left, top, PANEL_W, BTN_H,
                Component.literal("Aim Smoothing: " + fmt(EnemyBoxesState.aimSmoothing)),
                EnemyBoxesState.aimSmoothing / 0.95
        ) {
            @Override protected void updateMessage() { setMessage(Component.literal("Aim Smoothing: " + fmt(EnemyBoxesState.aimSmoothing))); }
            @Override protected void applyValue()    { EnemyBoxesState.aimSmoothing = (float)(this.value * 0.95); }
        });
        top += BTN_H + MARGIN;

        // ---- Drift slider ---------------------------------------------------
        this.addRenderableWidget(new AbstractSliderButton(
                left, top, PANEL_W, BTN_H,
                Component.literal("Drift: " + fmt(EnemyBoxesState.driftStrength)),
                EnemyBoxesState.driftStrength / 3.0
        ) {
            @Override protected void updateMessage() { setMessage(Component.literal("Drift: " + fmt(EnemyBoxesState.driftStrength))); }
            @Override protected void applyValue()    { EnemyBoxesState.driftStrength = (float)(this.value * 3.0); }
        });
        top += BTN_H + MARGIN;

        // ---- Jitter slider --------------------------------------------------
        this.addRenderableWidget(new AbstractSliderButton(
                left, top, PANEL_W, BTN_H,
                Component.literal("Jitter: " + fmt(EnemyBoxesState.jitterStrength)),
                EnemyBoxesState.jitterStrength / 2.0
        ) {
            @Override protected void updateMessage() { setMessage(Component.literal("Jitter: " + fmt(EnemyBoxesState.jitterStrength))); }
            @Override protected void applyValue()    { EnemyBoxesState.jitterStrength = (float)(this.value * 2.0); }
        });
        top += BTN_H + MARGIN;

        // ---- Close button ---------------------------------------------------
        this.addRenderableWidget(
                Button.builder(Component.literal("Close"), btn -> this.onClose())
                        .bounds(left, top, PANEL_W, BTN_H)
                        .build()
        );

        this.setInitialFocus(this.addBox);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void addCurrentEntry() {
        String val = this.addBox.getValue().trim();
        if (!val.isEmpty()) {
            EnemyBoxesState.targetNames.add(val);
            this.addBox.setValue("");
            rebuildList();
        }
    }

    private void rebuildList() {
        this.removeWidget(this.targetList);
        int top = panelTop() + BTN_H + MARGIN;
        this.targetList = new TargetList(panelLeft(), top, PANEL_W, LIST_H);
        this.addRenderableWidget(this.targetList);
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if (keyEvent.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER && this.addBox.isFocused()) {
            addCurrentEntry();
            return true;
        }
        return super.keyPressed(keyEvent);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private static String fmt(float v) { return String.format("%.2f", v); }

    // =========================================================================
    // Scrollable list
    // =========================================================================

    private class TargetList extends ObjectSelectionList<TargetList.Entry> {

        TargetList(int x, int y, int listWidth, int listHeight) {
            super(EnemyBoxesScreen.this.minecraft, listWidth, listHeight, y, ROW_H);
            this.setX(x);
            for (String name : new ArrayList<>(EnemyBoxesState.targetNames)) {
                this.addEntry(new Entry(name));
            }
        }

        @Override
        public int getRowWidth() { return PANEL_W - 8; }

        class Entry extends ObjectSelectionList.Entry<Entry> {

            private final String name;
            private final Button removeBtn;

            Entry(String name) {
                this.name = name;
                this.removeBtn = Button.builder(Component.literal("X"), btn -> {
                    EnemyBoxesState.targetNames.remove(name);
                    rebuildList();
                }).size(16, ROW_H - 4).build();
            }

            @Override
            public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovered, float tickDelta) {
                int entryX = this.getX();
                int entryY = this.getY();
                int entryH = this.getHeight();

                graphics.drawString(
                        EnemyBoxesScreen.this.font,
                        name,
                        entryX + 3,
                        entryY + (entryH - 8) / 2,
                        0xFFFFFFFF
                );

                int btnX = entryX + this.getWidth() - 18;
                int btnY = entryY + (entryH - (ROW_H - 4)) / 2;
                this.removeBtn.setX(btnX);
                this.removeBtn.setY(btnY);
                this.removeBtn.render(graphics, mouseX, mouseY, tickDelta);
            }

            @Override
            public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
                return this.removeBtn.mouseClicked(event, bl);
            }

            @Override
            public Component getNarration() {
                return Component.literal(name);
            }
        }
    }
}