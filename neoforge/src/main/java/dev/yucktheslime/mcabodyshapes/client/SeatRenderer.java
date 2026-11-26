package dev.yucktheslime.mcabodyshapes.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.yucktheslime.mcabodyshapes.body.BodyShape;
import dev.yucktheslime.mcabodyshapes.body.BodyShapeAge;
import net.conczin.mca.client.model.CommonVillagerModel;
import net.conczin.mca.client.model.PlayerArmorExtendedModel;
import net.conczin.mca.client.model.VillagerEntityBaseModelMCA;
import net.conczin.mca.client.model.VillagerEntityModelMCA;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;


/**
 * Lower-torso shaping, parented to the posed torso. Static (no physics,
 * jiggle, or motion).
 *
 * <p>0.6.0: geometry is ported from the v2 Blockbench NPC_Base model
 * (model_v2.py), mapped into MCA model space (y down from the shoulders):
 * <ul>
 *   <li><b>Hip width</b> is a rounded, chamfered skirt ring around the lower
 *   torso and leg tops: flush at the waist ({@code y = 5}), easing out to the
 *   hip crest at the leg line ({@code y = 12}), then tucking back in over the
 *   upper legs by {@code y = 14.8}. No narrow waist is added; capped at +1 px
 *   per side.</li>
 *   <li><b>Butt Size</b> is one joined, rounded rear shell (no cleft or
 *   split): flush on the lower back ({@code y = 6.4}), a gentle slope out to
 *   a short flat crest just above and below the leg line, and an angled tuck
 *   into the thigh tops ({@code y = 14.7}). The sides wrap slightly past the
 *   torso (up to +0.9 px per side). Peak depth stays capped at
 *   {@link #MAX_DEPTH_SCALE}.</li>
 * </ul>
 * Above the leg line the shells sample the torso strips of the outfit; below
 * it they sample the outfit's upper-leg strips (so pants, pockets and seams
 * carry on), except for skirts, which keep the skirt's last torso row.
 */
public final class SeatRenderer {
    private static final float CLOTHING_INFLATE = 0.07f;
    private static final float SKIRT_INFLATE = 0.18f;
    private static final float JACKET_EXTRA = 0.18f;
    private static final float ARMOR_INFLATE = 0.20f;
    /** Hard cap from 0.4.12: peak rear depth scale 1.55 (x2 px = 3.10 px). */
    public static final float MAX_DEPTH_SCALE = 1.55f;
    /** Hard cap: hip flare per side (px). */
    private static final float MAX_HIP_EXTRA = 1.0f;
    /** v2 seat side wrap at full Butt Size (px per side). */
    private static final float SEAT_WRAP = 0.9f;
    private static final float TEX_64 = 64.0f;
    private static final float TEX_32 = 32.0f;
    private static final float LEG_LINE = 12.0f;

    /** v2 HIP_PROFILE in model space: {y, share of the hip extra}. */
    private static final float[][] HIP_PROFILE = {
            {5.0f, 0.00f}, {6.5f, 0.18f}, {8.0f, 0.45f}, {9.5f, 0.75f}, {11.0f, 0.95f},
            {12.0f, 1.00f}, {13.0f, 0.90f}, {14.0f, 0.55f}, {14.8f, 0.00f}
    };
    /** v2 SEAT_PROFILE in model space: {y, share of the rear depth}. */
    private static final float[][] SEAT_PROFILE = {
            {6.4f, 0.00f}, {7.4f, 0.12f}, {8.4f, 0.32f}, {9.4f, 0.58f}, {10.4f, 0.82f},
            {11.4f, 0.97f}, {12.2f, 1.00f}, {13.0f, 0.96f}, {13.7f, 0.70f}, {14.3f, 0.32f},
            {14.7f, 0.00f}
    };
    /** v2 seat section: |x| / W across one half, outer edge to centre line. */
    private static final float[] SEAT_U = {1.0f, 0.93f, 0.78f, 0.55f, 0.28f, 0.0f};

    private enum UvLayer { INNER, JACKET, ARMOR }

    private static final ThreadLocal<State> CURRENT = ThreadLocal.withInitial(() -> State.HIDDEN);

    private SeatRenderer() {
    }

    public static void clear() {
        CURRENT.set(State.HIDDEN);
    }

