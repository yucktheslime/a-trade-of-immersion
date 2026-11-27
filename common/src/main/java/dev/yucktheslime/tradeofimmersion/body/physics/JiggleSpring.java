package dev.yucktheslime.tradeofimmersion.body.physics;

/**
 * Loader-agnostic, Minecraft-free spring-damper used for the body-shape bust and seat jiggle.
 *
 * <p>Two axes in model pixels: {@code y} (down is positive, like MCA model space) and {@code x}
 * (sideways). It is stepped once per game tick with semi-implicit Euler (unit mass, dt = 1 tick):
 * <pre>
 *   v = v * (1 - damping) + stiffness * (target - x) + impulse
 *   x = x + v
 * </pre>
 * With the shipped tunings ({@link Tuning#BUST}, {@link Tuning#SEAT}) this is underdamped and
 * decays by roughly {@code sqrt(1 - damping)} per tick, so a bounce settles within about two seconds.
 *
 * <p>Inputs are body kinematics ({@link Input}); the spring only ever produces a render offset
 * that is hard-clamped to {@link Tuning#maxY} / {@link Tuning#maxX}. It never changes a size.
 * Intensity 0 (or size 0) snaps the state to rest so nothing moves.
 */
public final class JiggleSpring {
    /** Ticks without a step after which the state is reset instead of integrating a huge gap. */
    public static final int MAX_TICK_GAP = 5;

    /**
     * Per-region tuning. Offsets are model pixels, gains are per block-per-tick of input.
     *
     * @param stiffness  pull back to the target per tick (0..1)
     * @param damping    velocity loss per tick (0..1)
     * @param accelGain  vertical impulse per (block/tick) change of vertical speed: jumps and landings
     * @param bobGain    walk/sprint bob amplitude at full walk speed
     * @param bobFreq    bob cycles per walk-animation stride (2 = twice per stride)
     * @param swayGain   sideways lag per degree/tick of body yaw turn
     * @param maxY       hard bound on |y| (px)
     * @param maxX       hard bound on |x| (px)
     * @param maxVel     hard bound on |velocity| (px/tick)
     */
    public record Tuning(float stiffness, float damping, float accelGain, float bobGain, float bobFreq,
                         float swayGain, float maxY, float maxX, float maxVel) {
        /** Chest: a little stiffer, two bobs per stride. */
        public static final Tuning BUST = new Tuning(0.32f, 0.36f, 0.65f, 0.16f, 2.0f, 0.018f, 0.60f, 0.30f, 0.45f);
        /** Seat: a little floppier, one bob per stride (per leg step it alternates via the phase). */
        public static final Tuning SEAT = new Tuning(0.26f, 0.32f, 0.55f, 0.14f, 1.0f, 0.014f, 0.50f, 0.30f, 0.40f);
    }

    /**
     * One tick of body kinematics.
     *
     * @param verticalSpeed this tick's vertical movement in blocks (up positive)
     * @param walkSpeed     walk animation speed normalized to 1 at a normal walk (sprint ~1.3), 0 standing
     * @param walkPosition  walk animation position (radians-ish phase source; MCA/vanilla limb swing)
     * @param yawDelta      body yaw change this tick in degrees (wrapped to -180..180)
     * @param seated        riding or sitting: motion is damped down
     */
    public record Input(float verticalSpeed, float walkSpeed, float walkPosition, float yawDelta, boolean seated) {
        public static final Input REST = new Input(0.0f, 0.0f, 0.0f, 0.0f, false);
    }

    private final Tuning tuning;
    private final float phase;

    private float y, x, vy, vx;
    private float prevY, prevX;
    private float lastVerticalSpeed;
    private boolean primed;
    private long lastTick = Long.MIN_VALUE;

    public JiggleSpring(Tuning tuning, float phase) {
        this.tuning = tuning;
        this.phase = phase;
    }

    public Tuning tuning() {
        return tuning;
    }

