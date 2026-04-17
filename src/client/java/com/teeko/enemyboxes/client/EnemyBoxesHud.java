package com.teeko.enemyboxes.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.List;

public final class EnemyBoxesHud {

    private static final int SEGMENTS = 128;
    private static final int COLOR    = 0xFFFFFF00;

    public static void render(GuiGraphics graphics, float tickDelta) {
        Minecraft client = Minecraft.getInstance();

        // FOV circle
        if (EnemyBoxesState.enabled && EnemyBoxesState.showFovCircle && EnemyBoxesState.hasTarget()
                && client.level != null) {
            float cx     = client.getWindow().getGuiScaledWidth()  / 2f;
            float cy     = client.getWindow().getGuiScaledHeight() / 2f;
            float radius = EnemyBoxesState.lockFov;

            for (int i = 0; i < SEGMENTS; i++) {
                double a0 = 2 * Math.PI * i       / SEGMENTS;
                double a1 = 2 * Math.PI * (i + 1) / SEGMENTS;

                int x0 = (int)(cx + Math.cos(a0) * radius);
                int y0 = (int)(cy + Math.sin(a0) * radius);
                int x1 = (int)(cx + Math.cos(a1) * radius);
                int y1 = (int)(cy + Math.sin(a1) * radius);

                drawLine(graphics, x0, y0, x1, y1, COLOR);
            }
        }

        // Snaplines — draw when toggle is on OR when hunt forces them; each path
        // has its own entity collection logic inside renderSnaplines.
        if (client.level != null && client.player != null
                && (EnemyBoxesState.snaplinesEnabled || EnemyBoxesState.autoHuntEnabled)) {
            renderSnaplines(graphics, client, tickDelta);
        }

        // Box distance labels
        if (EnemyBoxesState.showBoxDistance && client.level != null && client.player != null
                && (EnemyBoxesState.enabled || EnemyBoxesState.autoHuntEnabled)) {
            renderBoxDistances(graphics, client, tickDelta);
        }

        // Hunt / shard tracker overlay (top-right corner)
        if ((EnemyBoxesState.autoHuntEnabled || EnemyBoxesState.shardTrackerEnabled)
                && client.level != null) {
            renderHuntDebug(graphics, client);
        }

        // CPS counter
        if (EnemyBoxesState.showCps) {
            renderCps(graphics, client, EnemyBoxesState.cpsX, EnemyBoxesState.cpsY, EnemyBoxesState.cpsScale, false);
        }

        // Auto-clicker activity graph
        if (EnemyBoxesState.showClickGraph) {
            renderClickGraph(graphics, client);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Snaplines
    // ─────────────────────────────────────────────────────────────────────────

    private static void renderSnaplines(GuiGraphics graphics, Minecraft client, float tickDelta) {
        if (client.gameRenderer == null) return;

        Camera camera = client.gameRenderer.getMainCamera();
        float cx = client.getWindow().getGuiScaledWidth()  / 2f;
        float cy = client.getWindow().getGuiScaledHeight() / 2f;

        Vec3 camPos = camera.position();
        Vector3fc forwardVec = camera.forwardVector();
        Vector3fc upVec      = camera.upVector();
        Vector3fc leftVec    = camera.leftVector();
        Vec3 camForward      = new Vec3(forwardVec.x(), forwardVec.y(), forwardVec.z());
        Vec3 camUp           = new Vec3(upVec.x(), upVec.y(), upVec.z());
        Vec3 camRight        = new Vec3(-leftVec.x(), -leftVec.y(), -leftVec.z());

        // Collect entities to draw lines to — two independent sources:
        //   1) Normal snaplines: ESP-matched entities when the toggle is on
        //   2) Hunt snaplines:   all alive Shulkers when auto hunt is on
        List<Entity> toRender    = new ArrayList<>();
        Entity closestEntity     = null;
        double closestDist       = Double.MAX_VALUE;
        Entity secondEntity      = null;
        double secondDist        = Double.MAX_VALUE;

        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity == client.player) continue;

            boolean include = false;

            // Source 1: normal ESP-matched entities
            if (EnemyBoxesState.snaplinesEnabled && EnemyBoxesState.enabled
                    && entity instanceof LivingEntity living && living.isAlive()
                    && EnemyBoxesState.matches(living)) {
                include = true;
            }

            // Source 2: force-include shulkers when hunt is active
            if (EnemyBoxesState.autoHuntEnabled
                    && entity instanceof Shulker s && s.isAlive()) {
                include = true;
            }

            if (!include) continue;

            toRender.add(entity);
            double dist = client.player.distanceTo(entity);
            if (dist < closestDist) {
                secondDist    = closestDist;
                secondEntity  = closestEntity;
                closestDist   = dist;
                closestEntity = entity;
            } else if (dist < secondDist) {
                secondDist   = dist;
                secondEntity = entity;
            }
        }

        if (toRender.isEmpty()) return;

        final Entity finalClosest = closestEntity;
        final Entity finalSecond  = secondEntity;
        int thickness = EnemyBoxesState.snaplineThickness;

        int screenW = client.getWindow().getGuiScaledWidth();
        int screenH = client.getWindow().getGuiScaledHeight();

        for (Entity entity : toRender) {
            boolean isClosest = entity == finalClosest;
            boolean isSecond  = entity == finalSecond;
            if (EnemyBoxesState.snaplinesOnlyClosest && !isClosest && !isSecond) continue;

            // Lerp position between previous and current tick for smooth sub-tick motion
            double lerpX = entity.xOld + (entity.getX() - entity.xOld) * tickDelta;
            double lerpY = entity.yOld + (entity.getY() - entity.yOld) * tickDelta
                           + entity.getBbHeight() / 2.0;
            double lerpZ = entity.zOld + (entity.getZ() - entity.zOld) * tickDelta;
            Vec3 center  = new Vec3(lerpX, lerpY, lerpZ);

            Vec3 rel = center.subtract(camPos);
            double forward = rel.dot(camForward);
            double right   = rel.dot(camRight);
            double up      = rel.dot(camUp);

            double sx;
            double sy;
            if (forward > 0.0) {
                Vec3 projected = client.gameRenderer.projectPointToScreen(center);

                // `projectPointToScreen` already gives us a stable forward-facing projection.
                // Only discard pathological outputs; valid off-screen points should be
                // clamped to the viewport edge below.
                if (!Double.isFinite(projected.x) || !Double.isFinite(projected.y)) continue;

                sx = cx + projected.x * cx;
                sy = cy - projected.y * cy;
            } else {
                if (!EnemyBoxesState.drawOffscreenEnemies) continue;

                // For behind-camera targets, build an indicator direction from the
                // camera-space right/up axes instead of perspective projection.
                double planarLen = Math.hypot(right, up);
                double dirX;
                double dirY;

                if (planarLen < 1.0e-6) {
                    dirX = 0.0;
                    dirY = rel.y >= 0.0 ? 1.0 : -1.0;
                } else {
                    dirX = right / planarLen;
                    dirY = up / planarLen;
                }

                double scale = Math.max(screenW, screenH) * 2.0;
                sx = cx + dirX * scale;
                sy = cy - dirY * scale;
            }

            boolean onScreen = sx >= 0 && sx < screenW && sy >= 0 && sy < screenH;
            if (!onScreen) {
                if (!EnemyBoxesState.drawOffscreenEnemies) continue;
                ScreenPoint clamped = clampToScreenEdge(cx, cy, sx, sy, screenW, screenH, 2);
                sx = clamped.x();
                sy = clamped.y();
            }

            // Green for closest, yellow for 2nd closest, red for all others
            int color = isClosest ? 0xFF00FF00 : isSecond ? 0xFFFFFF00 : 0xFFFF0000;
            drawThickLine(graphics, (int) cx, (int) cy, (int) Math.round(sx), (int) Math.round(sy), thickness, color);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Box distance labels
    // ─────────────────────────────────────────────────────────────────────────

    private static void renderBoxDistances(GuiGraphics graphics, Minecraft client, float tickDelta) {
        if (client.gameRenderer == null) return;

        Camera camera = client.gameRenderer.getMainCamera();
        float cx = client.getWindow().getGuiScaledWidth()  / 2f;
        float cy = client.getWindow().getGuiScaledHeight() / 2f;

        Vec3 camPos = camera.position();
        Vector3fc forwardVec = camera.forwardVector();
        Vec3 camForward = new Vec3(forwardVec.x(), forwardVec.y(), forwardVec.z());

        int screenW = client.getWindow().getGuiScaledWidth();
        int screenH = client.getWindow().getGuiScaledHeight();

        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity == client.player) continue;

            boolean include = false;
            if (EnemyBoxesState.enabled && entity instanceof LivingEntity living && living.isAlive()
                    && EnemyBoxesState.matches(living)) {
                include = true;
            }
            if (EnemyBoxesState.autoHuntEnabled
                    && entity instanceof Shulker s && s.isAlive()) {
                include = true;
            }
            if (!include) continue;

            // Lerp to the top-center of the bounding box
            double lerpX = entity.xOld + (entity.getX() - entity.xOld) * tickDelta;
            double lerpY = entity.yOld + (entity.getY() - entity.yOld) * tickDelta
                           + entity.getBbHeight();
            double lerpZ = entity.zOld + (entity.getZ() - entity.zOld) * tickDelta;
            Vec3 topPos = new Vec3(lerpX, lerpY, lerpZ);

            // Skip entities behind the camera
            if (topPos.subtract(camPos).dot(camForward) <= 0.0) continue;

            Vec3 projected = client.gameRenderer.projectPointToScreen(topPos);
            if (!Double.isFinite(projected.x) || !Double.isFinite(projected.y)) continue;

            double sx = cx + projected.x * cx;
            double sy = cy - projected.y * cy;

            if (sx < 0 || sx >= screenW || sy < -16 || sy >= screenH) continue;

            String text  = String.format("%.1fm", client.player.distanceTo(entity));
            int    textW = client.font.width(text);
            int    tx    = (int)(sx - textW / 2.0);
            int    ty    = (int) sy - 12; // a few pixels above the box top

            graphics.drawString(client.font, text, tx, ty, 0xFFFFFFFF, true);
        }
    }