    public static void prepare(LivingEntity entity, BodyShape shape, float pregnancy, HumanoidModel<?> model) {
        if (!BodyShapeAge.isShapeableAdult(entity) || !model.body.visible) {
            CURRENT.set(State.HIDDEN);
            return;
        }
        boolean female = isFemale(entity);

        float depthScale = Math.min(MAX_DEPTH_SCALE, BodyShapeApplier.rearDepthScale(shape, female));
        float depthPx = 2.0f * depthScale;
        float hipExtra = Mth.clamp(BodyShapeApplier.hipExtraPerSide(shape), 0.0f, MAX_HIP_EXTRA);

        boolean seat = shape.seat() > 0.02f && depthPx >= 0.12f;
        boolean hips = hipExtra >= 0.04f;
        if (!seat && !hips) {
            CURRENT.set(State.HIDDEN);
            return;
        }
        CURRENT.set(new State(
                true, seat, hips,
                4.0f * model.body.xScale, 2.0f * model.body.zScale, -2.0f * model.body.zScale,
                hipExtra,
                Math.abs(model.leftLeg.x), 2.0f * model.leftLeg.xScale, 2.0f * model.leftLeg.zScale,
                depthPx, entity
        ));
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
        if (state == null || !state.visible) {
            return;
        }
        if (state.entity != null && !BodyShapeAge.isShapeableAdult(state.entity)) {
            return;
        }

        boolean armorModel = isArmorModel(model);
        if (!armorModel && !LoftMesh.bodyLayer(model)) {
            return;
        }
        boolean hasLeggings = state.entity != null
                && !state.entity.getItemBySlot(EquipmentSlot.LEGS).isEmpty();
        if (armorModel) {
            if (!hasLeggings || !(model.leftLeg.visible || model.rightLeg.visible)) {
                return;
            }
        } else {
            if (!model.body.visible || hasLeggings) {
                return;
            }
        }

        boolean clothingLayer = !armorModel && overlayVisible(model);
        ClothingSeatFit.Info fit = ClothingSeatFit.of(state.entity);
        float inflate;
        UvLayer layer;
        if (armorModel) {
            inflate = ARMOR_INFLATE;
            layer = UvLayer.ARMOR;
        } else {
            inflate = LoftMesh.layerInflate(model, clothingLayer, fit.skirt() ? SKIRT_INFLATE : CLOTHING_INFLATE);
            layer = UvLayer.INNER;
        }

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
        float clamp = layer == UvLayer.ARMOR ? 12.0f
                : (layer == UvLayer.JACKET ? fit.jacketClampY() : fit.clampY());
        float lastRow = Mth.clamp(Math.min(12.0f, clamp), 1.0f, 12.0f) - 0.05f;
        // Leg strips below the leg line (pants / pockets carry on); skirts keep the skirt row.
        boolean legTex = layer == UvLayer.ARMOR || !fit.skirt();
        float rU, rV, lU, lV;
        switch (layer) {
            case ARMOR -> { rU = 0.0f; rV = 16.0f; lU = 0.0f; lV = 16.0f; }
            case JACKET -> { rU = 0.0f; rV = 32.0f; lU = 0.0f; lV = 48.0f; }
            default -> { rU = 0.0f; rV = 16.0f; lU = 16.0f; lV = 48.0f; }
        }
        float hw = st.torsoHalf + inf;
        float zf = st.torsoFront - inf;
        float zb = st.torsoBack + inf;
        float legHx = st.legHalf + inf;
        float legHz = st.legDepth + inf;
        LoftMesh.Uv uv = (x, y, z, nx, ny, nz, fx, fy, fz) -> {
            if (legTex && fy > LEG_LINE) {
                boolean left = fx > 0.0f;
                float lx = x - (left ? st.legCenter : -st.legCenter);
                return LoftMesh.legUv(lx, y - LEG_LINE, z, nx, nz, fz, legHx, legHz,
                        left ? lU : rU, left ? lV : rV);
            }
            return LoftMesh.torsoUv(x, y, z, nx, nz, fz, hw, zf, zb, baseV, 0.0f, lastRow);
        };
        if (st.hips) {
            drawHips(st, pose, vc, light, overlay, color, texH, inf, uv);
        }
        if (st.seat) {
            drawSeat(st, pose, vc, light, overlay, color, texH, inf, uv);
        }
    }

