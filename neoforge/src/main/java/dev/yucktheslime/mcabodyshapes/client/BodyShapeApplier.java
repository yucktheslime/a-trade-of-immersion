package dev.yucktheslime.mcabodyshapes.client;

import dev.yucktheslime.tradeofimmersion.life.client.BellyVisual;
import net.conczin.mca.entity.VillagerLike;

import dev.yucktheslime.mcabodyshapes.body.BodyShape;
import dev.yucktheslime.mcabodyshapes.body.BodyShapeAge;
import dev.yucktheslime.mcabodyshapes.body.BodyShapeLimits;
import dev.yucktheslime.mcabodyshapes.body.PregnancyProgress;
import net.conczin.mca.client.model.CommonVillagerModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * Applies saved slider values and pregnancy progress (A Trade of Immersion's phases) to an already-posed MCA model.
 *
 * <p>Called at the tail of {@code setupAnim}, after MCA (and any expression animations that
 * hook the same pose) have finished, so we only change part scales and leave rotations alone
 * (aside from moving the arms outward for shoulder width). Seat geometry
 * is prepared here and drawn later in {@link SeatRenderer} / {@link BellyRenderer}
 * after the body.
 */
public final class BodyShapeApplier {
    private BodyShapeApplier() {
    }

    public static boolean shouldShape(LivingEntity entity) {
        return BodyShapeAge.isShapeableAdult(entity);
    }

    public static float heightMultiplier(BodyShape shape) {
        return lerpChannel(shape.height(), 0.88f, 1.0f, 1.14f);
    }

    public static void apply(LivingEntity entity, HumanoidModel<?> model, CommonVillagerModel<?> villagerModel) {
        resetScales(model, villagerModel);

        // Children and teens keep MCA's unmodified model. Only adults are shaped.
        if (!shouldShape(entity)) {
            BustRenderer.clear();
            SeatRenderer.clear();
            BellyRenderer.clear();
            ThighRenderer.clear();
            // A Trade of Immersion companions use MCA's player model but are not MCA villagers, so this mod
            // leaves them unshaped; re-apply TOI's own simple belly that resetScales just undid. Never for
            // MCA villagers or players here (adult-only rule).
            if (!(entity instanceof VillagerLike<?>)) {
                BellyVisual.applyBody(entity, model);
            }
            villagerModel.syncWearParts();
            return;
        }

        // Configured slider ranges apply at render time too, so lowering a
        // max in the config immediately caps villagers saved before it.
        BodyShape shape = BodyShapeLimits.clamp(BodyShape.get(entity));
        float pregnancy = PregnancyProgress.of(entity);

        if (!(shape.isVanilla() && pregnancy <= 0.0f)) {
            float weightMul = weightMul(shape);
            // Waist width narrows modestly (about 0.5px per side at 0).
            float waistMul = lerpChannel(shape.waist(), 0.88f, 1.0f, 1.10f);
            // Hip width below 1 slims the lower torso and legs a little.
            // Above 1 it is drawn as the hip flare (SeatRenderer), never as
            // depth. Rear depth and thigh thickness do not touch the torso.
            float narrowT = Math.min(1.0f, shape.hips());
            float hipTorsoMul = Mth.lerp(narrowT, 0.96f, 1.0f);
            float hipLegMul = Mth.lerp(narrowT, 0.92f, 1.0f);
            float muscleMul = lerpChannel(shape.muscle(), 0.90f, 1.0f, 1.22f);
            float chestMul = lerpChannel(shape.chest(), 0.92f, 1.0f, 1.18f);
            float gutMul = lerpChannel(shape.gut(), 0.92f, 1.0f, 1.28f);
            float shoulderSpread = lerpChannel(shape.shoulders(), -0.55f, 0.0f, 0.70f);
            float hipSpread = Math.max(0.0f, hipExtraPerSide(shape));

            // Pregnancy volume is the front bump (BellyRenderer). Keep only a
            // faint torso swell so clothing still wraps; gut is unchanged.
            float gutAndPregnancy = (gutMul - 1.0f) + 0.08f * pregnancy;
            float bodyX = weightMul * waistMul * hipTorsoMul * lerpChannel(shape.chest(), 0.96f, 1.0f, 1.06f);
            float bodyZ = weightMul * chestMul * (1.0f + gutAndPregnancy);
            float bodyY = 1.0f + 0.02f * pregnancy + 0.06f * Math.max(0.0f, shape.gut() - 1.0f);
            float armMul = lerpChannel(shape.weight(), 0.90f, 1.0f, 1.16f) * muscleMul
                    * lerpChannel(shape.shoulders(), 0.94f, 1.0f, 1.10f);
            // Lower legs stay near the vanilla MCA size so the calf and
            // knee do not fatten. Extra upper-thigh volume is a separate
            // clothed cube in ThighRenderer, capped at +1px/side.
            float legX = lowerLegWidthScale(shape) * hipLegMul;
            float legZ = lowerLegDepthScale(shape);

            scale(model.body, bodyX, bodyY, bodyZ);
            scale(model.leftArm, armMul, 1.0f, armMul);
            scale(model.rightArm, armMul, 1.0f, armMul);
            scale(model.leftLeg, legX, 1.0f, legZ);
            scale(model.rightLeg, legX, 1.0f, legZ);

            // HumanoidModel.setupAnim never resets part positions, and MCA reuses
            // one model instance for every villager, so positions are always set
            // absolutely from the initial pose (never incrementally).
            model.leftArm.x = model.leftArm.getInitialPose().x - shoulderSpread;
            model.rightArm.x = model.rightArm.getInitialPose().x + shoulderSpread;
            // Wider hips: legs move out with the hip flare so it meets them.
            outward(model.leftLeg, 0.25f * hipSpread);
            outward(model.rightLeg, 0.25f * hipSpread);
            // 0.6.0: hanging arms tilt out (like the v2 NPC_Base sheet, ~9 deg
            // at max) so the hip ring and the seat's side wrap never clip them.
            float seatWrap = Mth.clamp(2.0f * rearDepthScale(shape, true) / 3.1f, 0.0f, 1.0f);
            // A pregnancy belly stays inside the torso width, but the arms
            // also ease out a little so they clear it with every outfit layer.
            float armClear = Math.max(Math.max(hipSpread, 0.9f * seatWrap), 0.8f * pregnancy);
            model.leftArm.x += Math.signum(model.leftArm.getInitialPose().x) * 0.25f * armClear;
            model.rightArm.x += Math.signum(model.rightArm.getInitialPose().x) * 0.25f * armClear;
            model.leftArm.zRot -= 0.16f * armClear;
            model.rightArm.zRot += 0.16f * armClear;
            float forward = 0.12f * pregnancy + 0.55f * Math.max(0.0f, shape.gut() - 1.0f);
            model.body.z = model.body.getInitialPose().z - forward * bodyZ;
        } else {
            model.leftArm.x = model.leftArm.getInitialPose().x;
            model.rightArm.x = model.rightArm.getInitialPose().x;
            model.leftLeg.x = model.leftLeg.getInitialPose().x;
            model.rightLeg.x = model.rightLeg.getInitialPose().x;
            model.body.z = model.body.getInitialPose().z;
        }

        if (DevPoseHook.armsBack()) {
            // Dev screenshots only: swing both arms back so a true side view
            // shows the whole front profile (chest, belly).
            model.leftArm.xRot = 0.85f;
            model.rightArm.xRot = 0.85f;
        }
        if (DevPoseHook.armsForward()) {
            // Dev screenshots only: raise the near arm cleanly forward so the
            // true side view exposes the full chest, ribcage, waist, and seat.
            model.leftArm.xRot = -0.38f;
        }

        applyBustPolicy(villagerModel);
        BustRenderer.prepare(entity, shape, model);
        SeatRenderer.prepare(entity, shape, pregnancy, model);
        BellyRenderer.prepare(entity, pregnancy, model);
        ThighRenderer.prepare(entity, shape, model);
        villagerModel.syncWearParts();
    }

    private static void applyBustPolicy(CommonVillagerModel<?> villagerModel) {
        // BustRenderer draws the clean flat-faced Bust Size mesh on top of the
        // torso (with zero physics/jiggle), so hide MCA's built-in tilted cubes.
        ModelPart breastPart = villagerModel.getBreastPart();
        if (breastPart != null) {
            breastPart.visible = false;
        }
        for (ModelPart part : villagerModel.getBreastParts()) {
            part.visible = false;
        }
    }

    private static void resetScales(HumanoidModel<?> model, CommonVillagerModel<?> villagerModel) {
        reset(model.head, model.hat, model.body, model.leftArm, model.rightArm, model.leftLeg, model.rightLeg);
        reset(villagerModel.getBodyPart(), villagerModel.getBreastPart());
        for (ModelPart part : villagerModel.getBreastParts()) {
            reset(part);
        }
        for (ModelPart part : villagerModel.getCommonBodyParts()) {
            reset(part);
        }
    }

    private static void reset(ModelPart... parts) {
        for (ModelPart part : parts) {
            if (part != null) {
                part.xScale = 1.0f;
                part.yScale = 1.0f;
                part.zScale = 1.0f;
            }
        }
    }

    private static void scale(ModelPart part, float x, float y, float z) {
        if (part != null) {
            part.xScale = x;
            part.yScale = y;
            part.zScale = z;
        }
    }

    /** Clamp so extreme slider combos cannot invert or explode the legs. */
    public static final float LEG_SCALE_MIN = 0.62f;
    public static final float LEG_SCALE_MAX = 2.35f;

    public static float curvesT(BodyShape shape) {
        return Mth.clamp(shape.curves() / BodyShape.MAX, 0.0f, 1.0f);
    }

    public static float weightMul(BodyShape shape) {
        return lerpChannel(shape.weight(), 0.76f, 1.0f, 1.32f);
    }

    /**
     * Hip width in model pixels per side, from the hips slider only: -0.4 at
     * 0, 0 at 1 (vanilla), +1.0 at 2. Hard capped at +1px per side.
     */
    public static float hipExtraPerSide(BodyShape shape) {
        return Mth.clamp(lerpChannel(shape.hips(), -0.40f, 0.0f, 1.0f), -0.40f, 1.0f);
    }

    /**
     * Rear depth scale from the seat slider (weight adds a little). 0 at
     * seat 0; clamped to the {@link SeatRenderer#MAX_DEPTH_SCALE} cap of 1.55
     * after every factor. Male villagers get 80%.
     */
    public static float rearDepthScale(BodyShape shape, boolean female) {
        float depth = lerpSeat(shape.seat(), 0.0f, 0.70f, 1.10f, 1.55f)
                * lerpChannel(shape.weight(), 0.92f, 1.0f, 1.06f);
        depth = Mth.clamp(depth, 0.0f, SeatRenderer.MAX_DEPTH_SCALE);
        return female ? depth : depth * 0.80f;
    }

    /**
     * Extra width, in model pixels per side, on the upper-thigh cubes, from
     * the thigh thickness slider only: 0 at 0, 0.5 at 1, 1.0 (hard cap) at 2.
     */
    public static float thighExtraPerSide(BodyShape shape) {
        return Mth.clamp(lerpChannel(shape.thighs(), 0.0f, 0.50f, 1.00f), 0.0f, 1.00f);
    }

    /**
     * Lower-leg width, scaled from the hip pivot. Kept close to vanilla so
     * the calf and knee stay the MCA size; fullness lives on the upper
     * thigh cubes instead. Weight is the only slider that still trims or
     * fills the whole leg, and only slightly.
     */
    public static float lowerLegWidthScale(BodyShape shape) {
        return Mth.clamp(lerpChannel(shape.weight(), 0.92f, 1.0f, 1.08f), LEG_SCALE_MIN, LEG_SCALE_MAX);
    }

    public static float lowerLegDepthScale(BodyShape shape) {
        return Mth.clamp(lerpChannel(shape.weight(), 0.94f, 1.0f, 1.06f), LEG_SCALE_MIN, LEG_SCALE_MAX);
    }

    private static void outward(ModelPart part, float amount) {
        float x0 = part.getInitialPose().x;
        part.x = x0 + Math.signum(x0) * amount;
    }

    /**
     * Maps a 0–2 channel onto [low, mid, high] with mid at 1.0.
     */
    public static float lerpChannel(float value, float low, float mid, float high) {
        if (value <= 1.0f) {
            return Mth.lerp(value, low, mid);
        }
        return Mth.lerp(value - 1.0f, mid, high);
    }

    /**
     * Maps the 0–3 seat channel onto values at 0, 1, 2, and 3. Midpoints stay
     * on the 0.4.6 look (1 and 2); 0 is flat and 3 is extra thick.
     */
    public static float lerpSeat(float value, float at0, float at1, float at2, float at3) {
        if (value <= 1.0f) {
            return Mth.lerp(Mth.clamp(value, 0.0f, 1.0f), at0, at1);
        }
        if (value <= 2.0f) {
            return Mth.lerp(value - 1.0f, at1, at2);
        }
        float t = (value - 2.0f) / (BodyShape.SEAT_MAX - 2.0f);
        return Mth.lerp(Mth.clamp(t, 0.0f, 1.0f), at2, at3);
    }
}