    /**
     * Steps once for game tick {@code tick}. Repeat calls for the same tick are ignored (an entity
     * renders many frames per tick and in several passes). A gap longer than {@link #MAX_TICK_GAP}
     * (the entity was off screen) resets to rest.
     *
     * @param size      0..1 share of the region's maximum size (amplitude scales with it)
     * @param intensity 0..2 user intensity; 0 means no motion at all
     */
    public void step(long tick, Input in, float size, float intensity) {
        if (tick == lastTick) {
            return;
        }
        boolean gap = lastTick == Long.MIN_VALUE || tick < lastTick || tick - lastTick > MAX_TICK_GAP;
        lastTick = tick;
        float amp = clamp(size, 0.0f, 1.0f) * clamp(intensity, 0.0f, 2.0f);
        if (!(amp > 0.0f) || gap || !finite(in)) {
            reset();
            lastVerticalSpeed = finite(in) ? in.verticalSpeed() : 0.0f;
            primed = amp > 0.0f && finite(in);
            return;
        }
        prevY = y;
        prevX = x;

        float seatedScale = in.seated() ? 0.4f : 1.0f;
        float drive = amp * seatedScale;

        // Jumps and landings: a change of vertical speed throws the flesh the other way (lags behind).
        float accel = clamp(in.verticalSpeed() - lastVerticalSpeed, -0.8f, 0.8f);
        lastVerticalSpeed = in.verticalSpeed();
        float impulseY = primed ? accel * tuning.accelGain() * drive : 0.0f;
        primed = true;

        // Walk/sprint bob and a little out-of-phase side wobble.
        float walk = clamp(in.walkSpeed(), 0.0f, 1.6f);
        float p = in.walkPosition() * 0.6662f * tuning.bobFreq() + phase;
        float targetY = (float) Math.cos(p) * tuning.bobGain() * walk * drive;
        float targetX = (float) Math.sin(p * 0.5f) * tuning.bobGain() * 0.35f * walk * drive;

        // Turning: lag opposite to the turn.
        targetX += -clamp(in.yawDelta(), -45.0f, 45.0f) * tuning.swayGain() * drive;

        targetY = clamp(targetY, -tuning.maxY(), tuning.maxY());
        targetX = clamp(targetX, -tuning.maxX(), tuning.maxX());

        float keep = 1.0f - tuning.damping();
        vy = clamp(vy * keep + tuning.stiffness() * (targetY - y) + impulseY, -tuning.maxVel(), tuning.maxVel());
        vx = clamp(vx * keep + tuning.stiffness() * (targetX - x), -tuning.maxVel(), tuning.maxVel());
        y += vy;
        x += vx;
        if (y > tuning.maxY() || y < -tuning.maxY()) {
            y = clamp(y, -tuning.maxY(), tuning.maxY());
            vy = 0.0f;
        }
        if (x > tuning.maxX() || x < -tuning.maxX()) {
            x = clamp(x, -tuning.maxX(), tuning.maxX());
            vx = 0.0f;
        }
    }

    public void reset() {
        y = x = vy = vx = prevY = prevX = 0.0f;
    }

    /** Vertical offset in model pixels (down positive), interpolated between the last two ticks. */
    public float y(float partialTick) {
        return lerp(clamp(partialTick, 0.0f, 1.0f), prevY, y);
    }

    /** Sideways offset in model pixels, interpolated between the last two ticks. */
    public float x(float partialTick) {
        return lerp(clamp(partialTick, 0.0f, 1.0f), prevX, x);
    }

    public float velocityY() {
        return vy;
    }

    public float velocityX() {
        return vx;
    }

    private static boolean finite(Input in) {
        return Float.isFinite(in.verticalSpeed()) && Float.isFinite(in.walkSpeed())
                && Float.isFinite(in.walkPosition()) && Float.isFinite(in.yawDelta());
    }

    static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static float lerp(float t, float a, float b) {
        return a + t * (b - a);
    }
}
