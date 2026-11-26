package dev.yucktheslime.mcabodyshapes.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.yucktheslime.mcabodyshapes.body.BodyShapeAge;
import dev.yucktheslime.mcabodyshapes.config.BodyShapeCommonConfig;
import net.conczin.mca.client.model.PlayerArmorExtendedModel;
import net.conczin.mca.client.model.VillagerEntityBaseModelMCA;
import net.conczin.mca.client.model.VillagerEntityModelMCA;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;

/**
 * Draws the clothed pregnancy belly on the front (-Z) of an adult female
 * villager, parented to the already-posed torso. Static (no physics).
 *
 * <p>Geometry is the v2 Blockbench NPC_Base belly (model_v2.py
 * {@code build_belly}): a smooth rounded loft from just under the chest down to
 * the lower torso, converted to MCA model space (y down from the shoulders).
 *
 * <p>0.6.2: three distinct phases mapped from pregnancy progress {@code p} (bundled in A Trade of
 * Immersion this is TOI's pregnancy progress, see {@code PregnancyProgress}, not MCA's BabyAge)
 * (thirds: phase 1 up to 1/3, phase 2 up to 2/3, phase 3 above). Each phase
 * has its own depth, fullness (width) and height range, and <b>blends smoothly
 * inside its phase</b> but <b>steps</b> at the phase edges, so each phase reads
 * as clearly bigger than the last:
 * <pre>
 *   phase 1  small early bump   depth 0.30 -> 1.00 px
 *   phase 2  rounder mid belly  depth 1.40 -> 1.80 px
 *   phase 3  full late belly    depth 2.10 -> 2.50 px   (maximum)
 * </pre>
 * Peak depth is clamped in code to {@link #MAX_DEPTH} (2.5 px); the common
 * config {@code maxBellyDepth} can only lower it. The bump stays inside the
 * torso width and samples the shirt rows of the outfit's front strip, eased
 * into the span above the belt so the belt cannot smear across it; the top
 * edge is flush with the torso rows.
 */
public final class BellyRenderer {
    /** Hard cap in model pixels forward from the torso front (phase 3 maximum). */
    public static final float MAX_DEPTH = 2.50f;
    /** Phase edges in pregnancy progress (0..1). */
    private static final float PHASE_1_END = 1.0f / 3.0f;
    private static final float PHASE_2_END = 2.0f / 3.0f;
    /** Per phase {start, end}: peak depth (px), fullness 0..1 (width), height scale (vertical extent). */
    private static final float[][] PHASE_DEPTH = {{0.30f, 1.00f}, {1.40f, 1.80f}, {2.10f, 2.50f}};
    private static final float[][] PHASE_FULL = {{0.25f, 0.40f}, {0.60f, 0.75f}, {0.85f, 1.00f}};
    private static final float[][] PHASE_HEIGHT = {{0.66f, 0.74f}, {0.84f, 0.90f}, {0.95f, 1.00f}};
    /** Centre of the v2 profile (y), the height scale pivots here. */
    private static final float Y_CENTER = 8.95f;
    private static final float CLOTHING_INFLATE = 0.07f;
    private static final float JACKET_EXTRA = 0.18f;
    private static final float ARMOR_INFLATE = 0.20f;
    private static final float TEX_64 = 64.0f;
    private static final float TEX_32 = 32.0f;

    /** v2 BELLY_PROFILE (bb y 17.8 .. 12.3) in model space: {y, share of the depth}. */
    private static final float[][] PROFILE = {
            {6.2f, 0.00f}, {7.0f, 0.40f}, {8.0f, 0.76f}, {9.0f, 0.95f},
            {9.9f, 1.00f}, {10.7f, 0.85f}, {11.3f, 0.50f}, {11.7f, 0.00f}
    };
    /** v2 section: |x| / W from the outer edge toward the centre line. */
    private static final float[] SECTION_U = {1.0f, 0.9f, 0.72f, 0.48f, 0.22f};

    private enum UvLayer { INNER, JACKET, ARMOR }

    private static final ThreadLocal<State> CURRENT = ThreadLocal.withInitial(() -> State.HIDDEN);