    /** v2 build_hips: chamfered ring (with centre-line points so no face crosses x = 0). */
    private static void drawHips(
            State st, PoseStack.Pose pose, VertexConsumer vc,
            int light, int overlay, int color, float texH, float inf, LoftMesh.Uv uv
    ) {
        int rings = HIP_PROFILE.length;
        float[] ys = new float[rings];
        float[][][] ring = new float[rings][][];
        float legOuter = st.legCenter + st.legHalf;
        float cap = st.torsoHalf + MAX_HIP_EXTRA;
        for (int k = 0; k < rings; k++) {
            float y = HIP_PROFILE[k][0];
            float e = st.hipExtra * HIP_PROFILE[k][1];
            float x = y < LEG_LINE - 0.05f
                    ? (st.torsoHalf - 0.07f) + 1.07f * e
                    : Math.min(cap, (legOuter - 0.04f) + 1.14f * e);
            x = Math.min(x, cap) + inf;
            float zmin = st.torsoFront + 0.06f - 0.25f * e - inf;
            float zmax = st.torsoBack - 0.06f + 0.45f * e + inf;
            float[][] o = LoftMesh.octagon(-x, x, zmin, zmax, 0.6f + 0.7f * e, 0.5f + 0.5f * e);
            ys[k] = y;
            ring[k] = new float[][] {
                    o[0], {0.0f, zmin}, o[1], o[2], o[3], o[4], {0.0f, zmax}, o[5], o[6], o[7]
            };
        }
        LoftMesh.loft(pose, vc, light, overlay, color, TEX_64, texH, ys, ring, uv);
    }

    /** v2 build_seat: one joined elliptical rear shell, no cleft. */
    private static void drawSeat(
            State st, PoseStack.Pose pose, VertexConsumer vc,
            int light, int overlay, int color, float texH, float inf, LoftMesh.Uv uv
    ) {
        float s = Mth.clamp(st.depth / (2.0f * MAX_DEPTH_SCALE), 0.0f, 1.0f);
        float zIn = st.torsoBack - 1.2f;
        float zBack = st.torsoBack - 0.02f + inf;
        int rings = SEAT_PROFILE.length;
        int half = SEAT_U.length + 1;
        int n = 2 * half - 1;
        float[] ys = new float[rings];
        float[][][] ring = new float[rings][n][];
        for (int k = 0; k < rings; k++) {
            float f = SEAT_PROFILE[k][1];
            float q = st.depth * f;
            float w = (st.torsoHalf - 0.07f) + SEAT_WRAP * s * f + inf;
            float[][] h = new float[half][];
            h[0] = new float[] {w, zIn};
            for (int i = 0; i < SEAT_U.length; i++) {
                float a = SEAT_U[i];
                float kz = (float) Math.sqrt(Math.max(0.0, 1.0 - Math.pow(a, 2.6)));
                h[i + 1] = new float[] {w * a, zBack + kz * q};
            }
            for (int i = 0; i < half; i++) {
                ring[k][i] = h[i];
            }
            for (int i = 0; i < half - 1; i++) {
                float[] m = h[half - 2 - i];
                ring[k][half + i] = new float[] {-m[0], m[1]};
            }
            ys[k] = SEAT_PROFILE[k][0];
        }
        LoftMesh.loft(pose, vc, light, overlay, color, TEX_64, texH, ys, ring, uv);
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

    static boolean isFemale(LivingEntity entity) {
        VillagerLike<?> villager;
        if (entity instanceof VillagerLike<?> typed) {
            villager = typed;
        } else {
            villager = CommonVillagerModel.getVillager(entity);
        }
        return villager != null
                && villager.getGenetics() != null
                && villager.getGenetics().getGender() == Gender.FEMALE;
    }

    private record State(
            boolean visible,
            boolean seat,
            boolean hips,
            float torsoHalf,
            float torsoBack,
            float torsoFront,
            float hipExtra,
            float legCenter,
            float legHalf,
            float legDepth,
            float depth,
            LivingEntity entity
    ) {
        private static final State HIDDEN = new State(
                false, false, false,
                4.0f, 2.0f, -2.0f, 0.0f,
                1.9f, 2.0f, 2.0f, 0.0f,
                null);
    }
}