    private static ScreenPoint clampToScreenEdge(float cx, float cy,
                                                 double sx, double sy,
                                                 int screenW, int screenH,
                                                 int padding) {
        double dx = sx - cx;
        double dy = sy - cy;
        if (Math.abs(dx) < 1.0e-6 && Math.abs(dy) < 1.0e-6) {
            return new ScreenPoint((int) cx, (int) cy);
        }

        double minX = padding;
        double maxX = screenW - 1.0 - padding;
        double minY = padding;
        double maxY = screenH - 1.0 - padding;

        double tx = Double.POSITIVE_INFINITY;
        if (dx > 0) tx = (maxX - cx) / dx;
        if (dx < 0) tx = (minX - cx) / dx;

        double ty = Double.POSITIVE_INFINITY;
        if (dy > 0) ty = (maxY - cy) / dy;
        if (dy < 0) ty = (minY - cy) / dy;

        double t = Math.min(tx, ty);
        if (!Double.isFinite(t)) t = 0.0;
        t = Math.max(0.0, Math.min(1.0, t));

        double clampedX = Math.max(minX, Math.min(maxX, cx + dx * t));
        double clampedY = Math.max(minY, Math.min(maxY, cy + dy * t));
        return new ScreenPoint((int) Math.round(clampedX), (int) Math.round(clampedY));
    }

