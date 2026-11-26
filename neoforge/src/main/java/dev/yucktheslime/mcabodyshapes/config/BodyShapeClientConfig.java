package dev.yucktheslime.mcabodyshapes.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client-only settings for seat geometry bounce. When MCA: Female Gender Physics
 * is also loaded, its master physics toggle and bounce multiplier are applied
 * on top of these values.
 */
public final class BodyShapeClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue PHYSICS_ENABLED;
    public static final ModConfigSpec.DoubleValue PHYSICS_INTENSITY;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        PHYSICS_ENABLED = builder
                .comment("When enabled, the seat geometry has a light spring-damper bounce driven by walking, jumping, landing, and turning. The mesh itself still renders when this is off.")
                .translation("mcabodyshapes.config.physics_enabled")
                .define("seatPhysicsEnabled", false);
        PHYSICS_INTENSITY = builder
                .comment("Seat bounce intensity. 0 is none, 1 is the default, 2 is strong.")
                .translation("mcabodyshapes.config.physics_intensity")
                .defineInRange("seatPhysicsIntensity", 0.0d, 0.0d, 2.0d);
        SPEC = builder.build();
    }

    private BodyShapeClientConfig() {
    }

    /** Physics / motion on the chest and seat is disabled in 0.5.1. */
    public static boolean physicsEnabled() {
        return false;
    }

    public static float physicsIntensity() {
        return 0.0f;
    }

    public static void setPhysicsEnabled(boolean enabled) {
        PHYSICS_ENABLED.set(enabled);
        SPEC.save();
    }

    public static void setPhysicsIntensity(float intensity) {
        PHYSICS_INTENSITY.set((double) intensity);
        SPEC.save();
    }
}
