package dev.yucktheslime.mcabodyshapes.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.yucktheslime.mcabodyshapes.body.BodyShape;
import dev.yucktheslime.mcabodyshapes.body.BodyShapeAge;
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

import static dev.yucktheslime.mcabodyshapes.client.ProfileMesh.t;
import static dev.yucktheslime.mcabodyshapes.client.ProfileMesh.v;

/**
 * Draws the clothed <b>Bust Size</b> protrusion on adult female figures,
 * parented to the posed torso. From TOI 0.6.0 it jiggles ({@link BodyPhysics}): a small offset
 * and tilt of the same mesh about its collar line, never a size change. This still runs when
 * MCA: Female Gender Physics is loaded (its breast gene is kept at 0 so there is no second bust).
 *
 * <p>0.6.0: geometry is the v2 Blockbench NPC_Base chest: two mirrored,
 * angled chest blocks just below the shoulders, lofted from the same
 * profile (flush at the collar, gentle slope out to the crest, short angled
 * underside back into the ribcage) with a shallow recess at the centre line
 * so the blocks read as a pair without any gap. Shirt UVs follow the torso
 * front and side strips at every value. At Bust Size 0 nothing is drawn (identical to vanilla MCA), and
 * peak forward depth is capped at {@link #MAX_DEPTH} (strictly within MCA's
 * existing chest maximum).
 */
public final class BustRenderer {
    /** Hard cap in model pixels forward from the torso front (existing MCA max is ~2.09px). */
    public static final float MAX_DEPTH = 1.95f;
    private static final float CLOTHING_INFLATE = 0.07f;
    private static final float JACKET_EXTRA = 0.18f;
    private static final float ARMOR_INFLATE = 0.20f;
    private static final float TEX_64 = 64.0f;
    private static final float TEX_32 = 32.0f;


    private enum UvLayer { INNER, JACKET, ARMOR }

    private static final ThreadLocal<State> CURRENT = ThreadLocal.withInitial(() -> State.HIDDEN);

    private BustRenderer() {
    }

    public static void clear() {
        CURRENT.set(State.HIDDEN);
    }

    public static void prepare(LivingEntity entity, BodyShape shape, HumanoidModel<?> model) {
        if (!BodyShapeAge.isShapeableAdult(entity)
                || !model.body.visible
                || !SeatRenderer.isFemale(entity)) {
            CURRENT.set(State.HIDDEN);
            return;
        }
        float t = Mth.clamp(shape.bust() / BodyShape.MAX, 0.0f, 1.0f);
        float depth = MAX_DEPTH * t;
        if (shape.bust() <= 0.02f || depth < 0.04f) {
            CURRENT.set(State.HIDDEN);
            return;
        }
        float torsoHalf = 4.0f * model.body.xScale;
        float torsoFront = -2.0f * model.body.zScale;
        CURRENT.set(new State(true, torsoHalf, torsoFront, depth, entity, BodyPhysics.bust(entity, shape)));
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
        boolean hasChestplate = state.entity != null
                && !state.entity.getItemBySlot(EquipmentSlot.CHEST).isEmpty();
        if (armorModel) {
            // Only the chestplate pass should carry a bust (not leggings-only pass).
            if (!hasChestplate || !model.body.visible) {
                return;
            }
        } else {
            if (!model.body.visible || hasChestplate) {
                return;
            }
        }

        boolean clothingLayer = !armorModel && overlayVisible(model);
        ClothingSeatFit.Info fit = ClothingSeatFit.of(state.entity);
        float inflate = armorModel ? ARMOR_INFLATE : LoftMesh.layerInflate(model, clothingLayer, CLOTHING_INFLATE);
        UvLayer layer = armorModel ? UvLayer.ARMOR : UvLayer.INNER;

        matrices.pushPose();
        applyBodyPoseWithoutScale(model.body, matrices);
        applyJiggle(state, matrices);
        PoseStack.Pose pose = matrices.last();
        draw(state, pose, vertices, light, overlay, color, layer, inflate);
        if (!armorModel && LoftMesh.jacketPass(model, clothingLayer) && fit.jacket()) {
            draw(state, pose, vertices, light, overlay, color, UvLayer.JACKET, inflate + JACKET_EXTRA);
        }
        matrices.popPose();
    }

