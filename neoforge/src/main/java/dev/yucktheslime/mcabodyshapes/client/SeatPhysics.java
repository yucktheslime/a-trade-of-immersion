package dev.yucktheslime.mcabodyshapes.client;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;

/**
 * Per-side spring-damper bounce for a seat part, modeled on the vertical +
 * lateral bounce used by MCA: Female Gender Physics' breast physics, but
 * simpler (no vehicle / swing / emote special cases).
 *
 * <p>Client-side only. One instance is kept for the left side and one for the
 * right so they can drift slightly out of phase.
 */
public final class SeatPhysics {
    private static final float WALK_SPEED_REFERENCE = 0.26f;
    private static final float FLOPPINESS = 0.75f;

    private final float phase;
    private float positionY;
    private float positionX;
    private float rotation;
    private float prePositionY;
    private float prePositionX;
    private float preRotation;
    private float velocityY;
    private float velocityX;
    private float velocityRot;
    private Vec3 lastPos;
    private double lastVerticalVelocity;
    private int lastTick = Integer.MIN_VALUE;

    public SeatPhysics(float phase) {
        this.phase = phase;
    }

    /**
     * Steps the simulation once per entity tick. Repeated calls in the same
     * tick are ignored so setupAnim / shadow / inventory renders do not
     * accelerate the bounce.
     */
    public void update(LivingEntity entity, float size, float intensity, boolean left) {
        int tick = entity.tickCount;
        if (tick == lastTick) {
            return;
        }
        lastTick = tick;

        prePositionY = positionY;
        prePositionX = positionX;
        preRotation = rotation;

        if (lastPos == null) {
            lastPos = entity.position();
            return;
        }

        float bounce = size * 2.4f * intensity;
        if (bounce <= 0.0f) {
            positionY = 0.0f;
            positionX = 0.0f;
            rotation = 0.0f;
            velocityY = 0.0f;
            velocityX = 0.0f;
            velocityRot = 0.0f;
            lastPos = entity.position();
            return;
        }

        Vec3 motion = entity.position().subtract(lastPos);
        lastPos = entity.position();

        boolean seated = entity.isPassenger() || entity.getPose() == Pose.SITTING;
        float sitDampen = seated ? 0.45f : 1.0f;

        float targetY = (float) motion.y * bounce * sitDampen;
        // Resting sag, scaled by size so larger seats hang a little lower.
        targetY += size * 0.28f * intensity * sitDampen;

        float walkSpeed = entity.walkAnimation.speed() / WALK_SPEED_REFERENCE;
        float walkPos = entity.walkAnimation.position() * 0.6662f + (left ? 0.0f : (float) Math.PI) + phase;
        targetY += Mth.cos(walkPos) * 0.42f * walkSpeed * 0.5f * intensity * sitDampen;

        double verticalVelocity = entity.getDeltaMovement().y;
        if (lastVerticalVelocity < -0.28 && (entity.onGround() || verticalVelocity >= -0.02)) {
            targetY += bounce * 0.85f * sitDampen;
        }
        lastVerticalVelocity = verticalVelocity;

        float yawDelta = entity.yBodyRot - entity.yBodyRotO;
        float targetX = -(yawDelta / 15.0f) * bounce * 0.22f * sitDampen;
        targetX += Mth.sin(walkPos) * 0.12f * walkSpeed * intensity * (left ? -1.0f : 1.0f) * sitDampen;

        float targetRot = targetY * 10.0f * sitDampen;

        targetY = Mth.clamp(targetY, -1.2f, 2.2f);
        targetX = Mth.clamp(targetX, -1.2f, 1.2f);
        targetRot = Mth.clamp(targetRot, -18.0f, 18.0f);

        float bounceAmount = Mth.clamp(0.45f * (1.0f - FLOPPINESS) + 0.15f, 0.15f, 0.6f);
        float delta = 2.25f - bounceAmount;

        velocityY = Mth.lerp(bounceAmount, velocityY, (targetY - positionY) * delta);
        positionY += velocityY * FLOPPINESS * 1.1625f;
        velocityX = Mth.lerp(bounceAmount, velocityX, (targetX - positionX) * delta);
        positionX += velocityX * FLOPPINESS;
        velocityRot = Mth.lerp(bounceAmount, velocityRot, (targetRot - rotation) * delta);
        rotation += velocityRot * FLOPPINESS;

        positionY = Mth.clamp(positionY, -0.45f, 1.35f);
        positionX = Mth.clamp(positionX, -1.2f, 1.2f);
        rotation = Mth.clamp(rotation, -16.0f, 16.0f);
        if (positionY <= -0.45f || positionY >= 1.35f) {
            velocityY = 0.0f;
        }
        if (positionX <= -1.2f || positionX >= 1.2f) {
            velocityX = 0.0f;
        }
    }

    public float getY(float partialTick) {
        return Mth.lerp(partialTick, prePositionY, positionY);
    }

    public float getX(float partialTick) {
        return Mth.lerp(partialTick, prePositionX, positionX);
    }

    public float getRotation(float partialTick) {
        return Mth.lerp(partialTick, preRotation, rotation);
    }
}
