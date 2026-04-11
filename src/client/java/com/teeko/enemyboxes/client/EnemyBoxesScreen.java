package com.teeko.enemyboxes.client;

import net.minecraft.client.Minecraft;
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

    private enum Tab { ESP, AIMBOT, COMBAT, TARGETS }
    private Tab activeTab = Tab.ESP;

    private EditBox addBox;
    private TargetList targetList;

    private static final int PANEL_W = 280;
    private static final int PANEL_H = 300;
    private static final int LIST_H  = 120;
    private static final int ROW_H   = 18;
    private static final int BTN_H   = 20;
    private static final int MARGIN  = 4;
    private static final int TAB_H   = 22;
    private static final int TAB_W   = PANEL_W / 4;

    public EnemyBoxesScreen(Screen parent) {
        super(Component.literal("EnemyBoxes"));
        this.parent = parent;
    }

    private int panelLeft() { return this.width  / 2 - PANEL_W / 2; }
    private int panelTop()  { return this.height / 2 - PANEL_H / 2; }

    @Override
    protected void init() {
        rebuildMenu();
    }

    private void rebuildMenu() {
        this.clearWidgets();

        int left = panelLeft();
        int top  = panelTop();

        // ---- Tab bar --------------------------------------------------------
        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            final Tab tab = tabs[i];
            int tx = left + i * TAB_W;
            this.addRenderableWidget(
                    Button.builder(Component.literal(tabLabel(tab)), btn -> {
                        activeTab = tab;
                        rebuildMenu();
                    }).bounds(tx, top, TAB_W, TAB_H).build()
            );
        }

        int contentTop = top + TAB_H + MARGIN;

        switch (activeTab) {
            case ESP     -> buildEspTab(left, contentTop);
            case AIMBOT  -> buildAimbotTab(left, contentTop);
            case COMBAT  -> buildCombatTab(left, contentTop);
            case TARGETS -> buildTargetsTab(left, contentTop);
        }

        // ---- Close ----------------------------------------------------------
        int closeTop = top + PANEL_H - BTN_H;
        this.addRenderableWidget(
                Button.builder(Component.literal("Close"), btn -> onClose())
                        .bounds(left, closeTop, PANEL_W, BTN_H)
                        .build()
        );
    }

    // -------------------------------------------------------------------------
    // ESP tab
    // -------------------------------------------------------------------------

    private void buildEspTab(int left, int top) {
        int halfW = (PANEL_W - MARGIN) / 2;

        this.addRenderableWidget(Button.builder(
                Component.literal(EnemyBoxesState.enabled ? "ESP: ON" : "ESP: OFF"),
                btn -> {
                    EnemyBoxesState.enabled = !EnemyBoxesState.enabled;
                    btn.setMessage(Component.literal(EnemyBoxesState.enabled ? "ESP: ON" : "ESP: OFF"));
                }
        ).bounds(left, top, halfW, BTN_H).build());

        this.addRenderableWidget(Button.builder(
                Component.literal(EnemyBoxesState.showFovCircle ? "FOV Circle: ON" : "FOV Circle: OFF"),
                btn -> {
                    EnemyBoxesState.showFovCircle = !EnemyBoxesState.showFovCircle;
                    btn.setMessage(Component.literal(EnemyBoxesState.showFovCircle ? "FOV Circle: ON" : "FOV Circle: OFF"));
                }
        ).bounds(left + halfW + MARGIN, top, halfW, BTN_H).build());
        top += BTN_H + MARGIN;

        this.addRenderableWidget(new AbstractSliderButton(
                left, top, PANEL_W, BTN_H,
                Component.literal("FOV Size: " + (int) EnemyBoxesState.lockFov),
                (EnemyBoxesState.lockFov - 10f) / 290f
        ) {
            @Override protected void updateMessage() { setMessage(Component.literal("FOV Size: " + (int) EnemyBoxesState.lockFov)); }
            @Override protected void applyValue()    { EnemyBoxesState.lockFov = (float)(10 + this.value * 290); }
        });
        top += BTN_H + MARGIN; // <-- this was missing

        this.addRenderableWidget(Button.builder(
                Component.literal(EnemyBoxesState.showCps ? "CPS: ON" : "CPS: OFF"),
                btn -> {
                    EnemyBoxesState.showCps = !EnemyBoxesState.showCps;
                    btn.setMessage(Component.literal(EnemyBoxesState.showCps ? "CPS: ON" : "CPS: OFF"));
                }
        ).bounds(left, top, halfW, BTN_H).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("Position CPS"),
                btn -> Minecraft.getInstance().setScreen(new CpsDragScreen(this))
        ).bounds(left + halfW + MARGIN, top, halfW, BTN_H).build());
        top += BTN_H + MARGIN;
    }

    // -------------------------------------------------------------------------
    // Aimbot tab
    // -------------------------------------------------------------------------

    private void buildAimbotTab(int left, int top) {
        this.addRenderableWidget(Button.builder(
                Component.literal(EnemyBoxesState.aimbotEnabled ? "Aimbot: ON" : "Aimbot: OFF"),
                btn -> {
                    EnemyBoxesState.aimbotEnabled = !EnemyBoxesState.aimbotEnabled;
                    btn.setMessage(Component.literal(EnemyBoxesState.aimbotEnabled ? "Aimbot: ON" : "Aimbot: OFF"));
                }
        ).bounds(left, top, PANEL_W, BTN_H).build());
        top += BTN_H + MARGIN;

        this.addRenderableWidget(new AbstractSliderButton(
                left, top, PANEL_W, BTN_H,
                Component.literal("Smoothing: " + fmt(EnemyBoxesState.aimSmoothing)),
                EnemyBoxesState.aimSmoothing / 0.95
        ) {
            @Override protected void updateMessage() { setMessage(Component.literal("Smoothing: " + fmt(EnemyBoxesState.aimSmoothing))); }
            @Override protected void applyValue()    { EnemyBoxesState.aimSmoothing = (float)(this.value * 0.95); }
        });
        top += BTN_H + MARGIN;

        this.addRenderableWidget(new AbstractSliderButton(
                left, top, PANEL_W, BTN_H,
                Component.literal("Drift: " + fmt(EnemyBoxesState.driftStrength)),
                EnemyBoxesState.driftStrength / 3.0
        ) {
            @Override protected void updateMessage() { setMessage(Component.literal("Drift: " + fmt(EnemyBoxesState.driftStrength))); }
            @Override protected void applyValue()    { EnemyBoxesState.driftStrength = (float)(this.value * 3.0); }
        });
        top += BTN_H + MARGIN;

        this.addRenderableWidget(new AbstractSliderButton(
                left, top, PANEL_W, BTN_H,
                Component.literal("Jitter: " + fmt(EnemyBoxesState.jitterStrength)),
                EnemyBoxesState.jitterStrength / 2.0
        ) {
            @Override protected void updateMessage() { setMessage(Component.literal("Jitter: " + fmt(EnemyBoxesState.jitterStrength))); }
            @Override protected void applyValue()    { EnemyBoxesState.jitterStrength = (float)(this.value * 2.0); }
        });
    }

    // -------------------------------------------------------------------------
    // Combat tab
    // -------------------------------------------------------------------------

    private void buildCombatTab(int left, int top) {
        int halfW = (PANEL_W - MARGIN) / 2;

        // Auto Swing + LOS toggles
        this.addRenderableWidget(Button.builder(
                Component.literal(EnemyBoxesState.autoSwingEnabled ? "Auto Swing: ON" : "Auto Swing: OFF"),
                btn -> {
                    EnemyBoxesState.autoSwingEnabled = !EnemyBoxesState.autoSwingEnabled;
                    btn.setMessage(Component.literal(EnemyBoxesState.autoSwingEnabled ? "Auto Swing: ON" : "Auto Swing: OFF"));
                }
        ).bounds(left, top, halfW, BTN_H).build());

        this.addRenderableWidget(Button.builder(
                Component.literal(EnemyBoxesState.requireLineOfSight ? "Vis Check: ON" : "Vis Check: OFF"),
                btn -> {
                    EnemyBoxesState.requireLineOfSight = !EnemyBoxesState.requireLineOfSight;
                    btn.setMessage(Component.literal(EnemyBoxesState.requireLineOfSight ? "Vis Check: ON" : "Vis Check: OFF"));
                }
        ).bounds(left + halfW + MARGIN, top, halfW, BTN_H).build());
        top += BTN_H + MARGIN;

        // Swing delay triple slider
        this.addRenderableWidget(new TripleSlider(
                left, top, PANEL_W, BTN_H,
                "Swing",
                50, 200,
                EnemyBoxesState.swingDelayMin,
                EnemyBoxesState.swingDelayMode,
                EnemyBoxesState.swingDelayMax
        ) {
            @Override
            protected void onValueChanged() {
                EnemyBoxesState.swingDelayMin  = getMin();
                EnemyBoxesState.swingDelayMode = getMode();
                EnemyBoxesState.swingDelayMax  = getMax();
            }
        });
        top += BTN_H + MARGIN;

        // Reaction delay toggle
        this.addRenderableWidget(Button.builder(
                Component.literal(EnemyBoxesState.randomizeReactionDelay ? "Reaction Delay: ON" : "Reaction Delay: OFF"),
                btn -> {
                    EnemyBoxesState.randomizeReactionDelay = !EnemyBoxesState.randomizeReactionDelay;
                    btn.setMessage(Component.literal(EnemyBoxesState.randomizeReactionDelay ? "Reaction Delay: ON" : "Reaction Delay: OFF"));
                }
        ).bounds(left, top, PANEL_W, BTN_H).build());
        top += BTN_H + MARGIN;

        // Reaction delay triple slider
        this.addRenderableWidget(new TripleSlider(
                left, top, PANEL_W, BTN_H,
                "Reaction",
                0, 500,
                EnemyBoxesState.reactionDelayMin,
                EnemyBoxesState.reactionDelayMode,
                EnemyBoxesState.reactionDelayMax
        ) {
            @Override
            protected void onValueChanged() {
                EnemyBoxesState.reactionDelayMin  = getMin();
                EnemyBoxesState.reactionDelayMode = getMode();
                EnemyBoxesState.reactionDelayMax  = getMax();
            }
        });
    }

    // -------------------------------------------------------------------------
    // Targets tab
    // -------------------------------------------------------------------------

    private void buildTargetsTab(int left, int top) {
        int right   = left + PANEL_W;
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

        this.targetList = new TargetList(left, top, PANEL_W, LIST_H);
        this.addRenderableWidget(this.targetList);

        this.setInitialFocus(this.addBox);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String tabLabel(Tab tab) {
        String name = tab.name().charAt(0) + tab.name().substring(1).toLowerCase();
        return activeTab == tab ? "§e§l" + name : name;
    }

    private void addCurrentEntry() {
        if (this.addBox == null) return;
        String val = this.addBox.getValue().trim();
        if (!val.isEmpty()) {
            EnemyBoxesState.targetNames.add(val);
            this.addBox.setValue("");
            rebuildMenu();
        }
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if (keyEvent.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
                && this.addBox != null && this.addBox.isFocused()) {
            addCurrentEntry();
            return true;
        }
        return super.keyPressed(keyEvent);
    }

    @Override
    public void onClose() {
        EnemyBoxesConfig.save();
        if (this.minecraft != null) this.minecraft.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private static String fmt(float v) { return String.format("%.2f", v); }

    // =========================================================================
    // Scrollable target list
    // =========================================================================

    private class TargetList extends ObjectSelectionList<TargetList.Entry> {

        TargetList(int x, int y, int listWidth, int listHeight) {
            super(EnemyBoxesScreen.this.minecraft, listWidth, listHeight, y, ROW_H);
            this.setX(x);
            for (String name : new ArrayList<>(EnemyBoxesState.targetNames)) {
                this.addEntry(new Entry(name));
            }
        }

        @Override public int getRowWidth() { return PANEL_W - 8; }

        class Entry extends ObjectSelectionList.Entry<Entry> {

            private final String name;
            private final Button removeBtn;

            Entry(String name) {
                this.name = name;
                this.removeBtn = Button.builder(Component.literal("X"), btn -> {
                    EnemyBoxesState.targetNames.remove(name);
                    rebuildMenu();
                }).size(16, ROW_H - 4).build();
            }

            @Override
            public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovered, float tickDelta) {
                int entryX = this.getX();
                int entryY = this.getY();
                int entryH = this.getHeight();

                graphics.drawString(EnemyBoxesScreen.this.font, name,
                        entryX + 3, entryY + (entryH - 8) / 2, 0xFFFFFFFF);

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
            public Component getNarration() { return Component.literal(name); }
        }
    }
}