    private BellyRenderer() {
    }

    /** Pregnancy phase 1..3 for a progress value (0 if none). */
    public static int phaseOf(float progress) {
        if (progress <= 0.02f) {
            return 0;
        }
        return progress <= PHASE_1_END ? 1 : (progress <= PHASE_2_END ? 2 : 3);
    }

    private static float within(float[][] table, int phase, float progress) {
        float lo = phase == 1 ? 0.02f : (phase == 2 ? PHASE_1_END : PHASE_2_END);
        float hi = phase == 1 ? PHASE_1_END : (phase == 2 ? PHASE_2_END : 1.0f);
        float u = Mth.clamp((progress - lo) / (hi - lo), 0.0f, 1.0f);
        return Mth.lerp(u, table[phase - 1][0], table[phase - 1][1]);
    }

    /** Peak depth in px for a progress value, clamped to the hard cap and the config. */
    public static float depthAt(float progress) {
        int phase = phaseOf(progress);
        if (phase == 0) {
            return 0.0f;
        }
        float cap = Math.min(MAX_DEPTH, BodyShapeCommonConfig.maxBellyDepth());
        // A lowered config scales every phase down together (distinct steps stay).
        float scale = cap / MAX_DEPTH;
        return Math.min(cap, within(PHASE_DEPTH, phase, progress) * scale);
    }

    public static void clear() {
        CURRENT.set(State.HIDDEN);
    }

    public static void prepare(LivingEntity entity, float pregnancy, HumanoidModel<?> model) {
        if (!BodyShapeAge.isShapeableAdult(entity)
                || !model.body.visible
                || pregnancy <= 0.02f
                || !SeatRenderer.isFemale(entity)) {
            CURRENT.set(State.HIDDEN);
            return;
        }
        float t = Mth.clamp(pregnancy, 0.0f, 1.0f);
        CURRENT.set(new State(true, 4.0f * model.body.xScale, -2.0f * model.body.zScale, t, entity));
    }

    public static void render(
            HumanoidModel<?> model,
            PoseStack matrices,
            VertexConsumer vertices,
            int light,
            int overlay,
            int color
    ) {
        State state = CURRENT.get();
        if (state == null || !state.visible || !model.body.visible) {
            return;
        }
        if (state.entity != null && !BodyShapeAge.isShapeableAdult(state.entity)) {
            return;
        }

        boolean armorModel = isArmorModel(model);
        if (!armorModel && !LoftMesh.bodyLayer(model)) {
            return;
        }
        boolean hasChestplate = state.entity != null
                && !state.entity.getItemBySlot(EquipmentSlot.CHEST).isEmpty();
        if (armorModel) {
            if (!hasChestplate) {
                return;
            }
        } else if (hasChestplate) {
            return;
        }

        boolean clothingLayer = !armorModel && overlayVisible(model);
        ClothingSeatFit.Info fit = ClothingSeatFit.of(state.entity);
        float inflate = armorModel ? ARMOR_INFLATE : LoftMesh.layerInflate(model, clothingLayer, CLOTHING_INFLATE);
        UvLayer layer = armorModel ? UvLayer.ARMOR : UvLayer.INNER;

        matrices.pushPose();
        applyBodyPoseWithoutScale(model.body, matrices);
        PoseStack.Pose pose = matrices.last();
        draw(state, pose, vertices, light, overlay, color, layer, inflate, fit);
        if (!armorModel && LoftMesh.jacketPass(model, clothingLayer) && fit.jacket()) {
            draw(state, pose, vertices, light, overlay, color, UvLayer.JACKET, inflate + JACKET_EXTRA, fit);
        }
        matrices.popPose();
    }

