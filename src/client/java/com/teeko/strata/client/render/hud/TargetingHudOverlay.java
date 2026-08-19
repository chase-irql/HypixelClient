package com.teeko.strata.client.render.hud;

import com.teeko.strata.client.state.StrataState;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.List;

public final class TargetingHudOverlay {

    private static final int SEGMENTS = 128;
    private static final int FOV_COLOR = 0xFFFFFF00;

    private TargetingHudOverlay() {}

    public static void render(GuiGraphics graphics, Minecraft client, float tickDelta) {
        if (client.level == null) return;

        if (StrataState.enabled && StrataState.showFovCircle && StrataState.hasTarget()) {
            renderFovCircle(graphics, client);
        }

        if (client.player != null && (StrataState.snaplinesEnabled || StrataState.autoHuntEnabled)) {
            renderSnaplines(graphics, client, tickDelta);
        }

        if (client.player != null && StrataState.showBoxDistance
                && (StrataState.enabled || StrataState.autoHuntEnabled)) {
            renderBoxDistances(graphics, client, tickDelta);
        }
    }

    private static void renderFovCircle(GuiGraphics graphics, Minecraft client) {
        float centerX = client.getWindow().getGuiScaledWidth() / 2f;
        float centerY = client.getWindow().getGuiScaledHeight() / 2f;
        float radius = StrataState.lockFov;

        for (int i = 0; i < SEGMENTS; i++) {
            double a0 = 2 * Math.PI * i / SEGMENTS;
            double a1 = 2 * Math.PI * (i + 1) / SEGMENTS;

            int x0 = (int) (centerX + Math.cos(a0) * radius);
            int y0 = (int) (centerY + Math.sin(a0) * radius);
            int x1 = (int) (centerX + Math.cos(a1) * radius);
            int y1 = (int) (centerY + Math.sin(a1) * radius);

            HudDrawUtil.drawLine(graphics, x0, y0, x1, y1, FOV_COLOR);
        }
    }

    private static void renderSnaplines(GuiGraphics graphics, Minecraft client, float tickDelta) {
        if (client.gameRenderer == null) return;

        Camera camera = client.gameRenderer.getMainCamera();
        float centerX = client.getWindow().getGuiScaledWidth() / 2f;
        float centerY = client.getWindow().getGuiScaledHeight() / 2f;

        Vec3 camPos = camera.position();
        Vector3fc forwardVec = camera.forwardVector();
        Vector3fc upVec = camera.upVector();
        Vector3fc leftVec = camera.leftVector();
        Vec3 camForward = new Vec3(forwardVec.x(), forwardVec.y(), forwardVec.z());
        Vec3 camUp = new Vec3(upVec.x(), upVec.y(), upVec.z());
        Vec3 camRight = new Vec3(-leftVec.x(), -leftVec.y(), -leftVec.z());

        List<Entity> toRender = new ArrayList<>();
        Entity closestEntity = null;
        double closestDist = Double.MAX_VALUE;
        Entity secondEntity = null;
        double secondDist = Double.MAX_VALUE;

        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity == client.player) continue;

            boolean include = false;
            if (StrataState.snaplinesEnabled && StrataState.enabled
                    && entity instanceof LivingEntity living && living.isAlive()
                    && StrataState.matches(living)) {
                include = true;
            }
            if (StrataState.autoHuntEnabled && entity instanceof Shulker shulker && shulker.isAlive()) {
                include = true;
            }

            if (!include) continue;

