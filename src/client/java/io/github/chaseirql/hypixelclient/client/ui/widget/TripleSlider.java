package io.github.chaseirql.hypixelclient.client.ui.widget;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/**
 * Accessible three-handle slider used for minimum, most-likely, and maximum timing values.
 */
public class TripleSlider extends AbstractWidget {

    private static final Identifier SLIDER_SPRITE    = Identifier.withDefaultNamespace("widget/slider");
    private static final Identifier HANDLE_SPRITE    = Identifier.withDefaultNamespace("widget/slider_handle");
    private static final Identifier HANDLE_HL_SPRITE = Identifier.withDefaultNamespace("widget/slider_handle_highlighted");
    private static final int HANDLE_W = 8;

    public enum Handle { NONE, MIN, MODE, MAX }

    private final int rangeMin;
    private final int rangeMax;
    private final String label;
    private final String unit;

    private Handle dragging = Handle.NONE;

    private double normMin;
    private double normMode;
    private double normMax;

    /** Convenience constructor — defaults unit to "ms". */
    public TripleSlider(int x, int y, int width, int height,
                        String label,
                        int rangeMin, int rangeMax,
                        int initMin, int initMode, int initMax) {
        this(x, y, width, height, label, "ms", rangeMin, rangeMax, initMin, initMode, initMax);
    }

    public TripleSlider(int x, int y, int width, int height,
                        String label, String unit,
                        int rangeMin, int rangeMax,
                        int initMin, int initMode, int initMax) {
        super(x, y, width, height, Component.empty());
        this.label    = label;
        this.unit     = unit;
        this.rangeMin = rangeMin;
        this.rangeMax = rangeMax;
        this.normMin  = norm(initMin,  rangeMin, rangeMax);
        this.normMode = norm(initMode, rangeMin, rangeMax);
        this.normMax  = norm(initMax,  rangeMin, rangeMax);
        separateIfStacked(); // enforce minimum gaps on load
    }

    // -------------------------------------------------------------------------
    // Public value accessors
    // -------------------------------------------------------------------------

    public int getMin()  { return denorm(normMin);  }
    public int getMode() { return denorm(normMode); }
    public int getMax()  { return denorm(normMax);  }

    // -------------------------------------------------------------------------
    // Math helpers
    // -------------------------------------------------------------------------

    private static double norm(int val, int lo, int hi) {
        return Mth.clamp((double)(val - lo) / (hi - lo), 0.0, 1.0);
    }

    private int denorm(double n) {
        return (int) Math.round(rangeMin + n * (rangeMax - rangeMin));
    }

    private int handlePixelX(double norm) {
        return getX() + (int)(norm * (getWidth() - HANDLE_W));
    }

    private boolean isOverHandle(double mx, double my, double norm) {
        int hx = handlePixelX(norm);
        return mx >= hx && mx <= hx + HANDLE_W
                && my >= getY() && my <= getY() + getHeight();
    }

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float delta) {
        // Track
        g.blitSprite(RenderPipelines.GUI_TEXTURED, SLIDER_SPRITE,
                getX(), getY(), getWidth(), getHeight(), ARGB.white(this.alpha));

        // Handles
        drawHandle(g, normMin,  mouseX, mouseY, Handle.MIN);
        drawHandle(g, normMode, mouseX, mouseY, Handle.MODE);
        drawHandle(g, normMax,  mouseX, mouseY, Handle.MAX);

        // Label centered on bar
        var font = Minecraft.getInstance().font;
        String text = label + "  Min:" + getMin() + "  Mode:" + getMode() + "  Max:" + getMax() + " " + unit;
        int tx = getX() + getWidth() / 2 - font.width(text) / 2;
        int ty = getY() + (getHeight() - 8) / 2;
        g.drawString(font, text, tx, ty, 0xFFFFFFFF, true);

        if (isHovered()) {
            g.requestCursor(dragging != Handle.NONE ? CursorTypes.RESIZE_EW : CursorTypes.POINTING_HAND);
        }
    }

    private void drawHandle(GuiGraphics g, double norm, int mouseX, int mouseY, Handle h) {
        int hx = handlePixelX(norm);
        boolean hl = dragging == h
                || (dragging == Handle.NONE && isOverHandle(mouseX, mouseY, norm));
        Identifier sprite = hl ? HANDLE_HL_SPRITE : HANDLE_SPRITE;
        g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite,
                hx, getY(), HANDLE_W, getHeight(), ARGB.white(this.alpha));
    }

    // -------------------------------------------------------------------------
    // Input
    // -------------------------------------------------------------------------

    @Override
    public void onClick(MouseButtonEvent event, boolean bl) {
        double mx = event.x();
        double distMin  = Math.abs(mx - (handlePixelX(normMin)  + HANDLE_W / 2.0));
        double distMode = Math.abs(mx - (handlePixelX(normMode) + HANDLE_W / 2.0));
        double distMax  = Math.abs(mx - (handlePixelX(normMax)  + HANDLE_W / 2.0));

        if (distMin <= distMode && distMin <= distMax) {
            dragging = Handle.MIN;
        } else if (distMax <= distMode) {
            dragging = Handle.MAX;
        } else {
            dragging = Handle.MODE;
        }

        applyMouse(event);
    }

    @Override
    public void onRelease(MouseButtonEvent event) {
        dragging = Handle.NONE;
    }

    @Override
    public void onDrag(MouseButtonEvent event, double dragX, double dragY) {
        applyMouse(event);
        super.onDrag(event, dragX, dragY);
    }

    @Override
    public void mouseMoved(double mx, double my) {
        // handled via isOverHandle in renderWidget
    }

    private void applyMouse(MouseButtonEvent event) {
        double raw     = (event.x() - (getX() + HANDLE_W / 2.0)) / (getWidth() - HANDLE_W);
        double clamped = Mth.clamp(raw, 0.0, 1.0);

        switch (dragging) {
            case MIN  -> normMin  = clamped;
            case MODE -> normMode = clamped;
            case MAX  -> normMax  = clamped;
            default   -> {}
        }

        separateIfStacked();
        onValueChanged();
    }

    private void separateIfStacked() {
        double step = (rangeMax / 40.0f) / (rangeMax - rangeMin);

        // Enforce ordering based on which handle is moving
        if (dragging == Handle.MIN) {
            normMin  = Mth.clamp(normMin, 0.0, 1.0 - step * 2);
            normMode = Math.max(normMode, normMin + step);
            normMax  = Math.max(normMax,  normMode + step);
        } else if (dragging == Handle.MAX) {
            normMax  = Mth.clamp(normMax, step * 2, 1.0);
            normMode = Math.min(normMode, normMax - step);
            normMin  = Math.min(normMin,  normMode - step);
        } else if (dragging == Handle.MODE) {
            normMode = Mth.clamp(normMode, step, 1.0 - step);
            normMin  = Math.min(normMin,  normMode - step);
            normMax  = Math.max(normMax,  normMode + step);
        }

        // Final clamp to valid range
        normMin  = Mth.clamp(normMin,  0.0, 1.0);
        normMode = Mth.clamp(normMode, 0.0, 1.0);
        normMax  = Mth.clamp(normMax,  0.0, 1.0);
    }

    // Override this to receive updates
    protected void onValueChanged() {}

    // -------------------------------------------------------------------------
    // Narration
    // -------------------------------------------------------------------------

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        out.add(NarratedElementType.TITLE, Component.literal(
                label + " Min:" + getMin() + " Mode:" + getMode() + " Max:" + getMax() + " " + unit));
    }
}
