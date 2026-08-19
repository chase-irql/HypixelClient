package io.github.chaseirql.hypixelclient.client.ui.screen;

import io.github.chaseirql.hypixelclient.client.combat.AutoClicker;
import io.github.chaseirql.hypixelclient.client.feature.fishing.AutoFisher;
import io.github.chaseirql.hypixelclient.client.feature.fishing.PacketLogger;
import io.github.chaseirql.hypixelclient.client.config.HypixelClientConfig;
import io.github.chaseirql.hypixelclient.client.feature.hideonleaf.HideonleafShardTracker;
import io.github.chaseirql.hypixelclient.client.integration.BotEventClient;
import io.github.chaseirql.hypixelclient.client.state.HypixelClientState;
import io.github.chaseirql.hypixelclient.client.ui.widget.TripleSlider;
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
import net.minecraft.world.item.ItemStack;

import java.util.Map;

/** Tabbed configuration screen for all HypixelClient client features. */
public final class HypixelClientScreen extends Screen {

    private final Screen parent;

    private enum Tab { ESP, AIMBOT, COMBAT, TARGETS, HIDEONLEAF, BEACHBALL, FISHING }
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

    // Smoothing is stored as float [0, 0.95]. We expose it to TripleSlider as
    // integer centipercent [0, 95] so we can reuse the existing int-based widget.
    private static final int SMOOTH_SCALE = 100; // multiply float by this for slider ints

    public HypixelClientScreen(Screen parent) {
        super(Component.literal("HypixelClient"));
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
        int   tabW = PANEL_W / tabs.length; // distributes evenly across all tabs
        for (int i = 0; i < tabs.length; i++) {
            final Tab tab = tabs[i];
            int tx = left + i * tabW;
            this.addRenderableWidget(
                    Button.builder(Component.literal(tabLabel(tab)), btn -> {
                        activeTab = tab;
                        rebuildMenu();
                    }).bounds(tx, top, tabW, TAB_H).build()
            );
        }

        int contentTop = top + TAB_H + MARGIN;

        switch (activeTab) {
            case ESP        -> buildEspTab(left, contentTop);
            case AIMBOT     -> buildAimbotTab(left, contentTop);
            case COMBAT     -> buildCombatTab(left, contentTop);
            case TARGETS    -> buildTargetsTab(left, contentTop);
            case HIDEONLEAF -> buildHideonleafTab(left, contentTop);
            case BEACHBALL  -> buildBeachballTab(left, contentTop);
            case FISHING    -> buildFishingTab(left, contentTop);
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
                Component.literal(HypixelClientState.enabled ? "ESP: ON" : "ESP: OFF"),
                btn -> {
                    HypixelClientState.enabled = !HypixelClientState.enabled;
                    btn.setMessage(Component.literal(HypixelClientState.enabled ? "ESP: ON" : "ESP: OFF"));
                }
        ).bounds(left, top, halfW, BTN_H).build());

        this.addRenderableWidget(Button.builder(
                Component.literal(HypixelClientState.showFovCircle ? "FOV Circle: ON" : "FOV Circle: OFF"),
                btn -> {
                    HypixelClientState.showFovCircle = !HypixelClientState.showFovCircle;
                    btn.setMessage(Component.literal(HypixelClientState.showFovCircle ? "FOV Circle: ON" : "FOV Circle: OFF"));
                }
        ).bounds(left + halfW + MARGIN, top, halfW, BTN_H).build());
        top += BTN_H + MARGIN;

        this.addRenderableWidget(new AbstractSliderButton(
                left, top, PANEL_W, BTN_H,
                Component.literal("FOV Size: " + (int) HypixelClientState.lockFov),
                (HypixelClientState.lockFov - 10f) / 290f
        ) {
            @Override protected void updateMessage() { setMessage(Component.literal("FOV Size: " + (int) HypixelClientState.lockFov)); }
            @Override protected void applyValue()    { HypixelClientState.lockFov = (float)(10 + this.value * 290); }
        });
        top += BTN_H + MARGIN;

