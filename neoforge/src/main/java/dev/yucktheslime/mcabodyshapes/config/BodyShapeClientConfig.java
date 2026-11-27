package dev.yucktheslime.mcabodyshapes.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client-only settings ({@code config/mcabodyshapes-client.toml}) for the bust and seat jiggle
 * ({@link dev.yucktheslime.mcabodyshapes.client.BodyPhysics}). The keys live in a {@code [physics]}
 * section so the old, unused 0.5.x {@code seatPhysicsEnabled} / {@code seatPhysicsIntensity} values
 * (which defaulted to off / 0) do not carry over and silently disable the new physics.
 */
public final class BodyShapeClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue BODY_PHYSICS;
    public static final ModConfigSpec.DoubleValue BUST_INTENSITY;
    public static final ModConfigSpec.DoubleValue SEAT_INTENSITY;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Jiggle physics for the body-shape bust and seat meshes (explicitly adult, explicitly female"
                + " figures only). Motion only: sizes and caps are unchanged.").push("physics");
        BODY_PHYSICS = builder
                .comment("Master switch. When off, the bust and seat meshes are drawn static, as in 0.5.x.")
                .translation("mcabodyshapes.config.body_physics")
                .define("bodyPhysics", true);
        BUST_INTENSITY = builder
                .comment("Bust bounce intensity: 0 is none, 1 is the default, 2 is strong."
                        + " Still applied when MCA: Female Gender Physics (mcabp) is installed (TOI jiggles its own bust mesh;"
                        + " mcabp's bounce multiplier and master toggle are honored when readable).")
                .translation("mcabodyshapes.config.bust_physics_intensity")
                .defineInRange("bustPhysicsIntensity", 1.0d, 0.0d, 2.0d);
        SEAT_INTENSITY = builder
                .comment("Seat bounce intensity: 0 is none, 1 is the default, 2 is strong. Also runs with mcabp installed.")
                .translation("mcabodyshapes.config.seat_physics_intensity")
                .defineInRange("seatPhysicsIntensity", 1.0d, 0.0d, 2.0d);
        builder.pop();
        SPEC = builder.build();
    }

    private BodyShapeClientConfig() {
    }

    public static boolean physicsEnabled() {
        return SPEC.isLoaded() ? BODY_PHYSICS.get() : BODY_PHYSICS.getDefault();
    }

    public static float bustIntensity() {
        return (SPEC.isLoaded() ? BUST_INTENSITY.get() : BUST_INTENSITY.getDefault()).floatValue();
    }

    public static float seatIntensity() {
        return (SPEC.isLoaded() ? SEAT_INTENSITY.get() : SEAT_INTENSITY.getDefault()).floatValue();
    }
}
