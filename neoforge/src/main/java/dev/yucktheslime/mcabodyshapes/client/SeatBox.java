package dev.yucktheslime.mcabodyshapes.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.Direction;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Draws a model-space cube with floating-point texture offsets, matching
 * vanilla {@code ModelPart.Cube} winding and UV layout so 0.5-pixel seat
 * slices can sample a unique strip of clothing instead of repeating an
 * integer texV window.
 */
final class SeatBox {
    /**
     * Which of the six faces to emit. {@link #NO_SIDES_OR_BOTTOM} is used
     * on the inner seat when a jacket overlay will enclose it, so unpainted
     * overlay edges cannot reveal the inner dress.
     */
    record FaceMask(boolean down, boolean up, boolean west, boolean north, boolean east, boolean south) {
        static final FaceMask ALL = new FaceMask(true, true, true, true, true, true);
        static final FaceMask NO_SIDES_OR_BOTTOM = new FaceMask(false, true, false, true, false, true);
    }

    private SeatBox() {
    }

    /**
     * Uniform inflation on every side; all faces; standard per-face UVs.
     */
    static void draw(
            PoseStack.Pose pose,
            VertexConsumer vertices,
            int light,
            int overlay,
            int color,
            float x,
            float y,
            float z,
            float sx,
            float sy,
            float sz,
            float texU,
            float texV,
            float texWidth,
            float texHeight,
            float inflate
    ) {
        draw(pose, vertices, light, overlay, color,
                x, y, z, sx, sy, sz, texU, texV, texWidth, texHeight,
                inflate, inflate, inflate, inflate, inflate,
                FaceMask.ALL, false);
    }

    /**
     * @param inflateX        grown on both left and right
     * @param inflateDown     grown on the bottom (positive Y in this model)
     * @param inflateUp       grown on the top
     * @param inflateIn       grown on the inner (−Z, toward the torso)
     * @param inflateOut      grown on the outer (+Z, out the back)
     * @param faces           which faces to emit
     * @param fallbackNonBackToBack if true, every face except +Z (south/back)
     *                        uses the back-strip UVs so unpainted jacket
     *                        sides, bottom, or top stay opaque
     */
    static void draw(
            PoseStack.Pose pose,
            VertexConsumer vertices,
            int light,
            int overlay,
            int color,
            float x,
            float y,
            float z,
            float sx,
            float sy,
            float sz,
            float texU,
            float texV,
            float texWidth,
            float texHeight,
            float inflateX,
            float inflateDown,
            float inflateUp,
            float inflateIn,
            float inflateOut,
            FaceMask faces,
            boolean fallbackNonBackToBack
    ) {
        float x0 = x - inflateX;
        float y0 = y - inflateDown;
        float z0 = z - inflateIn;
        float x1 = x + sx + inflateX;
        float y1 = y + sy + inflateUp;
        float z1 = z + sz + inflateOut;

        // Same column/row layout as ModelPart.Cube.
        float u0 = texU;
        float u1 = texU + sz;
        float u2 = texU + sz + sx;
        float u3 = texU + sz + sx + sx;
        float u4 = texU + sz + sx + sz;
        float u5 = texU + sz + sx + sz + sx;
        float v0 = texV;
        float v1 = texV + sz;
        float v2 = texV + sz + sy;

        // +Z back strip, used as a fallback so empty overlay sides stay opaque.
        float bu1 = u4;
        float bv1 = v1;
        float bu2 = u5;
        float bv2 = v2;

        // DOWN:  (x1,y0,z1), (x0,y0,z1), (x0,y0,z0), (x1,y0,z0)  UV (u1,v0)-(u2,v1)
        if (faces.down()) {
            face(pose, vertices, light, overlay, color, texWidth, texHeight,
                    x1, y0, z1, x0, y0, z1, x0, y0, z0, x1, y0, z0,
                    fallbackNonBackToBack ? bu1 : u1,
                    fallbackNonBackToBack ? bv1 : v0,
                    fallbackNonBackToBack ? bu2 : u2,
                    fallbackNonBackToBack ? bv2 : v1,
                    Direction.DOWN);
        }
        // UP:    (x1,y1,z0), (x0,y1,z0), (x0,y1,z1), (x1,y1,z1)  UV (u2,v1)-(u3,v0)
        if (faces.up()) {
            face(pose, vertices, light, overlay, color, texWidth, texHeight,
                    x1, y1, z0, x0, y1, z0, x0, y1, z1, x1, y1, z1,
                    fallbackNonBackToBack ? bu1 : u2,
                    fallbackNonBackToBack ? bv1 : v1,
                    fallbackNonBackToBack ? bu2 : u3,
                    fallbackNonBackToBack ? bv2 : v0,
                    Direction.UP);
        }
        // WEST:  (x0,y0,z0), (x0,y0,z1), (x0,y1,z1), (x0,y1,z0)  UV (u0,v1)-(u1,v2)
        if (faces.west()) {
            face(pose, vertices, light, overlay, color, texWidth, texHeight,
                    x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0,
                    fallbackNonBackToBack ? bu1 : u0,
                    fallbackNonBackToBack ? bv1 : v1,
                    fallbackNonBackToBack ? bu2 : u1,
                    fallbackNonBackToBack ? bv2 : v2,
                    Direction.WEST);
        }
        // NORTH: (x1,y0,z0), (x0,y0,z0), (x0,y1,z0), (x1,y1,z0)  UV (u1,v1)-(u2,v2)
        if (faces.north()) {
            face(pose, vertices, light, overlay, color, texWidth, texHeight,
                    x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0,
                    fallbackNonBackToBack ? bu1 : u1,
                    fallbackNonBackToBack ? bv1 : v1,
                    fallbackNonBackToBack ? bu2 : u2,
                    fallbackNonBackToBack ? bv2 : v2,
                    Direction.NORTH);
        }
        // EAST:  (x1,y0,z1), (x1,y0,z0), (x1,y1,z0), (x1,y1,z1)  UV (u2,v1)-(u4,v2)
        if (faces.east()) {
            face(pose, vertices, light, overlay, color, texWidth, texHeight,
                    x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1,
                    fallbackNonBackToBack ? bu1 : u2,
                    fallbackNonBackToBack ? bv1 : v1,
                    fallbackNonBackToBack ? bu2 : u4,
                    fallbackNonBackToBack ? bv2 : v2,
                    Direction.EAST);
        }
        // SOUTH: (x0,y0,z1), (x1,y0,z1), (x1,y1,z1), (x0,y1,z1)  UV (u4,v1)-(u5,v2)
        if (faces.south()) {
            face(pose, vertices, light, overlay, color, texWidth, texHeight,
                    x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1,
                    u4, v1, u5, v2, Direction.SOUTH);
        }
    }

