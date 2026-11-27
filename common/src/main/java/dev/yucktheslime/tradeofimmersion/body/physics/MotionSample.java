package dev.yucktheslime.tradeofimmersion.body.physics;

/**
 * Client-tick kinematics for {@link JiggleSpring}, stored per entity.
 *
 * <p>Vertical speed and yaw delta are taken from this sample's own last pose,
 * not from {@code entity.yo} / {@code yBodyRotO}. Remote villagers, companions
 * and other players are interpolated on the client, and the vanilla previous
 * pose can already match the current one by the time a renderer reads it,
 * which would silently feed a zero velocity into the spring. The first sample
 * (or the first after {@link #reset()}) falls back to {@code y - yo}, or to
 * {@code deltaMovementY} when that fallback is zero while the entity is
 * clearly moving. Yaw is handled the same way against the previous body yaw.
 */
public final class MotionSample {
    private double lastY;
    private float lastYaw;
    private boolean primed;

    /**
     * @param y              current entity Y
     * @param yo             vanilla previous-tick Y (fallback on the first sample)
     * @param deltaMovementY entity delta-movement Y (fallback if {@code yo} looks stale)
     * @param walkSpeed      walk animation speed normalized to 1 at a normal walk
     * @param walkPosition   walk animation position
     * @param yaw            current body yaw in degrees
     * @param yawO           vanilla previous-tick body yaw (fallback on the first sample)
     * @param seated         riding, sitting or sleeping
     */
    public JiggleSpring.Input sample(double y, double yo, double deltaMovementY,
                                     float walkSpeed, float walkPosition,
                                     float yaw, float yawO, boolean seated) {
        float vertical;
        float yawDelta;
        if (primed) {
            vertical = (float) (y - lastY);
            yawDelta = wrapDegrees(yaw - lastYaw);
        } else {
            vertical = (float) (y - yo);
            if (vertical == 0.0f && deltaMovementY != 0.0 && Double.isFinite(deltaMovementY)) {
                vertical = (float) deltaMovementY;
            }
            yawDelta = wrapDegrees(yaw - yawO);
        }
        lastY = y;
        lastYaw = yaw;
        primed = true;
        if (!Float.isFinite(vertical)) {
            vertical = 0.0f;
        }
        if (!Float.isFinite(yawDelta)) {
            yawDelta = 0.0f;
        }
        return new JiggleSpring.Input(vertical, walkSpeed, walkPosition, yawDelta, seated);
    }

    /** Drops the last pose so the next sample does not span a gap (teleport, off-screen). */
    public void reset() {
        primed = false;
    }

    public boolean primed() {
        return primed;
    }

    static float wrapDegrees(float degrees) {
        float d = degrees % 360.0f;
        if (d >= 180.0f) {
            d -= 360.0f;
        }
        if (d < -180.0f) {
            d += 360.0f;
        }
        return d;
    }
}
