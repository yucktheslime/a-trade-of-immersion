package dev.yucktheslime.mcabodyshapes.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.yucktheslime.mcabodyshapes.body.BodyShape;
import dev.yucktheslime.mcabodyshapes.body.BodyShapeAge;
import net.conczin.mca.client.model.VillagerEntityModelMCA;
import net.conczin.mca.client.model.PlayerArmorExtendedModel;
import net.conczin.mca.client.model.VillagerEntityBaseModelMCA;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;

/**
 * Adds a modest, blocky upper-thigh shell on each leg, parented to the
 * already-posed leg. 0.6.0: ported from the v2 Blockbench NPC_Base thighs, a
 * chamfered ring loft that starts just inside the torso (no ledge at the
 * hip), is fullest about 1.6 px below the hip (mostly on the outer side and
 * the back), and tapers back to the vanilla leg by the knee. The calf and
 * knee stay at their MCA size.
 *
 * <p>Reads the thigh thickness slider only. At 0 nothing is drawn, so the
 * legs stay identical to vanilla MCA. Extra width is capped at one model
 * pixel per side. The shell samples the outfit's own upper-leg UVs, so the
 * extra volume stays clothed. The adult-only gate is the same
 * {@link BodyShapeAge} check used everywhere.
 */
public final class ThighRenderer {
    /** Below this per-side extra nothing is drawn (vanilla legs). */
    public static final float MIN_EXTRA = 0.04f;
    private static final float CLOTHING_INFLATE = 0.10f;
    private static final float ARMOR_INFLATE = 0.16f;
    private static final float TEX_64 = 64.0f;
    private static final float TEX_32 = 32.0f;

    private static final ThreadLocal<State> CURRENT = ThreadLocal.withInitial(() -> State.HIDDEN);

    private ThighRenderer() {
    }

    public static void clear() {
        CURRENT.set(State.HIDDEN);
    }

    public static void prepare(LivingEntity entity, BodyShape shape, HumanoidModel<?> model) {
        if (!BodyShapeAge.isShapeableAdult(entity)
                || !(model.leftLeg.visible || model.rightLeg.visible)) {
            CURRENT.set(State.HIDDEN);
            return;
        }
        float extra = BodyShapeApplier.thighExtraPerSide(shape);
        if (extra < MIN_EXTRA) {
            CURRENT.set(State.HIDDEN);
            return;
        }
        CURRENT.set(new State(true, extra, entity));
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
        if (state == null || !state.visible || state.entity == null) {
            return;
        }
        if (!BodyShapeAge.isShapeableAdult(state.entity)) {
            return;
        }

        boolean armorModel = isArmorModel(model);
        if (!armorModel && !LoftMesh.bodyLayer(model)) {
            return;
        }
        boolean hasLeggings = !state.entity.getItemBySlot(EquipmentSlot.LEGS).isEmpty();
        if (armorModel) {
            if (!hasLeggings || !(model.leftLeg.visible || model.rightLeg.visible)) {
                return;
            }
        } else {
            if (hasLeggings) {
                return;
            }
        }

        boolean clothingLayer = !armorModel && overlayVisible(model);
        float inflate = armorModel ? ARMOR_INFLATE : LoftMesh.layerInflate(model, clothingLayer, CLOTHING_INFLATE);
        float texH = armorModel ? TEX_32 : TEX_64;
        float extra = state.extra;

        if (model.rightLeg.visible) {
            float texU = 0.0f;
            float texV = 16.0f;
            renderLeg(model.rightLeg, matrices, vertices, light, overlay, color,
                    extra, inflate, texU, texV, texH, -1.0f);
        }
        if (model.leftLeg.visible) {
            // 64x64 left leg is at (16, 48); 64x32 armor reuses (0, 16).
            float texU = armorModel ? 0.0f : 16.0f;
            float texV = armorModel ? 16.0f : 48.0f;
            renderLeg(model.leftLeg, matrices, vertices, light, overlay, color,
                    extra, inflate, texU, texV, texH, 1.0f);
        }
    }

