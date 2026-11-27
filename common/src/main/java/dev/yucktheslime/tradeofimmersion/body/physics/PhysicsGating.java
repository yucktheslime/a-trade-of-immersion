package dev.yucktheslime.tradeofimmersion.body.physics;

/**
 * Loader-agnostic rules for whether A Trade of Immersion drives the bust jiggle
 * on its own {@code BustRenderer} mesh, and how intensity is scaled when
 * MCA: Female Gender Physics ({@code mcabp}) is also installed.
 *
 * <p>When mcabp is loaded the breast gene is kept at 0 so mcabp does not draw
 * a second bust. This layer still jiggles the TOI mesh. A missing or failed
 * read of mcabp's config must never disable that (pass {@code null} for the
 * toggle and 1.0 for the bounce scale).
 */
public final class PhysicsGating {
    private PhysicsGating() {
    }

    /**
     * @param toiPhysicsEnabled   TOI's {@code bodyPhysics} switch
     * @param mcabpLoaded         whether mcabp is loaded
     * @param mcabpPhysicsEnabled {@code Boolean.FALSE} if mcabp's master toggle
     *                            was read and is off; {@code null} if it could
     *                            not be read (treated as on); {@code Boolean.TRUE} if on
     */
    public static boolean toiDrivesBust(boolean toiPhysicsEnabled, boolean mcabpLoaded, Boolean mcabpPhysicsEnabled) {
        if (!toiPhysicsEnabled) {
            return false;
        }
        // Only an explicit off switch from mcabp disables the bust. A failed
        // or missing reflection is null and must not turn physics off.
        return !mcabpLoaded || !Boolean.FALSE.equals(mcabpPhysicsEnabled);
    }

    /**
     * Combined bust intensity. When mcabp is loaded, TOI's 0–2 intensity is
     * multiplied by {@code mcabpBounceScale} (mcabp's 0–1 bounceMultiplier
     * mapped so the default 0.5 becomes 1.0). Pass 1.0 when mcabp is absent
     * or the value cannot be read.
     */
    public static float bustIntensity(float toiIntensity, boolean mcabpLoaded, float mcabpBounceScale) {
        if (!mcabpLoaded) {
            return toiIntensity;
        }
        float scale = Float.isFinite(mcabpBounceScale) ? mcabpBounceScale : 1.0f;
        return toiIntensity * scale;
    }

    /**
     * Maps mcabp's 0–1 {@code bounceMultiplier} onto a scale factor around 1.0
     * (0.5 → 1.0). Null or non-finite values become 1.0.
     */
    public static float bounceScale(Double bounceMultiplierOrNull) {
        if (bounceMultiplierOrNull == null || !Double.isFinite(bounceMultiplierOrNull)) {
            return 1.0f;
        }
        return (float) (bounceMultiplierOrNull / 0.5d);
    }
}