            toRender.add(entity);
            double dist = client.player.distanceTo(entity);
            if (dist < closestDist) {
                secondDist = closestDist;
                secondEntity = closestEntity;
                closestDist = dist;
                closestEntity = entity;
            } else if (dist < secondDist) {
                secondDist = dist;
                secondEntity = entity;
            }
        }

        if (toRender.isEmpty()) return;

        int thickness = StrataState.snaplineThickness;
        int screenW = client.getWindow().getGuiScaledWidth();
        int screenH = client.getWindow().getGuiScaledHeight();

        for (Entity entity : toRender) {
            boolean isClosest = entity == closestEntity;
            boolean isSecond = entity == secondEntity;
            if (StrataState.snaplinesOnlyClosest && !isClosest && !isSecond) continue;

            double lerpX = entity.xOld + (entity.getX() - entity.xOld) * tickDelta;
            double lerpY = entity.yOld + (entity.getY() - entity.yOld) * tickDelta
                    + entity.getBbHeight() / 2.0;
            double lerpZ = entity.zOld + (entity.getZ() - entity.zOld) * tickDelta;
            Vec3 center = new Vec3(lerpX, lerpY, lerpZ);

            Vec3 rel = center.subtract(camPos);
            double forward = rel.dot(camForward);
            double right = rel.dot(camRight);
            double up = rel.dot(camUp);

            double sx;
            double sy;
            if (forward > 0.0) {
                Vec3 projected = client.gameRenderer.projectPointToScreen(center);
                if (!Double.isFinite(projected.x) || !Double.isFinite(projected.y)) continue;
                sx = centerX + projected.x * centerX;
                sy = centerY - projected.y * centerY;
            } else {
                if (!StrataState.drawOffscreenEnemies) continue;

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
                sx = centerX + dirX * scale;
                sy = centerY - dirY * scale;
            }

            boolean onScreen = sx >= 0 && sx < screenW && sy >= 0 && sy < screenH;
            if (!onScreen) {
                if (!StrataState.drawOffscreenEnemies) continue;
                ScreenPoint clamped = clampToScreenEdge(centerX, centerY, sx, sy, screenW, screenH, 2);
                sx = clamped.x();
                sy = clamped.y();
            }

            int color = isClosest ? 0xFF00FF00 : isSecond ? 0xFFFFFF00 : 0xFFFF0000;
            drawThickLine(graphics, (int) centerX, (int) centerY, (int) Math.round(sx), (int) Math.round(sy), thickness, color);
        }
    }

    private static void renderBoxDistances(GuiGraphics graphics, Minecraft client, float tickDelta) {
        if (client.gameRenderer == null) return;

        Camera camera = client.gameRenderer.getMainCamera();
        float centerX = client.getWindow().getGuiScaledWidth() / 2f;
        float centerY = client.getWindow().getGuiScaledHeight() / 2f;

        Vec3 camPos = camera.position();
        Vector3fc forwardVec = camera.forwardVector();
        Vec3 camForward = new Vec3(forwardVec.x(), forwardVec.y(), forwardVec.z());

        int screenW = client.getWindow().getGuiScaledWidth();
        int screenH = client.getWindow().getGuiScaledHeight();

        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity == client.player) continue;

            boolean include = false;
            if (StrataState.enabled && entity instanceof LivingEntity living && living.isAlive()
                    && StrataState.matches(living)) {
                include = true;
            }
            if (StrataState.autoHuntEnabled && entity instanceof Shulker shulker && shulker.isAlive()) {
                include = true;
            }
            if (!include) continue;

            double lerpX = entity.xOld + (entity.getX() - entity.xOld) * tickDelta;
            double lerpY = entity.yOld + (entity.getY() - entity.yOld) * tickDelta + entity.getBbHeight();
            double lerpZ = entity.zOld + (entity.getZ() - entity.zOld) * tickDelta;
            Vec3 topPos = new Vec3(lerpX, lerpY, lerpZ);

            if (topPos.subtract(camPos).dot(camForward) <= 0.0) continue;

            Vec3 projected = client.gameRenderer.projectPointToScreen(topPos);
            if (!Double.isFinite(projected.x) || !Double.isFinite(projected.y)) continue;

            double sx = centerX + projected.x * centerX;
            double sy = centerY - projected.y * centerY;

            if (sx < 0 || sx >= screenW || sy < -16 || sy >= screenH) continue;

            String text = String.format("%.1fm", client.player.distanceTo(entity));
            int textWidth = client.font.width(text);
            int textX = (int) (sx - textWidth / 2.0);
            int textY = (int) sy - 12;

            graphics.drawString(client.font, text, textX, textY, 0xFFFFFFFF, true);
        }
    }

    private static ScreenPoint clampToScreenEdge(float centerX, float centerY,
                                                 double sx, double sy,
                                                 int screenW, int screenH,
                                                 int padding) {
        double dx = sx - centerX;
        double dy = sy - centerY;
        if (Math.abs(dx) < 1.0e-6 && Math.abs(dy) < 1.0e-6) {
            return new ScreenPoint((int) centerX, (int) centerY);
        }

        double minX = padding;
        double maxX = screenW - 1.0 - padding;
        double minY = padding;
        double maxY = screenH - 1.0 - padding;

        double tx = Double.POSITIVE_INFINITY;
        if (dx > 0) tx = (maxX - centerX) / dx;
        if (dx < 0) tx = (minX - centerX) / dx;

        double ty = Double.POSITIVE_INFINITY;
        if (dy > 0) ty = (maxY - centerY) / dy;
        if (dy < 0) ty = (minY - centerY) / dy;

        double t = Math.min(tx, ty);
        if (!Double.isFinite(t)) t = 0.0;
        t = Math.max(0.0, Math.min(1.0, t));

        double clampedX = Math.max(minX, Math.min(maxX, centerX + dx * t));
        double clampedY = Math.max(minY, Math.min(maxY, centerY + dy * t));
        return new ScreenPoint((int) Math.round(clampedX), (int) Math.round(clampedY));
    }

    private static void drawThickLine(GuiGraphics graphics, int x0, int y0, int x1, int y1,
                                      int thickness, int color) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.5f) return;

        float angle = (float) Math.atan2(dy, dx);
        int half = Math.max(1, thickness) / 2;
        int lenI = (int) Math.ceil(len);

        graphics.pose().pushMatrix();
        graphics.pose().translate(x0, y0);
        graphics.pose().rotate(angle);
        graphics.fill(0, -half, lenI, thickness - half, color);
        graphics.pose().popMatrix();
    }

    private record ScreenPoint(int x, int y) {}
}