        this.addRenderableWidget(Button.builder(
                Component.literal(HypixelClientState.showCps ? "CPS: ON" : "CPS: OFF"),
                btn -> {
                    HypixelClientState.showCps = !HypixelClientState.showCps;
                    btn.setMessage(Component.literal(HypixelClientState.showCps ? "CPS: ON" : "CPS: OFF"));
                }
        ).bounds(left, top, halfW, BTN_H).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("Position CPS"),
                btn -> Minecraft.getInstance().setScreen(new CpsDragScreen(this))
        ).bounds(left + halfW + MARGIN, top, halfW, BTN_H).build());
        top += BTN_H + MARGIN;

        // ── Snaplines ────────────────────────────────────────────────────────
        this.addRenderableWidget(Button.builder(
                Component.literal(HypixelClientState.snaplinesEnabled ? "Snaplines: ON" : "Snaplines: OFF"),
                btn -> {
                    HypixelClientState.snaplinesEnabled = !HypixelClientState.snaplinesEnabled;
                    btn.setMessage(Component.literal(HypixelClientState.snaplinesEnabled ? "Snaplines: ON" : "Snaplines: OFF"));
                }
        ).bounds(left, top, PANEL_W, BTN_H).build());
        top += BTN_H + MARGIN;

        this.addRenderableWidget(Button.builder(
                Component.literal(HypixelClientState.drawOffscreenEnemies ? "Draw Offscreen Enemies: ON" : "Draw Offscreen Enemies: OFF"),
                btn -> {
                    HypixelClientState.drawOffscreenEnemies = !HypixelClientState.drawOffscreenEnemies;
                    btn.setMessage(Component.literal(HypixelClientState.drawOffscreenEnemies
                            ? "Draw Offscreen Enemies: ON"
                            : "Draw Offscreen Enemies: OFF"));
                }
        ).bounds(left, top, PANEL_W, BTN_H).build());
        top += BTN_H + MARGIN;

        this.addRenderableWidget(Button.builder(
                Component.literal(HypixelClientState.snaplinesOnlyClosest ? "Show Only Closest: ON" : "Show Only Closest: OFF"),
                btn -> {
                    HypixelClientState.snaplinesOnlyClosest = !HypixelClientState.snaplinesOnlyClosest;
                    btn.setMessage(Component.literal(HypixelClientState.snaplinesOnlyClosest ? "Show Only Closest: ON" : "Show Only Closest: OFF"));
                }
        ).bounds(left, top, PANEL_W, BTN_H).build());
        top += BTN_H + MARGIN;

        this.addRenderableWidget(new AbstractSliderButton(
                left, top, PANEL_W, BTN_H,
                Component.literal("Line Thickness: " + HypixelClientState.snaplineThickness),
                (HypixelClientState.snaplineThickness - 1) / 4.0
        ) {
            @Override protected void updateMessage() { setMessage(Component.literal("Line Thickness: " + HypixelClientState.snaplineThickness)); }
            @Override protected void applyValue()    { HypixelClientState.snaplineThickness = 1 + (int) Math.round(this.value * 4); }
        });
        top += BTN_H + MARGIN;

        this.addRenderableWidget(Button.builder(
                Component.literal(HypixelClientState.showBoxDistance ? "Box Distance: ON" : "Box Distance: OFF"),
                btn -> {
                    HypixelClientState.showBoxDistance = !HypixelClientState.showBoxDistance;
                    btn.setMessage(Component.literal(HypixelClientState.showBoxDistance ? "Box Distance: ON" : "Box Distance: OFF"));
                }
        ).bounds(left, top, PANEL_W, BTN_H).build());
    }

    // -------------------------------------------------------------------------
    // Aimbot tab
    // -------------------------------------------------------------------------

    private void buildAimbotTab(int left, int top) {
        this.addRenderableWidget(Button.builder(
                Component.literal(HypixelClientState.aimbotEnabled ? "Aimbot: ON" : "Aimbot: OFF"),
                btn -> {
                    HypixelClientState.aimbotEnabled = !HypixelClientState.aimbotEnabled;
                    btn.setMessage(Component.literal(HypixelClientState.aimbotEnabled ? "Aimbot: ON" : "Aimbot: OFF"));
                }
        ).bounds(left, top, PANEL_W, BTN_H).build());
        top += BTN_H + MARGIN;

        // Smoothing triple slider — range [0, 95] maps to smoothing [0.00, 0.95]
        // The TripleSlider label already appends " ms", so we override with a
        // subclass that shows decimal values instead.
        this.addRenderableWidget(new SmoothingTripleSlider(left, top, PANEL_W, BTN_H));
        top += BTN_H + MARGIN;

        this.addRenderableWidget(new AbstractSliderButton(
                left, top, PANEL_W, BTN_H,
                Component.literal("Drift: " + fmt(HypixelClientState.driftStrength)),
                HypixelClientState.driftStrength / 3.0
        ) {
            @Override protected void updateMessage() { setMessage(Component.literal("Drift: " + fmt(HypixelClientState.driftStrength))); }
            @Override protected void applyValue()    { HypixelClientState.driftStrength = (float)(this.value * 3.0); }
        });
        top += BTN_H + MARGIN;

        this.addRenderableWidget(new AbstractSliderButton(
                left, top, PANEL_W, BTN_H,
                Component.literal("Jitter: " + fmt(HypixelClientState.jitterStrength)),
                HypixelClientState.jitterStrength / 2.0
        ) {
            @Override protected void updateMessage() { setMessage(Component.literal("Jitter: " + fmt(HypixelClientState.jitterStrength))); }
            @Override protected void applyValue()    { HypixelClientState.jitterStrength = (float)(this.value * 2.0); }
        });
        top += BTN_H + MARGIN;

        this.addRenderableWidget(new AbstractSliderButton(
                left, top, PANEL_W, BTN_H,
                Component.literal("Target Priority: " + fmt(HypixelClientState.aimPriorityBlend)),
                HypixelClientState.aimPriorityBlend
        ) {
            @Override protected void updateMessage() { setMessage(Component.literal("Target Priority: " + fmt(HypixelClientState.aimPriorityBlend))); }
            @Override protected void applyValue()    { HypixelClientState.aimPriorityBlend = (float) this.value; }
        });
    }

    // -------------------------------------------------------------------------
    // Combat tab
    // -------------------------------------------------------------------------

    private void buildCombatTab(int left, int top) {
        int halfW = (PANEL_W - MARGIN) / 2;

        // Auto Swing + LOS toggles
        this.addRenderableWidget(Button.builder(
                Component.literal(HypixelClientState.autoSwingEnabled ? "Auto Swing: ON" : "Auto Swing: OFF"),
                btn -> {
                    HypixelClientState.autoSwingEnabled = !HypixelClientState.autoSwingEnabled;
                    btn.setMessage(Component.literal(HypixelClientState.autoSwingEnabled ? "Auto Swing: ON" : "Auto Swing: OFF"));
                }
        ).bounds(left, top, halfW, BTN_H).build());

        this.addRenderableWidget(Button.builder(
                Component.literal(HypixelClientState.requireLineOfSight ? "Vis Check: ON" : "Vis Check: OFF"),
                btn -> {
                    HypixelClientState.requireLineOfSight = !HypixelClientState.requireLineOfSight;
                    btn.setMessage(Component.literal(HypixelClientState.requireLineOfSight ? "Vis Check: ON" : "Vis Check: OFF"));
                }
        ).bounds(left + halfW + MARGIN, top, halfW, BTN_H).build());
        top += BTN_H + MARGIN;

        // Swing delay triple slider
        this.addRenderableWidget(new TripleSlider(
                left, top, PANEL_W, BTN_H,
                "Swing",
                20, 500,
                HypixelClientState.swingDelayMin,
                HypixelClientState.swingDelayMode,
                HypixelClientState.swingDelayMax
        ) {
            @Override
            protected void onValueChanged() {
                HypixelClientState.swingDelayMin  = getMin();
                HypixelClientState.swingDelayMode = getMode();
                HypixelClientState.swingDelayMax  = getMax();
            }
        });
        top += BTN_H + MARGIN;

        // Reaction delay toggle
        this.addRenderableWidget(Button.builder(
                Component.literal(HypixelClientState.randomizeReactionDelay ? "Reaction Delay: ON" : "Reaction Delay: OFF"),
                btn -> {
                    HypixelClientState.randomizeReactionDelay = !HypixelClientState.randomizeReactionDelay;
                    btn.setMessage(Component.literal(HypixelClientState.randomizeReactionDelay ? "Reaction Delay: ON" : "Reaction Delay: OFF"));
                }
        ).bounds(left, top, PANEL_W, BTN_H).build());
        top += BTN_H + MARGIN;

        // Reaction delay triple slider
        this.addRenderableWidget(new TripleSlider(
                left, top, PANEL_W, BTN_H,
                "Reaction",
                0, 500,
                HypixelClientState.reactionDelayMin,
                HypixelClientState.reactionDelayMode,
                HypixelClientState.reactionDelayMax
        ) {
            @Override
            protected void onValueChanged() {
                HypixelClientState.reactionDelayMin  = getMin();
                HypixelClientState.reactionDelayMode = getMode();
                HypixelClientState.reactionDelayMax  = getMax();
            }
        });
        top += BTN_H + MARGIN * 3; // extra gap to visually separate sections

        // ── Auto Clicker ──────────────────────────────────────────────────────
        this.addRenderableWidget(Button.builder(
                Component.literal(HypixelClientState.autoClickerEnabled ? "Auto Clicker: ON" : "Auto Clicker: OFF"),
                btn -> {
                    HypixelClientState.autoClickerEnabled = !HypixelClientState.autoClickerEnabled;
                    if (!HypixelClientState.autoClickerEnabled) AutoClicker.resetState();
                    btn.setMessage(Component.literal(HypixelClientState.autoClickerEnabled ? "Auto Clicker: ON" : "Auto Clicker: OFF"));
                }
        ).bounds(left, top, PANEL_W, BTN_H).build());
        top += BTN_H + MARGIN;

        this.addRenderableWidget(new TripleSlider(
                left, top, PANEL_W, BTN_H,
                "Auto Click", "CPS",
                5, 20,
                HypixelClientState.acCpsMin,
                HypixelClientState.acCpsMode,
                HypixelClientState.acCpsMax
        ) {
            @Override
            protected void onValueChanged() {
                HypixelClientState.acCpsMin  = getMin();
                HypixelClientState.acCpsMode = getMode();
                HypixelClientState.acCpsMax  = getMax();
            }
        });
        top += BTN_H + MARGIN;

        this.addRenderableWidget(Button.builder(
                Component.literal(HypixelClientState.showClickGraph ? "Click Graph: ON" : "Click Graph: OFF"),
                btn -> {
                    HypixelClientState.showClickGraph = !HypixelClientState.showClickGraph;
                    btn.setMessage(Component.literal(HypixelClientState.showClickGraph ? "Click Graph: ON" : "Click Graph: OFF"));
                }
        ).bounds(left, top, PANEL_W, BTN_H).build());
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
    // Hideonleaf tab
    // -------------------------------------------------------------------------

    private void buildHideonleafTab(int left, int top) {
        int halfW = (PANEL_W - MARGIN) / 2;

        this.addRenderableWidget(Button.builder(
                Component.literal(HypixelClientState.autoHuntEnabled ? "Auto Hunt: ON" : "Auto Hunt: OFF"),
                btn -> {
                    HypixelClientState.autoHuntEnabled = !HypixelClientState.autoHuntEnabled;
                    btn.setMessage(Component.literal(HypixelClientState.autoHuntEnabled ? "Auto Hunt: ON" : "Auto Hunt: OFF"));
                }
        ).bounds(left, top, PANEL_W, BTN_H).build());
        top += BTN_H + MARGIN;

        // Shard tracker toggle — resets session on every toggle
        this.addRenderableWidget(Button.builder(
                Component.literal(HypixelClientState.shardTrackerEnabled ? "Shard Tracker: ON" : "Shard Tracker: OFF"),
                btn -> {
                    HypixelClientState.shardTrackerEnabled = !HypixelClientState.shardTrackerEnabled;
                    HideonleafShardTracker.resetSession();
                    btn.setMessage(Component.literal(HypixelClientState.shardTrackerEnabled ? "Shard Tracker: ON" : "Shard Tracker: OFF"));
                }
        ).bounds(left, top, halfW, BTN_H).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("Reset Session"),
                btn -> HideonleafShardTracker.resetSession()
        ).bounds(left + halfW + MARGIN, top, halfW, BTN_H).build());
    }

    // -------------------------------------------------------------------------
    // Beachball tab
    // -------------------------------------------------------------------------

    private void buildBeachballTab(int left, int top) {
        this.addRenderableWidget(Button.builder(
                Component.literal(HypixelClientState.beachballForcedStopAlertsEnabled ? "Stop Alerts: ON" : "Stop Alerts: OFF"),
                btn -> {
                    HypixelClientState.beachballForcedStopAlertsEnabled = !HypixelClientState.beachballForcedStopAlertsEnabled;
                    btn.setMessage(Component.literal(HypixelClientState.beachballForcedStopAlertsEnabled
                            ? "Stop Alerts: ON"
                            : "Stop Alerts: OFF"));
                }
        ).bounds(left, top, PANEL_W, BTN_H).build());
        top += BTN_H + MARGIN;

        this.addRenderableWidget(Button.builder(
                Component.literal(HypixelClientState.chatNameMentionAlertsEnabled ? "Name Mention Alerts: ON" : "Name Mention Alerts: OFF"),
                btn -> {
                    HypixelClientState.chatNameMentionAlertsEnabled = !HypixelClientState.chatNameMentionAlertsEnabled;
                    btn.setMessage(Component.literal(HypixelClientState.chatNameMentionAlertsEnabled
                            ? "Name Mention Alerts: ON"
                            : "Name Mention Alerts: OFF"));
                }
        ).bounds(left, top, PANEL_W, BTN_H).build());
        top += BTN_H + MARGIN;

        this.addRenderableWidget(Button.builder(
                Component.literal(HypixelClientState.serverShutdownAlertsEnabled ? "Shutdown Ping: ON" : "Shutdown Ping: OFF"),
                btn -> {
                    HypixelClientState.serverShutdownAlertsEnabled = !HypixelClientState.serverShutdownAlertsEnabled;
                    btn.setMessage(Component.literal(HypixelClientState.serverShutdownAlertsEnabled
                            ? "Shutdown Ping: ON"
                            : "Shutdown Ping: OFF"));
                }
        ).bounds(left, top, PANEL_W, BTN_H).build());
        top += BTN_H + MARGIN;

        this.addRenderableWidget(Button.builder(
                Component.literal(HypixelClientState.beachballCrouchEnabled ? "Crouch: ON" : "Crouch: OFF"),
                btn -> {
                    HypixelClientState.beachballCrouchEnabled = !HypixelClientState.beachballCrouchEnabled;
                    btn.setMessage(Component.literal(HypixelClientState.beachballCrouchEnabled ? "Crouch: ON" : "Crouch: OFF"));
                }
        ).bounds(left, top, PANEL_W, BTN_H).build());
        top += BTN_H + MARGIN;

        this.addRenderableWidget(Button.builder(
                Component.literal("Send Test Alert"),
                btn -> {
                    HypixelClientState.beachballForcedStopAlertsEnabled = true;
                    HypixelClientConfig.save();

                    String playerName = this.minecraft != null && this.minecraft.player != null
                            ? this.minecraft.player.getName().getString()
                            : "";
                    String dimensionId = this.minecraft != null && this.minecraft.level != null
                            ? this.minecraft.level.dimension().toString()
                            : "";
                    boolean queued = BotEventClient.sendBeachballForcedStopEvent(
                            "Manual test alert triggered from HypixelClient settings.",
                            playerName,
                            dimensionId,
                            0,
                            "TEST"
                    );
                    if (this.minecraft != null && this.minecraft.player != null) {
                        this.minecraft.player.displayClientMessage(
                                Component.literal(queued
                                        ? "[HypixelClient] Test alert queued."
                                        : "[HypixelClient] Test alert not sent."),
                                false
                        );
                    }
                }
        ).bounds(left, top, PANEL_W, BTN_H).build());
    }

    // -------------------------------------------------------------------------
    // Fishing tab
    // -------------------------------------------------------------------------

    private void buildFishingTab(int left, int top) {
        this.addRenderableWidget(Button.builder(
                Component.literal(HypixelClientState.autoFisherEnabled ? "Auto Fish: ON" : "Auto Fish: OFF"),
                btn -> {
                    HypixelClientState.autoFisherEnabled = !HypixelClientState.autoFisherEnabled;
                    if (!HypixelClientState.autoFisherEnabled) AutoFisher.reset();
                    btn.setMessage(Component.literal(HypixelClientState.autoFisherEnabled ? "Auto Fish: ON" : "Auto Fish: OFF"));
                }
        ).bounds(left, top, PANEL_W, BTN_H).build());
        top += BTN_H + MARGIN;

        this.addRenderableWidget(Button.builder(
                Component.literal(PacketLogger.enabled ? "Packet Log: ON" : "Packet Log: OFF"),
                btn -> {
                    PacketLogger.enabled = !PacketLogger.enabled;
                    btn.setMessage(Component.literal(PacketLogger.enabled ? "Packet Log: ON" : "Packet Log: OFF"));
                }
        ).bounds(left, top, PANEL_W, BTN_H).build());
        top += BTN_H + MARGIN;

        // Weapon slot picker — arrow buttons cycle through hotbar slots 1-9,
        // showing the item name currently in that slot.
        int arrowW = 20;
        int labelW = PANEL_W - 2 * arrowW - 2 * MARGIN;
        this.addRenderableWidget(Button.builder(
                Component.literal("<"),
                btn -> {
                    HypixelClientState.fishingWeaponSlot = (HypixelClientState.fishingWeaponSlot + 8) % 9;
                    HypixelClientConfig.save();
                    rebuildMenu();
                }
        ).bounds(left, top, arrowW, BTN_H).build());
        this.addRenderableWidget(Button.builder(
                Component.literal(weaponSlotLabel()),
                btn -> {}
        ).bounds(left + arrowW + MARGIN, top, labelW, BTN_H).build());
        this.addRenderableWidget(Button.builder(
                Component.literal(">"),
                btn -> {
                    HypixelClientState.fishingWeaponSlot = (HypixelClientState.fishingWeaponSlot + 1) % 9;
                    HypixelClientConfig.save();
                    rebuildMenu();
                }
        ).bounds(left + arrowW + MARGIN + labelW + MARGIN, top, arrowW, BTN_H).build());
    }

    private String weaponSlotLabel() {
        int slot = HypixelClientState.fishingWeaponSlot;
        String label = "Slot " + (slot + 1);
        if (this.minecraft != null && this.minecraft.player != null) {
            ItemStack stack = this.minecraft.player.getInventory().getItem(slot);
            if (!stack.isEmpty()) {
                label += " (" + stack.getHoverName().getString() + ")";
            }
        }
        return "Weapon: " + label;
    }

    // -------------------------------------------------------------------------
    // Smoothing TripleSlider — shows decimal labels instead of " ms"
    // -------------------------------------------------------------------------

    private static final class SmoothingTripleSlider extends TripleSlider {

        // Range [0, 95] → smoothing [0.00, 0.95]
        SmoothingTripleSlider(int x, int y, int width, int height) {
            super(x, y, width, height,
                    "Smooth",
                    0, 99,
                    toInt(HypixelClientState.aimSmoothingMin),
                    toInt(HypixelClientState.aimSmoothingMode),
                    toInt(HypixelClientState.aimSmoothingMax));
        }

        private static int toInt(float f) {
            return Math.round(f * SMOOTH_SCALE);
        }

        @Override
        protected void onValueChanged() {
            HypixelClientState.aimSmoothingMin  = getMin()  / (float) SMOOTH_SCALE;
            HypixelClientState.aimSmoothingMode = getMode() / (float) SMOOTH_SCALE;
            HypixelClientState.aimSmoothingMax  = getMax()  / (float) SMOOTH_SCALE;
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String tabLabel(Tab tab) {
        String name = switch (tab) {
            case ESP -> "ESP";
            case AIMBOT -> "Aim";
            case COMBAT -> "Combat";
            case TARGETS -> "Targets";
            case HIDEONLEAF -> "HOL";
            case BEACHBALL -> "Ball";
            case FISHING   -> "Fish";
        };
        return activeTab == tab ? "§e§l" + name : name;
    }

    private void addCurrentEntry() {
        if (this.addBox == null) return;
        String val = this.addBox.getValue().trim();
        if (!val.isEmpty()) {
            HypixelClientState.addTarget(val);
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
        HypixelClientConfig.save();
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
            super(HypixelClientScreen.this.minecraft, listWidth, listHeight, y, ROW_H);
            this.setX(x);
            for (Map.Entry<String, Boolean> e : new java.util.ArrayList<>(HypixelClientState.targets.entrySet())) {
                this.addEntry(new Entry(e.getKey(), e.getValue()));
            }
        }

        @Override public int getRowWidth() { return PANEL_W - 8; }

        class Entry extends ObjectSelectionList.Entry<Entry> {

            private final String  name;
            private       boolean enabled;
            private final Button  toggleBtn;
            private final Button  removeBtn;

            Entry(String name, boolean enabled) {
                this.name   = name;
                this.enabled = enabled;

                this.toggleBtn = Button.builder(
                        Component.literal(enabled ? "§aON" : "§cOFF"),
                        btn -> {
                            HypixelClientState.toggleTarget(name);
                            this.enabled = HypixelClientState.targets.getOrDefault(name, false);
                            btn.setMessage(Component.literal(this.enabled ? "§aON" : "§cOFF"));
                        }
                ).size(28, ROW_H - 4).build();

                this.removeBtn = Button.builder(Component.literal("X"), btn -> {
                    HypixelClientState.removeTarget(name);
                    rebuildMenu();
                }).size(16, ROW_H - 4).build();
            }

            @Override
            public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovered, float tickDelta) {
                int entryX = this.getX();
                int entryY = this.getY();
                int entryH = this.getHeight();

                // Name — grey out if disabled
                int nameColor = enabled ? 0xFFFFFFFF : 0xFF888888;
                graphics.drawString(HypixelClientScreen.this.font, name,
                        entryX + 3, entryY + (entryH - 8) / 2, nameColor);

                // Remove button (rightmost)
                int removeX = entryX + this.getWidth() - 18;
                int btnY    = entryY + (entryH - (ROW_H - 4)) / 2;
                this.removeBtn.setX(removeX);
                this.removeBtn.setY(btnY);
                this.removeBtn.render(graphics, mouseX, mouseY, tickDelta);

                // Toggle button (left of remove)
                int toggleX = removeX - 30;
                this.toggleBtn.setX(toggleX);
                this.toggleBtn.setY(btnY);
                this.toggleBtn.render(graphics, mouseX, mouseY, tickDelta);
            }

            @Override
            public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
                if (this.toggleBtn.mouseClicked(event, bl)) return true;
                return this.removeBtn.mouseClicked(event, bl);
            }

            @Override
            public Component getNarration() {
                return Component.literal(name + " " + (enabled ? "enabled" : "disabled"));
            }
        }
    }
}