    /**
     * v2 NPC_Base chest profile (model_v2.py CHEST_PROFILE converted to MCA
     * model space, y down from the shoulders): {y, share of the depth}.
     */
    private static final float[][] CHEST_PROFILE = {
            {0.10f, 0.00f}, {0.45f, 0.12f}, {1.40f, 0.55f}, {2.60f, 0.95f},
            {3.80f, 1.00f}, {4.90f, 0.35f}, {5.50f, 0.00f}
    };
    /** Section across one block, centre line to outer edge (v2 xs / ks, torso half = 4). */
    private static final float[] SECTION_X = {0.0f, 0.5f, 1.4f, 2.4f, 3.2f, 3.75f, 3.97f};
    private static final float[] SECTION_K = {0.78f, 0.95f, 1.0f, 1.0f, 0.9f, 0.62f, 0.30f};

    private static void draw(
            State st, PoseStack.Pose pose, VertexConsumer vc,
            int light, int overlay, int color,
            UvLayer layer, float inf
    ) {
        float texH = layer == UvLayer.ARMOR ? TEX_32 : TEX_64;
        float baseV = layer == UvLayer.JACKET ? 36.0f : 20.0f;
        float hw = st.torsoHalf + inf;
        float sx = hw / 4.0f;
        float zf = st.torsoFront - inf;
        float zFlush = zf + 0.02f;
        float zIn = st.torsoFront + 0.80f;

        int rings = CHEST_PROFILE.length;
        int n = SECTION_X.length + 2;
        float[] ys = new float[rings];
        for (int side = -1; side <= 1; side += 2) {
            float[][][] ring = new float[rings][n][];
            for (int k = 0; k < rings; k++) {
                ys[k] = CHEST_PROFILE[k][0];
                float p = st.depth * CHEST_PROFILE[k][1];
                float[][] pts = new float[n][];
                pts[0] = new float[] {0.0f, zIn};
                for (int i = 0; i < SECTION_X.length; i++) {
                    pts[i + 1] = new float[] {SECTION_X[i] * sx, zFlush - SECTION_K[i] * p};
                }
                pts[n - 1] = new float[] {SECTION_X[SECTION_X.length - 1] * sx, zIn};
                for (int i = 0; i < n; i++) {
                    // right block (-x) is the mirror image; reverse to keep the winding order
                    float[] q = side < 0 ? pts[n - 1 - i] : pts[i];
                    ring[k][i] = new float[] {side * q[0], q[1]};
                }
            }
            LoftMesh.loft(pose, vc, light, overlay, color, TEX_64, texH, ys, ring,
                    (x, y, z, nx, ny, nz, fx, fy, fz) -> LoftMesh.torsoUv(
                            x, y, z, nx, nz, fz, hw, zf, -zf, baseV, 0.0f, 11.95f));
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

    /** Collar line of the chest profile (first ring), where the mesh is flush with the torso. */
    private static final float PIVOT_Y = 0.10f;

    /** Offsets and tilts the whole bust mesh about its collar line (model pixels / degrees). */
    private static void applyJiggle(State st, PoseStack matrices) {
        BodyPhysics.Offset o = st.jiggle;
        if (o == null || o.isNone()) {
            return;
        }
        float px = 1.0f / 16.0f;
        matrices.translate(o.x() * px, (PIVOT_Y + o.y()) * px, st.torsoFront * px);
        matrices.mulPose(new Quaternionf().rotationZYX(
                o.roll() * Mth.DEG_TO_RAD, 0.0f, o.pitch() * Mth.DEG_TO_RAD));
        matrices.translate(0.0f, -PIVOT_Y * px, -st.torsoFront * px);
    }

    private record State(
            boolean visible,
            float torsoHalf,
            float torsoFront,
            float depth,
            LivingEntity entity,
            BodyPhysics.Offset jiggle
    ) {
        private static final State HIDDEN = new State(false, 4.0f, -2.0f, 0.0f, null, BodyPhysics.Offset.NONE);
    }
}
