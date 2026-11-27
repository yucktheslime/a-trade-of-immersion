package dev.yucktheslime.mcabodyshapes.config;

import net.minecraft.util.Mth;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * Server / common settings: channel defaults, per-control slider ranges, and
 * named presets. Lives in {@code config/mcabodyshapes-common.toml}.
 *
 * <p>Slider ranges are in slider units (0–2, or 0–3 for rear depth). A config
 * can only narrow a range: every value is clamped in code to the hard slider
 * limits, and the geometry at the top of each slider is the hard cap (rear
 * depth peak 1.55, hip width +1px per side, thigh thickness +1px per side,
 * chest fullness at the existing bust maximum, waist narrowing about 0.5px
 * per side). Raising a max in the file past those limits has no effect.
 */
public final class BodyShapeCommonConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue DEFAULT_CURVES;
    public static final ModConfigSpec.DoubleValue DEFAULT_THIGHS;
    public static final ModConfigSpec.DoubleValue MAX_BELLY_DEPTH;
    public static final ModConfigSpec.ConfigValue<String> BOOBS;
    public static final ModConfigSpec.DoubleValue BOOBS_MCABP_CHANCE;
    /** Hard cap of the pregnancy belly's peak depth, in model pixels (phase 3). */
    public static final double BELLY_DEPTH_HARD_MAX = 2.5d;

    /** One entry per adjustable control; order matches {@link Control}. */
    private static final ModConfigSpec.DoubleValue[] MIN = new ModConfigSpec.DoubleValue[Control.values().length];
    private static final ModConfigSpec.DoubleValue[] MAX = new ModConfigSpec.DoubleValue[Control.values().length];

    public static final ModConfigSpec.ConfigValue<List<? extends String>> PRESETS;

    /**
     * The five independent figure controls. {@code channel} is the saved
     * field name, kept from earlier versions so old saves still load.
     */
    public enum Control {
        CHEST_FULLNESS("chestFullness", "bust", 2.0, "Bust Size (saved as 'bust'). 0 = normal MCA body, 2 = existing chest maximum; it cannot be raised."),
        WAIST_WIDTH("waistWidth", "waist", 2.0, "Waist width (saved as 'waist'). 0 = narrowest (about 0.5px per side narrower), 1 = MCA default, 2 = widest."),
        HIP_WIDTH("hipWidth", "hips", 2.0, "Hip width (saved as 'hips'). 1 = MCA default, 0 = slightly narrower, 2 = hard cap of +1px per side. Independent of rear depth."),
        REAR_DEPTH("rearDepth", "seat", 3.0, "Butt Size (saved as 'seat'). 0 = normal MCA body (no extra geometry), 3 = hard cap (peak depth 1.55). Independent of hip width."),
        THIGH_THICKNESS("thighThickness", "thighs", 2.0, "Thigh thickness (saved as 'thighs'). 0 = vanilla MCA legs, 2 = hard cap of +1px per side.");

        public final String configKey;
        public final String channel;
        public final double hardMax;
        public final String comment;

        Control(String configKey, String channel, double hardMax, String comment) {
            this.configKey = configKey;
            this.channel = channel;
            this.hardMax = hardMax;
            this.comment = comment;
        }
    }

    public static final List<String> DEFAULT_PRESETS = List.of(
            "subtle: bust_size=0.80, waist_width=0.90, hip_width=1.25, butt_size=1.25, thigh_thickness=1.00, curves=0.80",
            "pronounced: bust_size=1.60, waist_width=0.50, hip_width=2.00, butt_size=3.00, thigh_thickness=2.00, curves=1.40"
    );

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        BOOBS = builder
                .comment("Which bust model female NPCs (MCA villagers and A Trade of Immersion companions) draw. Exactly one is drawn.",
                        "random = each NPC is rolled once, the first time it is seen, and keeps it (see boobsMcabpChance).",
                        "toi    = everyone uses this mod's Body Shapes bust (and jiggle).",
                        "mcabp  = everyone uses MCA: Female Gender Physics' bust (and jiggle); needs it installed on the player's client,",
                        "         otherwise that player sees the TOI bust instead.",
                        "Players always use the toi bust. A forced value also replaces what /mcabody boobs set.")
                .translation("mcabodyshapes.config.boobs")
                // A validator, not defineInList: List.of(...).contains(null) throws when the key is missing from an older config file.
                .define("boobs", "random", o -> o instanceof String s
                        && (s.equalsIgnoreCase("random") || s.equalsIgnoreCase("toi") || s.equalsIgnoreCase("mcabp")));
        BOOBS_MCABP_CHANCE = builder
                .comment("With boobs = random: chance (0..1) that a newly rolled female NPC gets the mcabp bust. 0.5 is an even split.")
                .translation("mcabodyshapes.config.boobs_mcabp_chance")
                .defineInRange("boobsMcabpChance", 0.5d, 0.0d, 1.0d);

        DEFAULT_CURVES = builder
                .comment("Default Curves slider (0–2) used by Reset. Curves shapes the side profile of the hips: 0 is a straighter slope, 2 is rounder.")
                .translation("mcabodyshapes.config.default_curves")
                .defineInRange("defaultCurves", 0.0d, 0.0d, 2.0d);
        DEFAULT_THIGHS = builder
                .comment("Default Thigh thickness slider (0–2) used by Reset.")
                .translation("mcabodyshapes.config.default_thighs")
                .defineInRange("defaultThighs", 0.0d, 0.0d, 2.0d);

        MAX_BELLY_DEPTH = builder
                .comment("Pregnancy belly: highest peak depth, in model pixels, at the late phase (phase 3).",
                        "2.5 is the hard cap; a config can only lower it, never raise it. Phases 1 and 2 scale down with it.",
                        "The belly mesh uses thirds of pregnancy progress (0–1/3, 1/3–2/3, 2/3–1) and only lines up with A Trade of Immersion's default pregnancyPhaseCount of 3.")
                .translation("mcabodyshapes.config.max_belly_depth")
                .defineInRange("maxBellyDepth", BELLY_DEPTH_HARD_MAX, 0.0d, BELLY_DEPTH_HARD_MAX);

        builder.comment(
                "Per-control slider ranges, in slider units. Used by the Shape tab, /mcabody, presets,",
                "random rolls, and rendering. A config can lower a max (or raise a min) but never",
                "exceed the hard caps; values outside 0..hardMax are clamped in code."
        ).push("sliders");
        for (Control control : Control.values()) {
            builder.comment(control.comment).push(control.configKey);
            MIN[control.ordinal()] = builder
                    .comment("Lowest allowed value (0.." + control.hardMax + ").")
                    .defineInRange("min", 0.0d, 0.0d, control.hardMax);
            MAX[control.ordinal()] = builder
                    .comment("Highest allowed value (0.." + control.hardMax + "). " + control.hardMax + " is the hard cap.")
                    .defineInRange("max", control.hardMax, 0.0d, control.hardMax);
            builder.pop();
        }
        builder.pop();

        builder.comment(
                "Named presets for /mcabody preset <target> <name> and the Shape tab Preset button.",
                "Format: \"name: key=value, key=value\". Keys: chest_fullness, waist_width, hip_width,",
                "rear_depth, thigh_thickness, curves, weight, height (or the saved names bust, waist,",
                "hips, seat, thighs). Unlisted channels use the Reset defaults. Values are clamped to",
                "the slider ranges above, so a preset can never exceed the hard caps."
        ).push("presets");
        PRESETS = builder.defineListAllowEmpty("list", DEFAULT_PRESETS, () -> "name: hip_width=1.0",
                value -> value instanceof String s && s.contains(":"));
        builder.pop();

        SPEC = builder.build();
    }

    private BodyShapeCommonConfig() {
    }

    /** {@code random}, {@code toi} or {@code mcabp}; {@code random} if the config is not loaded yet. */
    public static String boobsMode() {
        try {
            return dev.yucktheslime.tradeofimmersion.body.BustModelRules.parseMode(BOOBS.get());
        } catch (IllegalStateException ignored) {
            return dev.yucktheslime.tradeofimmersion.body.BustModelRules.RANDOM;
        }
    }

    /** Chance (0..1) of the mcabp bust for a random roll; 0.5 if the config is not loaded yet. */
    public static double boobsMcabpChance() {
        try {
            return Mth.clamp(BOOBS_MCABP_CHANCE.get(), 0.0d, 1.0d);
        } catch (IllegalStateException ignored) {
            return 0.5d;
        }
    }

    /** Configured belly depth cap in px, always within 0..{@link #BELLY_DEPTH_HARD_MAX}. */
    public static float maxBellyDepth() {
        try {
            return (float) Mth.clamp(MAX_BELLY_DEPTH.get(), 0.0d, BELLY_DEPTH_HARD_MAX);
        } catch (IllegalStateException ignored) {
            return (float) BELLY_DEPTH_HARD_MAX;
        }
    }

    public static float defaultCurves() {
        try {
            return Mth.clamp(DEFAULT_CURVES.get().floatValue(), 0.0f, 2.0f);
        } catch (IllegalStateException ignored) {
            return 0.0f;
        }
    }

    public static float defaultThighs() {
        try {
            return Mth.clamp(DEFAULT_THIGHS.get().floatValue(), 0.0f, 2.0f);
        } catch (IllegalStateException ignored) {
            return 0.0f;
        }
    }

    /** Configured min, clamped to 0..hardMax. 0 if the config is not loaded yet. */
    public static float min(Control control) {
        try {
            float v = MIN[control.ordinal()].get().floatValue();
            return Mth.clamp(v, 0.0f, (float) control.hardMax);
        } catch (IllegalStateException | NullPointerException ignored) {
            return 0.0f;
        }
    }

    /** Configured max, clamped to min..hardMax. The hard cap if not loaded yet. */
    public static float max(Control control) {
        try {
            float v = MAX[control.ordinal()].get().floatValue();
            return Mth.clamp(v, min(control), (float) control.hardMax);
        } catch (IllegalStateException | NullPointerException ignored) {
            return (float) control.hardMax;
        }
    }

    public static List<String> presets() {
        try {
            return new ArrayList<>(PRESETS.get());
        } catch (IllegalStateException | NullPointerException ignored) {
            return new ArrayList<>(DEFAULT_PRESETS);
        }
    }
}
