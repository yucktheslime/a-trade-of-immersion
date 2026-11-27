package dev.yucktheslime.tradeofimmersion.body.physics;

import dev.yucktheslime.tradeofimmersion.body.BustModelRules;

/**
 * Loader-agnostic decisions for which jiggle runs on a female NPC, shared by the client renderer code and
 * unit tests.
 *
 * <ul>
 *   <li><b>Bust:</b> TOI's own {@code BodyPhysics} bust jiggle runs whenever the entity draws TOI's bust
 *       mesh, i.e. its model is {@code toi} (or mcabp is not available to this client, or it is a player).
 *       An entity whose model is {@code mcabp} has no TOI bust mesh; mcabp jiggles its own.</li>
 *   <li><b>Seat:</b> TOI's seat jiggle never depends on the bust model. It runs for every NPC.</li>
 * </ul>
 */
public final class PhysicsRules {
    /** Same as {@code BodyShape.MAX}: the Bust Size slider maximum. */
    public static final float BUST_MAX = 2.0f;
    /** Same as {@code BodyShape.SEAT_MAX}: the Butt Size slider maximum. */
    public static final float SEAT_MAX = 3.0f;

    private PhysicsRules() {
    }

    /** Spring amplitude share (0..1) for a Bust Size slider value. */
    public static float bustSize(float bust) {
        return share(bust, BUST_MAX);
    }

    /** Spring amplitude share (0..1) for a Butt Size slider value. */
    public static float seatSize(float seat) {
        return share(seat, SEAT_MAX);
    }

    private static float share(float value, float max) {
        if (!Float.isFinite(value)) {
            return 0.0f;
        }
        return (float) Math.sqrt(Math.max(0.0f, Math.min(1.0f, value / max)));
    }

    /** Does TOI's bust jiggle drive this entity's bust? True unless the entity draws mcabp's bust. */
    public static boolean toiDrivesBust(String storedModel, boolean mcabpLoaded, Boolean mcabpRenderingEnabled,
                                        boolean player) {
        return !BustModelRules.rendersMcabp(storedModel, mcabpLoaded, mcabpRenderingEnabled, player);
    }

    /** Does the seat jiggle run? Only the TOI config and a visible seat matter, never the bust model. */
    public static boolean seatJiggles(boolean physicsEnabled, float intensity, float seatSlider) {
        return physicsEnabled && intensity > 0.0f && seatSize(seatSlider) > 0.0f;
    }

    /** Does the TOI bust jiggle run for a model-{@code toi} entity under this config? */
    public static boolean bustJiggles(boolean physicsEnabled, float intensity, float bustSlider) {
        return physicsEnabled && intensity > 0.0f && bustSize(bustSlider) > 0.0f;
    }
}