    private static void face(
            PoseStack.Pose pose,
            VertexConsumer vertices,
            int light,
            int overlay,
            int color,
            float texWidth,
            float texHeight,
            float x0, float y0, float z0,
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float x3, float y3, float z3,
            float u1, float v1, float u2, float v2,
            Direction direction
    ) {
        float tu0 = u2 / texWidth;
        float tv0 = v1 / texHeight;
        float tu1 = u1 / texWidth;
        float tv1 = v1 / texHeight;
        float tu2 = u1 / texWidth;
        float tv2 = v2 / texHeight;
        float tu3 = u2 / texWidth;
        float tv3 = v2 / texHeight;

        Vector3f normal = new Vector3f();
        pose.transformNormal(direction.step(), normal);
        float nx = normal.x();
        float ny = normal.y();
        float nz = normal.z();

        vert(pose, vertices, light, overlay, color, x0, y0, z0, tu0, tv0, nx, ny, nz);
        vert(pose, vertices, light, overlay, color, x1, y1, z1, tu1, tv1, nx, ny, nz);
        vert(pose, vertices, light, overlay, color, x2, y2, z2, tu2, tv2, nx, ny, nz);
        vert(pose, vertices, light, overlay, color, x3, y3, z3, tu3, tv3, nx, ny, nz);
    }

    private static void vert(
            PoseStack.Pose pose,
            VertexConsumer vertices,
            int light,
            int overlay,
            int color,
            float x,
            float y,
            float z,
            float u,
            float v,
            float nx,
            float ny,
            float nz
    ) {
        Matrix4f matrix = pose.pose();
        Vector3f pos = matrix.transformPosition(x / 16.0f, y / 16.0f, z / 16.0f, new Vector3f());
        vertices.addVertex(pos.x(), pos.y(), pos.z(), color, u, v, overlay, light, nx, ny, nz);
    }
}