    private static void draw(
            State st, PoseStack.Pose pose, VertexConsumer vc,
            int light, int overlay, int color,
            UvLayer layer, float inf, ClothingSeatFit.Info fit
    ) {
        float texH = layer == UvLayer.ARMOR ? TEX_32 : TEX_64;
        float baseV = layer == UvLayer.JACKET ? 36.0f : 20.0f;
        float hw = st.torsoHalf + inf;
        float sx = hw / 4.0f;
        float zf = st.torsoFront - inf;
        float zFlush = zf + 0.02f;
        float zIn = st.torsoFront + 0.80f;

        float p = st.progress;
        int phase = phaseOf(p);
        if (phase == 0) {
            return;
        }
        float depth = depthAt(p);
        if (depth < 0.02f) {
            return;
        }
        float full = within(PHASE_FULL, phase, p);
        float vScale = within(PHASE_HEIGHT, phase, p);

        int rings = PROFILE.length;
        float[] ys = new float[rings];
        for (int k = 0; k < rings; k++) {
            ys[k] = Y_CENTER + (PROFILE[k][0] - Y_CENTER) * vScale;
        }
        float yTop = ys[0];
        float yBot = ys[rings - 1];

        // Shirt rows only: ease the bump into the span above the belt so the
        // belt and pants cannot smear across it. The top edge keeps the
        // identity mapping (flush with the torso rows). Default / undetected
        // shirtEndY is 8, which is still in the shirt, never 12 (identity).
        float shirtEnd = layer == UvLayer.ARMOR ? 12.0f
                : (layer == UvLayer.JACKET ? fit.jacketShirtEndY() : fit.shirtEndY());
        float limit = Mth.clamp(shirtEnd - 0.45f, yTop + 1.0f, 11.95f);
        float rowScale = Math.min(1.0f, (limit - yTop) / (yBot - yTop));

        int n = 2 * (SECTION_U.length + 1) + 1;
        float[][][] ring = new float[rings][n][];
        float wMax = hw - 0.10f;
        for (int k = 0; k < rings; k++) {
            float f = PROFILE[k][1];
            float b = depth * f;
            float w = Math.min(wMax, (2.80f + 0.50f * full + 0.60f * full * f) * sx);
            int i = 0;
            ring[k][i++] = new float[] {-w, zIn};
            for (float a : SECTION_U) {
                float kz = (float) Math.sqrt(Math.max(0.0, 1.0 - Math.pow(a, 2.2)));
                ring[k][i++] = new float[] {-w * a, zFlush - kz * b};
            }
            ring[k][i++] = new float[] {0.0f, zFlush - b};
            for (int j = SECTION_U.length - 1; j >= 0; j--) {
                float a = SECTION_U[j];
                float kz = (float) Math.sqrt(Math.max(0.0, 1.0 - Math.pow(a, 2.2)));
                ring[k][i++] = new float[] {w * a, zFlush - kz * b};
            }
            ring[k][i] = new float[] {w, zIn};
        }
        final float fRowScale = rowScale;
        final float fTop = yTop;
        LoftMesh.loft(pose, vc, light, overlay, color, TEX_64, texH, ys, ring,
                (x, y, z, nx, ny, nz, fx, fy, fz) -> LoftMesh.torsoUv(
                        x, fTop + (y - fTop) * fRowScale, z, nx, nz, fz,
                        hw, zf, -zf, baseV, 0.0f, limit));
    }

    private static boolean isArmorModel(HumanoidModel<?> model) {
        if (model instanceof PlayerArmorExtendedModel<?>) {
            return true;
        }
        return model instanceof VillagerEntityBaseModelMCA<?>
                && !(model instanceof VillagerEntityModelMCA<?>);
    }

    private static boolean overlayVisible(HumanoidModel<?> model) {
        if (model instanceof VillagerEntityModelMCA<?> villager) {
            return villager.bodyWear.visible;
        }
        if (model instanceof PlayerModel<?> player) {
            return player.jacket.visible;
        }
        return true;
    }

    private static void applyBodyPoseWithoutScale(ModelPart body, PoseStack matrices) {
        matrices.translate(body.x / 16.0f, body.y / 16.0f, body.z / 16.0f);
        if (body.xRot != 0.0f || body.yRot != 0.0f || body.zRot != 0.0f) {
            matrices.mulPose(new Quaternionf().rotationZYX(body.zRot, body.yRot, body.xRot));
        }
    }

    private record State(boolean visible, float torsoHalf, float torsoFront, float progress, LivingEntity entity) {
        private static final State HIDDEN = new State(false, 4.0f, -2.0f, 0.0f, null);
    }
}
