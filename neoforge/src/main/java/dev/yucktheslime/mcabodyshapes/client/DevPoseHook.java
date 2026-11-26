package dev.yucktheslime.mcabodyshapes.client;

/**
 * Dev-screenshot-only pose switch. Does nothing unless the game was started
 * with {@code -Dmcabodyshapes.devshot=true}; normal play never sets it.
 */
public final class DevPoseHook {
    private static final boolean DEV = Boolean.getBoolean("mcabodyshapes.devshot");
    private static volatile boolean armsForward;
    private static volatile boolean armsBack;

    private DevPoseHook() {
    }

    public static boolean armsForward() {
        return DEV && armsForward;
    }

    /** Both arms swung back so a side view shows the full front profile. */
    public static boolean armsBack() {
        return DEV && armsBack;
    }

    public static void setArmsBack(boolean value) {
        armsBack = DEV && value;
    }

    public static void setArmsForward(boolean value) {
        armsForward = DEV && value;
    }
}