    private record ScreenPoint(int x, int y) {}

    private static void drawThickLine(GuiGraphics graphics, int x0, int y0, int x1, int y1,
                                      int thickness, int color) {
        float dx  = x1 - x0;
        float dy  = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.5f) return;

        // Draw the line as a single rotated filled rectangle — O(1) draw calls
        // regardless of line length, and no Bresenham integer-overflow risk.
        float angle = (float) Math.atan2(dy, dx);
        int   half  = Math.max(1, thickness) / 2;
        int   lenI  = (int) Math.ceil(len);

        graphics.pose().pushMatrix();
        graphics.pose().translate(x0, y0);
        graphics.pose().rotate(angle);
        // Rectangle spans [0, lenI] along X and [-half, thickness-half] along Y
        graphics.fill(0, -half, lenI, thickness - half, color);
        graphics.pose().popMatrix();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Hunt debug overlay
    // ─────────────────────────────────────────────────────────────────────────

    private record HudLine(String text, int color) {}

    private static void renderHuntDebug(GuiGraphics graphics, Minecraft client) {
        List<HudLine> lines = new ArrayList<>();

        // ── Auto-hunt state ───────────────────────────────────────────────────
        if (EnemyBoxesState.autoHuntEnabled) {
            String shulker = HideonleafHunt.debugShulkerDist >= 0
                    ? "Shulker: " + String.format("%.1f", HideonleafHunt.debugShulkerDist) + "m"
                    : "Shulker: none";
            String bullet = HideonleafHunt.debugBulletDist >= 0
                    ? "Bullet: " + String.format("%.1f", HideonleafHunt.debugBulletDist)
                      + "m [" + HideonleafHunt.debugBulletState + "]"
                    : "Bullet: none";
            lines.add(new HudLine("[Hunt] " + HideonleafHunt.debugState, 0xFFFFFF00));
            lines.add(new HudLine(shulker,                               0xFFFFFFFF));
            lines.add(new HudLine(bullet,                                0xFFFFFFFF));
        }

        // ── Shard tracker ─────────────────────────────────────────────────────
        if (EnemyBoxesState.shardTrackerEnabled) {
            HideonleafShardTracker.updateComputed();
            String price = HideonleafShardTracker.priceLoaded
                    ? HideonleafShardTracker.fmtPrice(HideonleafShardTracker.sellPrice)
                    : "...";
            lines.add(new HudLine("Shards: "        + HideonleafShardTracker.totalShards,                             0xFF00FF00));
            lines.add(new HudLine("Shard Price: "   + price,                                                          0xFFFFFFFF));
            lines.add(new HudLine("Session Value: " + HideonleafShardTracker.fmtCoins(HideonleafShardTracker.sessionCoins), 0xFFFFFFFF));
            lines.add(new HudLine("Coins/hr: "      + HideonleafShardTracker.fmtCoins(HideonleafShardTracker.coinsPerHour), 0xFFFFFFFF));
        }

        if (lines.isEmpty()) return;

        int lineH = 10;
        int maxW  = 0;
        for (HudLine l : lines) maxW = Math.max(maxW, client.font.width(l.text()));

        // Draw in the top-right corner so it doesn't overlap the CPS widget
        int bx = client.getWindow().getGuiScaledWidth() - maxW - 8;
        int by = 4;

        graphics.fill(bx - 2, by - 2, bx + maxW + 4, by + lineH * lines.size() + 2, 0xAA000000);
        for (int i = 0; i < lines.size(); i++) {
            HudLine l = lines.get(i);
            graphics.drawString(client.font, l.text(), bx, by + lineH * i, l.color(), true);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Auto-clicker activity graph
    // ─────────────────────────────────────────────────────────────────────────

    private static final int GRAPH_W   = 240;
    private static final int GRAPH_H   = 68;
    private static final int GRAPH_PAD = 6;

    private static void renderClickGraph(GuiGraphics graphics, Minecraft client) {
        int screenH = client.getWindow().getGuiScaledHeight();

        // Position: bottom-left, above the hotbar (hotbar ≈ 22px + 6px clearance)
        int gx = 4;
        int gy = screenH - 22 - 6 - GRAPH_H;

        // Background
        graphics.fill(gx, gy, gx + GRAPH_W, gy + GRAPH_H, 0xAA000000);

        float plotX0 = gx + GRAPH_PAD;
        float plotX1 = gx + GRAPH_W - GRAPH_PAD;
        float plotY0 = gy + GRAPH_PAD + 8; // leave room for "Live Activity" label
        float plotY1 = gy + GRAPH_H - GRAPH_PAD;
        float plotW  = plotX1 - plotX0;
        float plotH  = plotY1 - plotY0;

        int   minCps  = EnemyBoxesState.acCpsMin;
        int   modeCps = EnemyBoxesState.acCpsMode;
        int   maxCps  = EnemyBoxesState.acCpsMax;

        // Y-axis range: extend 40% below min and 40% above max to show outliers,
        // matching the C++ reference implementation.
        float yLo    = minCps  * 0.4f;
        float yHi    = maxCps  * 1.4f;
        float yRange = Math.max(yHi - yLo, 0.1f);

        // Convert a CPS value to a Y pixel coordinate (higher CPS = higher on screen)
        // Extracted as a small lambda-equivalent via a local method pattern inline:
        // y = plotY0 + (1 - (cps - yLo) / yRange) * plotH
        float minRefY  = plotY0 + (1f - (minCps  - yLo) / yRange) * plotH;
        float modeRefY = plotY0 + (1f - (modeCps - yLo) / yRange) * plotH;
        float maxRefY  = plotY0 + (1f - (maxCps  - yLo) / yRange) * plotH;

        // Reference lines: dim blue (min), yellow (mode), red (max)
        drawLine(graphics, (int)plotX0, (int)minRefY,  (int)plotX1, (int)minRefY,  0x4A4AB4FF);
        drawLine(graphics, (int)plotX0, (int)modeRefY, (int)plotX1, (int)modeRefY, 0x4AFFC840);
        drawLine(graphics, (int)plotX0, (int)maxRefY,  (int)plotX1, (int)maxRefY,  0x4AFF5A5A);

        // "Live Activity" label (top-left inside the box)
        graphics.drawString(client.font, "Live Activity",
                gx + GRAPH_PAD, gy + GRAPH_PAD - 1, 0xFFAAAAAA, false);

        // History line + dots
        int count = AutoClicker.historyCount;
        int head  = AutoClicker.historyHead;

        if (count > 1) {
            int   visible = Math.min(count, 80);
            float step    = plotW / (float)(visible - 1);

            int prevPx = -1, prevPy = -1;

            for (int i = 0; i < visible; i++) {
                int   idx = (head - visible + i + AutoClicker.HISTORY_SIZE) % AutoClicker.HISTORY_SIZE;
                float cps = AutoClicker.clickHistory[idx];

                int px = (int)(plotX0 + i * step);
                int py = (int)(plotY0 + (1f - (cps - yLo) / yRange) * plotH);
                py = Math.max((int)plotY0, Math.min((int)plotY1, py));

                if (prevPx >= 0) {
                    drawLine(graphics, prevPx, prevPy, px, py, 0xCC78DCA0); // green line
                }
                // Dot
                graphics.fill(px - 1, py - 1, px + 2, py + 2, 0xFF78DCA0);

                prevPx = px;
                prevPy = py;
            }

            // Y-axis CPS labels — iterate top-to-bottom (max→mode→min), skip any
            // label whose top edge would land within 9px of the previous one drawn.
            int[] labelRefY  = { (int)maxRefY,  (int)modeRefY, (int)minRefY  };
            int[] labelCps   = { maxCps,         modeCps,       minCps        };
            int[] labelColor = { 0xFFFF5A5A,    0xFFFFC840,    0xFF4AB4FF    };
            int   lastY      = Integer.MIN_VALUE;
            for (int i = 0; i < 3; i++) {
                int ly = labelRefY[i] - 8; // draw 8px above the reference line
                if (ly - lastY < 9) continue; // would overlap previous label
                graphics.drawString(client.font, labelCps[i] + "",
                        gx + GRAPH_PAD, ly, labelColor[i], false);
                lastY = ly;
            }
        } else {
            // No data yet — centred hint text
            String hint = "Hold LMB to see activity";
            int    tw   = client.font.width(hint);
            graphics.drawString(client.font, hint,
                    gx + (GRAPH_W - tw) / 2,
                    gy + GRAPH_H / 2 - 4,
                    0xFF555555, false);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CPS display
    // ─────────────────────────────────────────────────────────────────────────

    public static void renderCps(GuiGraphics graphics, Minecraft client, float x, float y, float scale, boolean highlight) {
        String text = "CPS: " + EnemyBoxesClient.getCps();
        int bgColor   = highlight ? 0xCC4444FF : 0xAA000000;
        int textColor = 0xFFFFFF00;

        int w = (int)(client.font.width(text) * scale) + 6;
        int h = (int)(9 * scale) + 4;

        graphics.fill((int)x, (int)y, (int)x + w, (int)y + h, bgColor);

        graphics.pose().pushMatrix();
        graphics.pose().translate(x + 3, y + 2);
        graphics.pose().scale(scale, scale);
        graphics.drawString(client.font, text, 0, 0, textColor, true);
        graphics.pose().popMatrix();
    }

    public static int getCpsWidth(Minecraft client, float scale) {
        return (int)(client.font.width("CPS: " + EnemyBoxesClient.getCps()) * scale) + 6;
    }

    public static int getCpsHeight(float scale) {
        return (int)(9 * scale) + 4;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Line drawing helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static void drawLine(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        int x = x0, y = y0;

        while (true) {
            graphics.fill(x, y, x + 1, y + 1, color);
            if (x == x1 && y == y1) break;
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; x += sx; }
            if (e2 <  dx) { err += dx; y += sy; }
        }
    }
}
