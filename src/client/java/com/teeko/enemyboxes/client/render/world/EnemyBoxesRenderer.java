package com.teeko.enemyboxes.client.render.world;

import com.teeko.enemyboxes.client.EnemyBoxesClient;
import com.teeko.enemyboxes.client.state.EnemyBoxesState;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.util.OptionalDouble;
import java.util.OptionalInt;

public final class EnemyBoxesRenderer {

    private static final RenderPipeline BOX_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath(EnemyBoxesClient.MOD_ID, "pipeline/enemy_box_through_walls"))
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .build()
    );

    private static final ByteBufferBuilder allocator = new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE);
    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET = new Vector3f();
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
    private static final Matrix4f capturedViewMatrix = new Matrix4f();

    private static BufferBuilder buffer;
    private static MappableRingBuffer vertexBuffer;
    private static int verticesWritten = 0;

    public static void render(Camera camera, Matrix4f viewMatrix, float tickDelta) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) return;

        Vec3 camPos = camera.position();
        double camX = camPos.x;
        double camY = camPos.y;
        double camZ = camPos.z;

        capturedViewMatrix.set(viewMatrix);
        boolean anyDrawn = false;

        // ── Regular ESP boxes ────────────────────────────────────────────────
        if (EnemyBoxesState.enabled && EnemyBoxesState.hasTarget()) {
            for (Entity entity : client.level.entitiesForRendering()) {
                if (entity == client.player) continue;
                if (!(entity instanceof LivingEntity living) || !living.isAlive()) continue;
                if (!EnemyBoxesState.matches(living)) continue;

                AABB worldBox = entity.getBoundingBox();
                if (worldBox.maxX - worldBox.minX < 0.01
                        && worldBox.maxY - worldBox.minY < 0.01
                        && worldBox.maxZ - worldBox.minZ < 0.01) continue;

                if (buffer == null) {
                    buffer = new BufferBuilder(allocator, BOX_PIPELINE.getVertexFormatMode(), BOX_PIPELINE.getVertexFormat());
                    verticesWritten = 0;
                }

                double lerpX = lerp(tickDelta, entity.xOld, entity.getX());
                double lerpY = lerp(tickDelta, entity.yOld, entity.getY());
                double lerpZ = lerp(tickDelta, entity.zOld, entity.getZ());
                double shiftX = lerpX - entity.getX();
                double shiftY = lerpY - entity.getY();
                double shiftZ = lerpZ - entity.getZ();

                AABB localBox = worldBox
                        .move(shiftX, shiftY, shiftZ)
                        .move(-camX, -camY, -camZ);

                Matrix4f vertexMat = new Matrix4f();

                boolean isLocked = EnemyBoxesState.lockedTarget != null &&
                        EnemyBoxesState.lockedTarget.equals(entity.getUUID());

                float r = isLocked ? 0f : 1f;
                float g = isLocked ? 1f : 0f;
                renderBox(vertexMat, buffer, localBox, r, g, 0f, 1f);
                anyDrawn = true;
            }
        }

        // ── Hunt boxes: shulkers (magenta) + targeted bullet (cyan) ──────────
        // Rendered independently of ESP so the player can always see them.
        if (EnemyBoxesState.autoHuntEnabled) {
            for (Entity entity : client.level.entitiesForRendering()) {
                if (entity == client.player) continue;

                boolean isShulker = entity instanceof Shulker s && s.isAlive();
                boolean isBullet  = entity instanceof ShulkerBullet
                        && EnemyBoxesState.huntTrackedBullet != null
                        && entity.getUUID().equals(EnemyBoxesState.huntTrackedBullet);

                if (!isShulker && !isBullet) continue;

                // Skip shulkers that ESP is already drawing to avoid double-draw
                if (isShulker && EnemyBoxesState.enabled
                        && EnemyBoxesState.matches((LivingEntity) entity)) continue;

                AABB worldBox = entity.getBoundingBox();
                if (worldBox.maxX - worldBox.minX < 0.01
                        && worldBox.maxY - worldBox.minY < 0.01
                        && worldBox.maxZ - worldBox.minZ < 0.01) continue;

                if (buffer == null) {
                    buffer = new BufferBuilder(allocator, BOX_PIPELINE.getVertexFormatMode(), BOX_PIPELINE.getVertexFormat());
                    verticesWritten = 0;
                }

                double lerpX  = lerp(tickDelta, entity.xOld, entity.getX());
                double lerpY  = lerp(tickDelta, entity.yOld, entity.getY());
                double lerpZ  = lerp(tickDelta, entity.zOld, entity.getZ());
                double shiftX = lerpX - entity.getX();
                double shiftY = lerpY - entity.getY();
                double shiftZ = lerpZ - entity.getZ();

                AABB localBox = worldBox
                        .move(shiftX, shiftY, shiftZ)
                        .move(-camX, -camY, -camZ);

                Matrix4f vertexMat = new Matrix4f();

                if (isShulker) {
                    boolean isActive = EnemyBoxesState.huntLockedShulker != null
                            && EnemyBoxesState.huntLockedShulker.equals(entity.getUUID());
                    // Magenta for active shulker, dim purple for others in range
                    float intensity = isActive ? 1.0f : 0.5f;
                    renderBox(vertexMat, buffer, localBox, intensity, 0f, intensity, 1f);
                } else {
                    // Always render the outgoing tracked bullet; brighten it when
                    // it's also in true aim-lock range.
                    boolean isAimLocked = EnemyBoxesState.huntLockedBullet != null
                            && EnemyBoxesState.huntLockedBullet.equals(entity.getUUID());
                    float cyan = isAimLocked ? 1.0f : 0.55f;
                    renderBox(vertexMat, buffer, localBox, 0f, cyan, cyan, 1f);
                }
                anyDrawn = true;
            }
        }

        if (anyDrawn && buffer != null && verticesWritten > 0) {
            drawBoxes(client);
        } else if (buffer != null) {
            buffer.buildOrThrow().close();
            buffer = null;
            verticesWritten = 0;
        }
    }

    private static void renderBox(Matrix4fc m, BufferBuilder buf, AABB box,
                                  float r, float g, float b, float a) {
        float x0 = (float) box.minX, y0 = (float) box.minY, z0 = (float) box.minZ;
        float x1 = (float) box.maxX, y1 = (float) box.maxY, z1 = (float) box.maxZ;

        line(buf, m, x0,y0,z0, x1,y0,z0, r,g,b,a);
        line(buf, m, x1,y0,z0, x1,y0,z1, r,g,b,a);
        line(buf, m, x1,y0,z1, x0,y0,z1, r,g,b,a);
        line(buf, m, x0,y0,z1, x0,y0,z0, r,g,b,a);
        line(buf, m, x0,y1,z0, x1,y1,z0, r,g,b,a);
        line(buf, m, x1,y1,z0, x1,y1,z1, r,g,b,a);
        line(buf, m, x1,y1,z1, x0,y1,z1, r,g,b,a);
        line(buf, m, x0,y1,z1, x0,y1,z0, r,g,b,a);
        line(buf, m, x0,y0,z0, x0,y1,z0, r,g,b,a);
        line(buf, m, x1,y0,z0, x1,y1,z0, r,g,b,a);
        line(buf, m, x1,y0,z1, x1,y1,z1, r,g,b,a);
        line(buf, m, x0,y0,z1, x0,y1,z1, r,g,b,a);
    }

    private static void line(BufferBuilder buf, Matrix4fc m,
                             float x0, float y0, float z0,
                             float x1, float y1, float z1,
                             float r, float g, float b, float a) {
        float dx = x1-x0, dy = y1-y0, dz = z1-z0;
        float len = (float) Math.sqrt(dx*dx + dy*dy + dz*dz);
        if (len == 0) return;
        float nx = dx/len, ny = dy/len, nz = dz/len;

        buf.addVertex(m, x0, y0, z0).setColor(r,g,b,a).setNormal(nx,ny,nz).setLineWidth(3.0f);
        buf.addVertex(m, x1, y1, z1).setColor(r,g,b,a).setNormal(nx,ny,nz).setLineWidth(3.0f);
        verticesWritten += 2;
    }

    private static void drawBoxes(Minecraft client) {
        MeshData built = buffer.buildOrThrow();
        MeshData.DrawState drawParams = built.drawState();
        VertexFormat format = drawParams.format();

        int needed = drawParams.vertexCount() * format.getVertexSize();
        if (vertexBuffer == null || vertexBuffer.size() < needed) {
            if (vertexBuffer != null) vertexBuffer.close();
            vertexBuffer = new MappableRingBuffer(
                    () -> EnemyBoxesClient.MOD_ID + " enemy box vbo",
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE,
                    needed
            );
        }

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        try (GpuBuffer.MappedView view = encoder.mapBuffer(
                vertexBuffer.currentBuffer().slice(0, built.vertexBuffer().remaining()), false, true)) {
            MemoryUtil.memCopy(built.vertexBuffer(), view.data());
        }

        GpuBuffer vertices = vertexBuffer.currentBuffer();
        RenderSystem.AutoStorageIndexBuffer shapeIndex =
                RenderSystem.getSequentialBuffer(BOX_PIPELINE.getVertexFormatMode());
        GpuBuffer indices = shapeIndex.getBuffer(drawParams.indexCount());

        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .writeTransform(capturedViewMatrix, COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

        try (RenderPass pass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(
                        () -> EnemyBoxesClient.MOD_ID + " enemy box pass",
                        client.getMainRenderTarget().getColorTextureView(),
                        OptionalInt.empty(),
                        client.getMainRenderTarget().getDepthTextureView(),
                        OptionalDouble.empty()
                )) {
            pass.setPipeline(BOX_PIPELINE);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", dynamicTransforms);
            pass.setVertexBuffer(0, vertices);
            pass.setIndexBuffer(indices, shapeIndex.type());
            pass.drawIndexed(0, 0, drawParams.indexCount(), 1);
        }

        built.close();
        vertexBuffer.rotate();
        buffer = null;
        verticesWritten = 0;
    }

    public static void close() {
        allocator.close();
        if (vertexBuffer != null) {
            vertexBuffer.close();
            vertexBuffer = null;
        }
    }

    private static double lerp(float delta, double prev, double curr) {
        return prev + (curr - prev) * delta;
    }
}
