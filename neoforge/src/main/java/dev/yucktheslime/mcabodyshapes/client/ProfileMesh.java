package dev.yucktheslime.mcabodyshapes.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Emits single quads in model-pixel space with explicit per-vertex UVs. Used
 * for the sloped hip profile and hip flare, which are not axis-aligned cubes.
 * Winding is chosen so the geometric normal faces {@code outward}, matching
 * vanilla cube faces, and the lighting normal is that geometric normal.
 */
final class ProfileMesh {
    private ProfileMesh() {
    }

    /**
     * @param p    4 corners, each {x, y, z} in model pixels (Y down, +Z back)
     * @param uv   4 texel coordinates, each {u, v}
     * @param ox   outward hint x/y/z
     */
    static void quad(
            PoseStack.Pose pose, VertexConsumer vertices,
            int light, int overlay, int color,
            float texW, float texH,
            float[][] p, float[][] uv,
            float ox, float oy, float oz
    ) {
        float ax = p[1][0] - p[0][0], ay = p[1][1] - p[0][1], az = p[1][2] - p[0][2];
        float bx = p[2][0] - p[0][0], by = p[2][1] - p[0][1], bz = p[2][2] - p[0][2];
        float nx = ay * bz - az * by;
        float ny = az * bx - ax * bz;
        float nz = ax * by - ay * bx;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1.0e-5f) {
            // Degenerate first triangle (e.g. a collapsed corner); try the other diagonal.
            bx = p[3][0] - p[0][0]; by = p[3][1] - p[0][1]; bz = p[3][2] - p[0][2];
            ax = p[2][0] - p[0][0]; ay = p[2][1] - p[0][1]; az = p[2][2] - p[0][2];
            nx = ay * bz - az * by;
            ny = az * bx - ax * bz;
            nz = ax * by - ay * bx;
            len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len < 1.0e-5f) {
                return;
            }
        }
        nx /= len;
        ny /= len;
        nz /= len;
        int[] order = {0, 1, 2, 3};
        if (nx * ox + ny * oy + nz * oz < 0.0f) {
            order = new int[] {0, 3, 2, 1};
            nx = -nx;
            ny = -ny;
            nz = -nz;
        }
        Vector3f normal = new Vector3f();
        pose.transformNormal(nx, ny, nz, normal);
        Matrix4f matrix = pose.pose();
        for (int i : order) {
            Vector3f pos = matrix.transformPosition(p[i][0] / 16.0f, p[i][1] / 16.0f, p[i][2] / 16.0f, new Vector3f());
            vertices.addVertex(pos.x(), pos.y(), pos.z(), color,
                    uv[i][0] / texW, uv[i][1] / texH, overlay, light,
                    normal.x(), normal.y(), normal.z());
        }
    }

    static float[] v(float x, float y, float z) {
        return new float[] {x, y, z};
    }

    static float[] t(float u, float v) {
        return new float[] {u, v};
    }
}
