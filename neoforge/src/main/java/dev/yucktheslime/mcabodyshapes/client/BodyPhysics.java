package dev.yucktheslime.mcabodyshapes.client;

import dev.yucktheslime.mcabodyshapes.McaBodyShapes;
import dev.yucktheslime.mcabodyshapes.body.BodyShape;
import dev.yucktheslime.mcabodyshapes.body.BodyShapeAge;
import dev.yucktheslime.mcabodyshapes.compat.McaPhysicsCompat;
import dev.yucktheslime.mcabodyshapes.config.BodyShapeClientConfig;
import dev.yucktheslime.tradeofimmersion.body.physics.JiggleSpring;
import dev.yucktheslime.tradeofimmersion.body.physics.MotionSample;
import dev.yucktheslime.tradeofimmersion.body.physics.PhysicsGating;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Client-only, per-entity bust and seat jiggle for the body-shape meshes ({@link BustRenderer},
 * {@link SeatRenderer}). Each entity gets two independent {@link JiggleSpring}s (common code, unit
 * tested) stepped once per game tick from the entity's vertical speed (jumps, landings, falls), its
 * walk/sprint animation and its body yaw turn. The renderers only use the result as a small offset
 * and tilt of the existing mesh; sizes and caps are never touched.
 *
 * <p>Gating is the same as the shapes themselves: only {@link BodyShapeAge#isShapeableAdult} entities
 * that are explicitly female ({@link SeatRenderer#isFemale}). This layer always drives the TOI
 * {@link BustRenderer} mesh, including when MCA: Female Gender Physics ({@code mcabp}) is loaded
 * (the breast gene is kept at 0 so mcabp does not draw a second bust). mcabp's bounce multiplier
 * and master physics toggle are honored when they can be read; a failed reflection never disables
 * the jiggle. The seat always uses this physics.
 *
 * <p>Motion is sampled from the last stepped pose ({@link MotionSample}), not only from
 * {@code entity.yo}, so interpolated MCA villagers, TOI companions and other players still
 * produce a non-zero velocity. Bust and seat share one sample per tick.
 *
 * <p>State lives in a {@link WeakHashMap} keyed by the entity, so unloaded entities are collected,
 * and the whole map is dropped when the client level changes (world switch, dimension change,
 * disconnect). Render-thread only.
 */
public final class BodyPhysics {
    /** Degrees of tilt per pixel of vertical offset. */
    private static final float PITCH_PER_PX = 6.0f;
    /** Degrees of roll per pixel of sideways offset. */
    private static final float ROLL_PER_PX = 5.0f;

    private static final Map<LivingEntity, Entry> ENTRIES = new WeakHashMap<>();
    private static WeakReference<Object> level = new WeakReference<>(null);
    private static boolean logged;

    private BodyPhysics() {
    }

    /** Render offset in model pixels and degrees. {@link #NONE} means draw the mesh where it is. */
    public record Offset(float x, float y, float pitch, float roll) {
        public static final Offset NONE = new Offset(0.0f, 0.0f, 0.0f, 0.0f);

        public boolean isNone() {
            return x == 0.0f && y == 0.0f && pitch == 0.0f && roll == 0.0f;
        }
    }

    private static final class Entry {
        final JiggleSpring bust = new JiggleSpring(JiggleSpring.Tuning.BUST, 0.0f);
        final JiggleSpring seat = new JiggleSpring(JiggleSpring.Tuning.SEAT, (float) Math.PI * 0.5f);
        final MotionSample motion = new MotionSample();
        long sampledTick = Long.MIN_VALUE;
        JiggleSpring.Input input = JiggleSpring.Input.REST;
    }

    public static boolean eligible(LivingEntity entity) {
        return BodyShapeAge.isShapeableAdult(entity) && SeatRenderer.isFemale(entity);
    }

    /** Bust jiggle for this frame; {@link Offset#NONE} when off or not eligible. */
    public static Offset bust(LivingEntity entity, BodyShape shape) {
        logOnce();
        if (!PhysicsGating.toiDrivesBust(
                BodyShapeClientConfig.physicsEnabled(),
                McaPhysicsCompat.isLoaded(),
                McaPhysicsCompat.physicsEnabledOrNull())) {
            return Offset.NONE;
        }
        float size = (float) Math.sqrt(Mth.clamp(shape.bust() / BodyShape.MAX, 0.0f, 1.0f));
        float intensity = PhysicsGating.bustIntensity(
                BodyShapeClientConfig.bustIntensity(),
                McaPhysicsCompat.isLoaded(),
                McaPhysicsCompat.effectiveBounceScale());
        return compute(entity, true, size, intensity);
    }

    /** Seat jiggle for this frame (runs with or without mcabp). */
    public static Offset seat(LivingEntity entity, BodyShape shape) {
        logOnce();
        float size = (float) Math.sqrt(Mth.clamp(shape.seat() / BodyShape.SEAT_MAX, 0.0f, 1.0f));
        return compute(entity, false, size, BodyShapeClientConfig.seatIntensity());
    }

    private static Offset compute(LivingEntity entity, boolean bust, float size, float intensity) {
        if (!BodyShapeClientConfig.physicsEnabled() || !(intensity > 0.0f) || !(size > 0.0f) || !eligible(entity)) {
            return Offset.NONE;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != level.get()) {
            ENTRIES.clear();
            level = new WeakReference<>(mc.level);
        }
        if (mc.level == null || entity.level() != mc.level) {
            return Offset.NONE;
        }
        Entry entry = ENTRIES.computeIfAbsent(entity, e -> new Entry());
        JiggleSpring spring = bust ? entry.bust : entry.seat;
        spring.step(entity.tickCount, sampleInput(entity, entry), size, intensity);

        float pt = mc.isPaused() ? 1.0f : mc.getTimer().getGameTimeDeltaPartialTick(false);
        float y = spring.y(pt);
        float x = spring.x(pt);
        // Bust tips down as it drops; the seat (behind the pivot) tips the other way to drop too.
        float pitch = (bust ? 1.0f : -1.0f) * y * PITCH_PER_PX;
        float roll = x * ROLL_PER_PX;
        return new Offset(x, y, pitch, roll);
    }

    /**
     * One kinematics sample per entity per tick, shared by the bust and seat springs so the
     * second caller does not see a zero delta after the first has already advanced last-Y.
     */
    private static JiggleSpring.Input sampleInput(LivingEntity entity, Entry entry) {
        long tick = entity.tickCount;
        if (entry.sampledTick == tick) {
            return entry.input;
        }
        if (entry.sampledTick != tick - 1) {
            entry.motion.reset();
        }
        entry.sampledTick = tick;
        boolean seated = entity.isPassenger() || entity.getPose() == Pose.SITTING || entity.isSleeping();
        entry.input = entry.motion.sample(
                entity.getY(),
                entity.yo,
                entity.getDeltaMovement().y,
                entity.walkAnimation.speed() / 0.26f,
                entity.walkAnimation.position(),
                entity.yBodyRot,
                entity.yBodyRotO,
                seated);
        return entry.input;
    }

    private static void logOnce() {
        if (logged) {
            return;
        }
        logged = true;
        boolean toiOn = BodyShapeClientConfig.physicsEnabled();
        boolean mcabp = McaPhysicsCompat.isLoaded();
        Boolean toggle = McaPhysicsCompat.physicsEnabledOrNull();
        float bounce = McaPhysicsCompat.effectiveBounceScale();
        McaBodyShapes.LOGGER.info(
                "Body physics: enabled={}, bustIntensity={}, effectiveBustIntensity={}, seatIntensity={}, mcabp={}, mcabpPhysics={}, bounceScale={}, toiDrivesBust={}. Run /mcabody physics in game for a status readout.",
                toiOn,
                BodyShapeClientConfig.bustIntensity(),
                PhysicsGating.bustIntensity(BodyShapeClientConfig.bustIntensity(), mcabp, bounce),
                BodyShapeClientConfig.seatIntensity(),
                mcabp,
                toggle == null ? (mcabp ? "unread (treated as on)" : "n/a") : toggle,
                bounce,
                PhysicsGating.toiDrivesBust(toiOn, mcabp, toggle));
    }

    /** Drops all state (also happens automatically on a level change). */
    public static void clear() {
        ENTRIES.clear();
        level = new WeakReference<>(null);
    }
}
