package dev.yucktheslime.mcabodyshapes.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * 0.6.0: emits a closed, flat-shaded loft between section rings, the same
 * construction as the v2 Blockbench NPC_Base meshes (model_v2.py). Each ring
 * is a closed polygon of {x, z} points at one model-space height y (Y down).
 * Side quads join ring k to ring k+1 point by point; the two end rings get a
 * fan cap. Normals face away from the ring centroid, and each face picks its
 * texture from {@link Uv} so the shape keeps following the outfit/skin.
 */
final class LoftMesh {
    private LoftMesh() {
    }

    /**
     * Texture lookup for one vertex (x, y, z) of a face with outward normal
     * (nx, ny, nz) and face center (fx, fy, fz). The texture region is picked
     * per face (from the normal / center) so a face never straddles two strips.
     */
    interface Uv {
        float[] at(float x, float y, float z, float nx, float ny, float nz, float fx, float fy, float fz);
    }

    static void loft(
            PoseStack.Pose pose, VertexConsumer vc,
            int light, int overlay, int color,
            float texW, float texH,
            float[] ys, float[][][] rings, Uv uv
    ) {
        int n = rings[0].length;
        for (int k = 0; k < ys.length - 1; k++) {
            float cx = 0.0f, cz = 0.0f;
            for (int i = 0; i < n; i++) {
                cx += rings[k][i][0] + rings[k + 1][i][0];
                cz += rings[k][i][1] + rings[k + 1][i][1];
            }
            cx /= 2.0f * n;
            cz /= 2.0f * n;
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                float[][] p = {
                        {rings[k][i][0], ys[k], rings[k][i][1]},
                        {rings[k][j][0], ys[k], rings[k][j][1]},
                        {rings[k + 1][j][0], ys[k + 1], rings[k + 1][j][1]},
                        {rings[k + 1][i][0], ys[k + 1], rings[k + 1][i][1]}
                };
                float mx = 0.25f * (p[0][0] + p[1][0] + p[2][0] + p[3][0]);
                float mz = 0.25f * (p[0][2] + p[1][2] + p[2][2] + p[3][2]);
                emit(pose, vc, light, overlay, color, texW, texH, p, uv, mx - cx, 0.0f, mz - cz);
            }
        }
        cap(pose, vc, light, overlay, color, texW, texH, ys[0], rings[0], uv, -1.0f);
        cap(pose, vc, light, overlay, color, texW, texH, ys[ys.length - 1], rings[ys.length - 1], uv, 1.0f);
    }

    private static void cap(
            PoseStack.Pose pose, VertexConsumer vc, int light, int overlay, int color,
            float texW, float texH, float y, float[][] ring, Uv uv, float dirY
    ) {
        int n = ring.length;
        float cx = 0.0f, cz = 0.0f;
        for (float[] q : ring) {
            cx += q[0];
            cz += q[1];
        }
        cx /= n;
        cz /= n;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            float[][] p = {{ring[i][0], y, ring[i][1]}, {ring[j][0], y, ring[j][1]}, {cx, y, cz}, {cx, y, cz}};
            emit(pose, vc, light, overlay, color, texW, texH, p, uv, 0.0f, dirY, 0.0f);
        }
    }

    private static void emit(
            PoseStack.Pose pose, VertexConsumer vc, int light, int overlay, int color,
            float texW, float texH, float[][] p, Uv uv, float hx, float hy, float hz
    ) {
        // Face normal from both diagonals (robust for collapsed corners).
        float ax = p[2][0] - p[0][0], ay = p[2][1] - p[0][1], az = p[2][2] - p[0][2];
        float bx = p[3][0] - p[1][0], by = p[3][1] - p[1][1], bz = p[3][2] - p[1][2];
        float nx = ay * bz - az * by;
        float ny = az * bx - ax * bz;
        float nz = ax * by - ay * bx;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1.0e-5f) {
            return;
        }
        nx /= len;
        ny /= len;
        nz /= len;
        if (nx * hx + ny * hy + nz * hz < 0.0f) {
            nx = -nx;
            ny = -ny;
            nz = -nz;
        }
        float fx = 0.25f * (p[0][0] + p[1][0] + p[2][0] + p[3][0]);
        float fy = 0.25f * (p[0][1] + p[1][1] + p[2][1] + p[3][1]);
        float fz = 0.25f * (p[0][2] + p[1][2] + p[2][2] + p[3][2]);
        float[][] t = new float[4][];
        for (int i = 0; i < 4; i++) {
            t[i] = uv.at(p[i][0], p[i][1], p[i][2], nx, ny, nz, fx, fy, fz);
        }
        ProfileMesh.quad(pose, vc, light, overlay, color, texW, texH, p, t, nx, ny, nz);
    }

    /** 8-point chamfered rectangle (same as v2 octagon()). */
    static float[][] octagon(float xmin, float xmax, float zmin, float zmax, float cx, float cz) {
        cx = Math.min(cx, (xmax - xmin) / 2.0f - 1.0e-3f);
        cz = Math.min(cz, (zmax - zmin) / 2.0f - 1.0e-3f);
        return new float[][] {
                {xmin + cx, zmin}, {xmax - cx, zmin}, {xmax, zmin + cz}, {xmax, zmax - cz},
                {xmax - cx, zmax}, {xmin + cx, zmax}, {xmin, zmax - cz}, {xmin, zmin + cz}
        };
    }

    /**
     * Torso texture lookup (64x64 layout, body at u 16..40): front/back strips
     * by x, side strips by z, top/bottom-facing faces reuse the front or back
     * strip. Rows are {@code baseV + y}, clamped to {@code [vMinY, vMaxY]}.
     */
    static float[] torsoUv(float x, float y, float z, float nx, float nz, float fz,
                           float hw, float zFront, float zBack, float baseV, float vMinY, float vMaxY) {
        float xn = clamp(x / hw, -1.0f, 1.0f);
        // zn: -1 at the front face, +1 at the back face
        float zn = z < 0.0f ? -clamp(z / zFront, 0.0f, 1.0f) : clamp(z / zBack, 0.0f, 1.0f);
        float ax = Math.abs(nx), az = Math.abs(nz);
        float u;
        if (ax > az && ax > 0.35f) {
            u = nx < 0.0f ? strip(18.0f - 2.0f * zn, 16.0f, 20.0f) : strip(30.0f + 2.0f * zn, 28.0f, 32.0f);
        } else if (nz > 0.0f || (az <= 0.35f && fz > 0.0f)) {
            u = strip(36.0f - 4.0f * xn, 32.0f, 40.0f);
        } else {
            u = strip(24.0f + 4.0f * xn, 20.0f, 28.0f);
        }
        return new float[] {u, baseV + clamp(y, Math.max(vMinY, UV_EPS), vMaxY)};
    }

    /**
     * Leg texture lookup for one 4x12x4 leg whose texture starts at
     * (texU, texV). {@code lx}/{@code lz} are leg-local, {@code ly} is the row
     * below the hip.
     */
    static float[] legUv(float lx, float ly, float lz, float nx, float nz, float fz,
                         float hx, float hz, float texU, float texV) {
        float xn = clamp(lx / hx, -1.0f, 1.0f);
        float zn = clamp(lz / hz, -1.0f, 1.0f);
        float ax = Math.abs(nx), az = Math.abs(nz);
        float u;
        if (ax > az && ax > 0.35f) {
            u = nx < 0.0f ? strip(texU + 2.0f - 2.0f * zn, texU, texU + 4.0f)
                    : strip(texU + 10.0f + 2.0f * zn, texU + 8.0f, texU + 12.0f);
        } else if (nz > 0.0f || (az <= 0.35f && fz > 0.0f)) {
            u = strip(texU + 14.0f - 2.0f * xn, texU + 12.0f, texU + 16.0f);
        } else {
            u = strip(texU + 6.0f + 2.0f * xn, texU + 4.0f, texU + 8.0f);
        }
        return new float[] {u, texV + 4.0f + clamp(ly, UV_EPS, 12.0f - UV_EPS)};
    }

    private static final net.minecraft.util.RandomSource CUBE_PICK = net.minecraft.util.RandomSource.create(0L);
    private static final java.util.Map<net.minecraft.client.model.geom.ModelPart, Float> DILATION =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /**
     * MCA renders the villager model once per layer (skin 0, face 0.01,
     * clothing 0.0625, hair 0.125 px of cube dilation). Returns that layer's
     * dilation, measured once from the body cube's baked vertices, so each
     * layer's shells keep the same stacking order as MCA's own cubes instead
     * of sharing one surface (which z-fights wherever two layer textures both
     * have pixels). Returns -1 for models that are not MCA villager layers.
     */
    static float layerDilation(net.minecraft.client.model.HumanoidModel<?> model) {
        if (!(model instanceof net.conczin.mca.client.model.VillagerEntityModelMCA<?>)) {
            return -1.0f;
        }
        net.minecraft.client.model.geom.ModelPart body = model.body;
        Float cached = DILATION.get(body);
        if (cached != null) {
            return cached;
        }
        float d;
        try {
            net.minecraft.client.model.geom.ModelPart.Cube cube = body.getRandomCube(CUBE_PICK);
            float[] min = {Float.MAX_VALUE};
            VertexConsumer probe = new VertexConsumer() {
                @Override
                public VertexConsumer addVertex(float x, float y, float z) {
                    min[0] = Math.min(min[0], x);
                    return this;
                }

                @Override
                public VertexConsumer setColor(int r, int g, int b, int a) {
                    return this;
                }

                @Override
                public VertexConsumer setUv(float u, float v) {
                    return this;
                }

                @Override
                public VertexConsumer setUv1(int u, int v) {
                    return this;
                }

                @Override
                public VertexConsumer setUv2(int u, int v) {
                    return this;
                }

                @Override
                public VertexConsumer setNormal(float x, float y, float z) {
                    return this;
                }
            };
            cube.compile(new PoseStack().last(), probe, 0, 0, -1);
            d = min[0] == Float.MAX_VALUE ? 0.0f : Math.max(0.0f, cube.minX - min[0] * 16.0f);
        } catch (RuntimeException e) {
            d = 0.0f;
        }
        DILATION.put(body, d);
        return d;
    }

    /**
     * Inflate for one render pass: MCA villager layers scale {@code base}
     * (tuned for the 0.0625 px clothing layer) by the layer's dilation;
     * players and other models use {@code base} when their overlay is shown.
     */
    static float layerInflate(net.minecraft.client.model.HumanoidModel<?> model, boolean overlayShown, float base) {
        float d = layerDilation(model);
        if (d < 0.0f) {
            return overlayShown ? base : 0.0f;
        }
        return base * d / 0.0625f;
    }

    /**
     * Only MCA's skin (0) and clothing (0.0625) layers carry body shells. The
     * face (0.01) and hair (0.125) layers would otherwise stack extra shells
     * a hair's width away and z-fight / speckle wherever their textures have
     * stray pixels in the body or leg strips.
     */
    static boolean bodyLayer(net.minecraft.client.model.HumanoidModel<?> model) {
        float d = layerDilation(model);
        return d < 0.0f || d < 0.005f || (d > 0.03f && d < 0.1f);
    }

    /** The jacket/overlay pass is drawn for the clothing layer only (not the hair layer). */
    static boolean jacketPass(net.minecraft.client.model.HumanoidModel<?> model, boolean overlayShown) {
        float d = layerDilation(model);
        return overlayShown && (d < 0.0f || (d > 0.03f && d < 0.1f));
    }

    /**
     * Keeps a texel coordinate a little inside its strip: a UV exactly on a
     * strip edge (e.g. u = 16 where the seat wraps past the torso) jitters
     * across the texel boundary during interpolation and speckles with the
     * neighbouring strip's pixels.
     */
    private static final float UV_EPS = 0.06f;

    private static float strip(float u, float lo, float hi) {
        return clamp(u, lo + UV_EPS, hi - UV_EPS);
    }

    static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
