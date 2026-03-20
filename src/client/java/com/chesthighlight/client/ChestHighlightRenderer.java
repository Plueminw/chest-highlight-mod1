package com.chesthighlight.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Renders glowing rectangular outlines around chunks flagged as having 5+ chests.
 *
 * Visual: thick pulsing orange-gold border, 4 vertical corner pillars + horizontal
 * rings at multiple Y levels so it is visible from surface and underground.
 * Drawn with depth-test disabled so it shows through terrain.
 */
public final class ChestHighlightRenderer {

    private ChestHighlightRenderer() {}

    /** Thread-safe: updated from network thread, read on render thread. */
    private static final Set<ChunkPos> HIGHLIGHTED = ConcurrentHashMap.newKeySet();

    // Outline colour — vivid orange-gold
    private static final float R = 1.0f;
    private static final float G = 0.65f;
    private static final float B = 0.0f;

    private static final int WORLD_MIN_Y  = -64;
    private static final int WORLD_MAX_Y  = 320;

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public static void addHighlight(ChunkPos pos)    { HIGHLIGHTED.add(pos); }
    public static boolean isHighlighted(ChunkPos pos)   { return HIGHLIGHTED.contains(pos); }
    public static void removeHighlight(ChunkPos pos) { HIGHLIGHTED.remove(pos); }
    public static void clearAll()                    { HIGHLIGHTED.clear(); }

    // -------------------------------------------------------------------------
    // Render hook  (registered via WorldRenderEvents.LAST)
    // -------------------------------------------------------------------------

    public static void onWorldRender(WorldRenderContext ctx) {
        if (HIGHLIGHTED.isEmpty()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        Entity camera = client.getCameraEntity();
        if (camera == null || client.world == null) return;

        Vec3d camPos = ctx.camera().getPos();
        MatrixStack matrices = ctx.matrixStack();
        if (matrices == null) return;

        // Smooth pulse: alpha oscillates between 0.35 and 0.90
        long time = client.world.getTime();
        float tickDelta = ctx.tickCounter().getTickDelta(true);
        float pulse = 0.625f + 0.265f * (float) Math.sin((time + tickDelta) * 0.10);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.lineWidth(3.5f);
        RenderSystem.setShader(ShaderProgramKeys.RENDERTYPE_LINES);

        Tessellator tess = Tessellator.getInstance();

        matrices.push();
        matrices.translate(-camPos.x, -camPos.y, -camPos.z);
        Matrix4f mat = matrices.peek().getPositionMatrix();

        BufferBuilder buf = tess.begin(VertexFormat.DrawMode.LINES, VertexFormats.LINES);

        for (ChunkPos cp : HIGHLIGHTED) {
            drawChunkOutline(buf, mat, matrices, cp, pulse);
        }

        // End and draw — in 1.21.1 we must call tess.draw() not BufferRenderer directly
        try {
            BuiltBuffer built = buf.endNullable();
            if (built != null) {
                BufferRenderer.drawWithGlobalProgram(built);
            }
        } catch (Exception ignored) {
            // Buffer was empty — nothing to draw
        }

        matrices.pop();

        RenderSystem.lineWidth(1.0f);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    // -------------------------------------------------------------------------
    // Drawing helpers
    // -------------------------------------------------------------------------

    private static void drawChunkOutline(BufferBuilder buf, Matrix4f mat,
                                         MatrixStack matrices,
                                         ChunkPos cp, float alpha) {
        double x0 = cp.getStartX();
        double z0 = cp.getStartZ();
        double x1 = cp.getEndX() + 1.0;
        double z1 = cp.getEndZ() + 1.0;
        double yBot = WORLD_MIN_Y;
        double yTop = WORLD_MAX_Y;

        // 4 vertical corner pillars
        vLine(buf, mat, matrices, x0, z0, yBot, yTop, alpha);
        vLine(buf, mat, matrices, x1, z0, yBot, yTop, alpha);
        vLine(buf, mat, matrices, x0, z1, yBot, yTop, alpha);
        vLine(buf, mat, matrices, x1, z1, yBot, yTop, alpha);

        // Horizontal rings at evenly-spaced Y levels (every 64 blocks + extremes)
        int[] ringYs = { -60, 0, 64, 128, 192, 256, 319 };
        float ringAlpha = alpha * 0.65f;
        for (int y : ringYs) {
            hRect(buf, mat, matrices, x0, z0, x1, z1, y, ringAlpha);
        }
    }

    /** Vertical line at corner (x, z) from yBottom to yTop. */
    private static void vLine(BufferBuilder buf, Matrix4f mat, MatrixStack ms,
                               double x, double z, double y0, double y1, float a) {
        // For LINES format we need a normal per vertex
        float[] normal = computeNormal(ms, 0, 1, 0);
        addVertex(buf, mat, x, y0, z, a, normal);
        addVertex(buf, mat, x, y1, z, a, normal);
    }

    /** Horizontal rectangle outline at a fixed Y level. */
    private static void hRect(BufferBuilder buf, Matrix4f mat, MatrixStack ms,
                               double x0, double z0, double x1, double z1,
                               double y, float a) {
        segment(buf, mat, ms, x0, y, z0, x1, y, z0, a);
        segment(buf, mat, ms, x1, y, z0, x1, y, z1, a);
        segment(buf, mat, ms, x1, y, z1, x0, y, z1, a);
        segment(buf, mat, ms, x0, y, z1, x0, y, z0, a);
    }

    private static void segment(BufferBuilder buf, Matrix4f mat, MatrixStack ms,
                                 double ax, double ay, double az,
                                 double bx, double by, double bz,
                                 float alpha) {
        // Normal points from A to B (unit vector)
        float dx = (float)(bx - ax);
        float dy = (float)(by - ay);
        float dz = (float)(bz - az);
        float len = (float) Math.sqrt(dx*dx + dy*dy + dz*dz);
        if (len == 0) return;
        float[] normal = computeNormal(ms, dx/len, dy/len, dz/len);
        addVertex(buf, mat, ax, ay, az, alpha, normal);
        addVertex(buf, mat, bx, by, bz, alpha, normal);
    }

    private static float[] computeNormal(MatrixStack ms, float nx, float ny, float nz) {
        // Transform normal by the normal matrix
        var normalMat = ms.peek().getNormalMatrix();
        float rx = normalMat.m00() * nx + normalMat.m10() * ny + normalMat.m20() * nz;
        float ry = normalMat.m01() * nx + normalMat.m11() * ny + normalMat.m21() * nz;
        float rz = normalMat.m02() * nx + normalMat.m12() * ny + normalMat.m22() * nz;
        return new float[]{ rx, ry, rz };
    }

    private static void addVertex(BufferBuilder buf, Matrix4f mat,
                                   double x, double y, double z,
                                   float alpha, float[] normal) {
        buf.vertex(mat, (float) x, (float) y, (float) z)
           .color(R, G, B, alpha)
           .normal(normal[0], normal[1], normal[2]);
    }
}