    /**
     * v2 THIGH_PROFILE in leg-local px below the hip: ring heights and how
     * much of the extra each ring gets. The first ring starts just inside the
     * torso so the thigh rises out of the hip without a ledge.
     */
    private static final float[] RING_Y = {-0.4f, 0.4f, 1.6f, 3.4f, 5.2f, 7.0f, 7.8f};
    private static final float[] RING_E = {0.0f, 0.85f, 1.0f, 0.75f, 0.45f, 0.15f, 0.0f};
    /** v2 per-face shares of the extra: outer side, inner side, front, back. */
    private static final float OUTER_SHARE = 1.0f;
    private static final float INNER_SHARE = 0.10f;
    private static final float FRONT_SHARE = 0.50f;
    private static final float BACK_SHARE = 0.80f;

    /** Extra (per side) at leg-local height {@code y}, following the rings. */
    public static float extraAt(float y, float extra) {
        if (extra < MIN_EXTRA || y <= RING_Y[0] || y >= RING_Y[RING_Y.length - 1]) {
            return 0.0f;
        }
        for (int i = 0; i < RING_Y.length - 1; i++) {
            if (y <= RING_Y[i + 1]) {
                float f = (y - RING_Y[i]) / (RING_Y[i + 1] - RING_Y[i]);
                return extra * (RING_E[i] + (RING_E[i + 1] - RING_E[i]) * f);
            }
        }
        return 0.0f;
    }

    /**
     * Lofted upper thigh (v2 build_thighs): a chamfered ring shell that is
     * fullest just below the hip, widest on the outer side, and tapers back
     * to the vanilla leg by the knee. UVs are the leg's own side / front /
     * back strips at the matching rows.
     */
    private static void renderLeg(
            ModelPart leg,
            PoseStack matrices,
            VertexConsumer vertices,
            int light,
            int overlay,
            int color,
            float extra,
            float inflate,
            float texU,
            float texV,
            float texH,
            float outerSign
    ) {
        matrices.pushPose();
        applyPartPoseWithoutScale(leg, matrices);
        PoseStack.Pose pose = matrices.last();

        float hx = 2.0f * leg.xScale + inflate;
        float hz = 2.0f * leg.zScale + inflate;
        int rings = RING_Y.length;
        float[][][] ring = new float[rings][][];
        for (int i = 0; i < rings; i++) {
            float e = extra * RING_E[i];
            float xo = hx - 0.03f + OUTER_SHARE * e;
            float xi = hx - 0.03f + INNER_SHARE * e;
            float xmin = outerSign < 0 ? -xo : -xi;
            float xmax = outerSign < 0 ? xi : xo;
            float c = 0.45f + 0.45f * e;
            ring[i] = LoftMesh.octagon(xmin, xmax, -(hz - 0.03f) - FRONT_SHARE * e,
                    (hz - 0.03f) + BACK_SHARE * e, c, c);
        }
        LoftMesh.loft(pose, vertices, light, overlay, color, TEX_64, texH, RING_Y, ring,
                (x, y, z, nx, ny, nz, fx, fy, fz) -> LoftMesh.legUv(x, y, z, nx, nz, fz, hx, hz, texU, texV));
        matrices.popPose();
    }

    private static void applyPartPoseWithoutScale(ModelPart part, PoseStack matrices) {
        matrices.translate(part.x / 16.0f, part.y / 16.0f, part.z / 16.0f);
        if (part.xRot != 0.0f || part.yRot != 0.0f || part.zRot != 0.0f) {
            matrices.mulPose(new Quaternionf().rotationZYX(part.zRot, part.yRot, part.xRot));
        }
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
            return player.leftPants.visible || player.rightPants.visible;
        }
        return true;
    }

    private record State(boolean visible, float extra, LivingEntity entity) {
        private static final State HIDDEN = new State(false, 0.0f, null);
    }
}
