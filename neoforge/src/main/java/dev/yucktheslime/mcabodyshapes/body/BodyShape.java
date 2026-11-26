package dev.yucktheslime.mcabodyshapes.body;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

/**
 * Per-entity clothed body proportions. Most channels are 0 (minimum) to 2
 * (maximum), with 1 meaning "leave MCA's default look alone". The seat
 * (butt) channel runs 0–3: 0 is fully flat (no seat geometry), 1 is the
 * default modest bulge, 2 is the previous full seat, and 3 is extra thick.
 *
 * <p>Since 0.5.1 the two headline controls are <b>Bust Size</b> ({@code bust},
 * 0–2, 0 = normal MCA body) and <b>Butt Size</b> ({@code seat}, 0–3, 0 =
 * normal MCA body, peak rear depth 1.55). Hip width (+1px/side cap), waist
 * width, and thigh thickness (+1px/side cap) stay as separate secondary
 * controls. The saved field names are unchanged so 0.4.x / 0.5.0 data loads.
 */
public record BodyShape(
        float bust,
        float hips,
        float seat,
        float waist,
        float weight,
        float muscle,
        float shoulders,
        float chest,
        float gut,
        float height,
        float curves,
        float thighs
) {
    public static final float MIN = 0.0f;
    public static final float MAX = 2.0f;
    public static final float SEAT_MAX = 3.0f;
    public static final float DEFAULT = 1.0f;

    /** Curves 0 is the straight, pre-hourglass look (existing 0.4.8 cubes). */
    public static final float CURVES_DEFAULT = 0.0f;

    /** Vanilla MCA body: Bust Size 0, Butt Size 0, Thighs 0, other sliders 1. */
    public static final BodyShape VANILLA = new BodyShape(
            MIN, DEFAULT, MIN, DEFAULT, DEFAULT,
            DEFAULT, DEFAULT, DEFAULT, DEFAULT, DEFAULT,
            CURVES_DEFAULT, MIN
    );

    /**
     * Reset / vanilla shape, with curves taken from the common config default
     * (0 if the config is not loaded yet).
     */
    public static BodyShape defaults() {
        float curves = CURVES_DEFAULT;
        float thighs = MIN;
        try {
            curves = dev.yucktheslime.mcabodyshapes.config.BodyShapeCommonConfig.defaultCurves();
            thighs = dev.yucktheslime.mcabodyshapes.config.BodyShapeCommonConfig.defaultThighs();
        } catch (Throwable ignored) {
        }
        return VANILLA.with(Channel.CURVES, curves).with(Channel.THIGHS, thighs);
    }

    public static final Codec<BodyShape> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("bust", DEFAULT).forGetter(BodyShape::bust),
            Codec.FLOAT.optionalFieldOf("hips", DEFAULT).forGetter(BodyShape::hips),
            Codec.FLOAT.optionalFieldOf("seat", DEFAULT).forGetter(BodyShape::seat),
            Codec.FLOAT.optionalFieldOf("waist", DEFAULT).forGetter(BodyShape::waist),
            Codec.FLOAT.optionalFieldOf("weight", DEFAULT).forGetter(BodyShape::weight),
            Codec.FLOAT.optionalFieldOf("muscle", DEFAULT).forGetter(BodyShape::muscle),
            Codec.FLOAT.optionalFieldOf("shoulders", DEFAULT).forGetter(BodyShape::shoulders),
            Codec.FLOAT.optionalFieldOf("chest", DEFAULT).forGetter(BodyShape::chest),
            Codec.FLOAT.optionalFieldOf("gut", DEFAULT).forGetter(BodyShape::gut),
            Codec.FLOAT.optionalFieldOf("height", DEFAULT).forGetter(BodyShape::height),
            // Missing field (0.4.8 saves) stays at 0 so existing bodies keep
            // their straight, non-flared seat and hips.
            Codec.FLOAT.optionalFieldOf("curves", CURVES_DEFAULT).forGetter(BodyShape::curves),
            // Missing field (0.4.9 saves) stays at 1 so existing bodies keep
            // the natural butt-to-thigh proportion.
            Codec.FLOAT.optionalFieldOf("thighs", DEFAULT).forGetter(BodyShape::thighs)
    ).apply(instance, BodyShape::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, BodyShape> STREAM_CODEC = StreamCodec.of(
            (buf, shape) -> {
                buf.writeFloat(shape.bust());
                buf.writeFloat(shape.hips());
                buf.writeFloat(shape.seat());
                buf.writeFloat(shape.waist());
                buf.writeFloat(shape.weight());
                buf.writeFloat(shape.muscle());
                buf.writeFloat(shape.shoulders());
                buf.writeFloat(shape.chest());
                buf.writeFloat(shape.gut());
                buf.writeFloat(shape.height());
                buf.writeFloat(shape.curves());
                buf.writeFloat(shape.thighs());
            },
            buf -> new BodyShape(
                    buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                    buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                    buf.readFloat(), buf.readFloat()
            )
    );

    public BodyShape {
        bust = clamp(bust);
        hips = clamp(hips);
        seat = clamp(Channel.SEAT, seat);
        waist = clamp(waist);
        weight = clamp(weight);
        muscle = clamp(muscle);
        shoulders = clamp(shoulders);
        chest = clamp(chest);
        gut = clamp(gut);
        height = clamp(height);
        curves = clamp(curves);
        thighs = clamp(thighs);
    }

    public static float clamp(float value) {
        return Mth.clamp(value, MIN, MAX);
    }

    public static float max(Channel channel) {
        return channel == Channel.SEAT ? SEAT_MAX : MAX;
    }

    public static float clamp(Channel channel, float value) {
        return Mth.clamp(value, MIN, max(channel));
    }

    public BodyShape with(Channel channel, float value) {
        float v = clamp(channel, value);
        return switch (channel) {
            case BUST -> new BodyShape(v, hips, seat, waist, weight, muscle, shoulders, chest, gut, height, curves, thighs);
            case HIPS -> new BodyShape(bust, v, seat, waist, weight, muscle, shoulders, chest, gut, height, curves, thighs);
            case SEAT -> new BodyShape(bust, hips, v, waist, weight, muscle, shoulders, chest, gut, height, curves, thighs);
            case WAIST -> new BodyShape(bust, hips, seat, v, weight, muscle, shoulders, chest, gut, height, curves, thighs);
            case WEIGHT -> new BodyShape(bust, hips, seat, waist, v, muscle, shoulders, chest, gut, height, curves, thighs);
            case MUSCLE -> new BodyShape(bust, hips, seat, waist, weight, v, shoulders, chest, gut, height, curves, thighs);
            case SHOULDERS -> new BodyShape(bust, hips, seat, waist, weight, muscle, v, chest, gut, height, curves, thighs);
            case CHEST -> new BodyShape(bust, hips, seat, waist, weight, muscle, shoulders, v, gut, height, curves, thighs);
            case GUT -> new BodyShape(bust, hips, seat, waist, weight, muscle, shoulders, chest, v, height, curves, thighs);
            case HEIGHT -> new BodyShape(bust, hips, seat, waist, weight, muscle, shoulders, chest, gut, v, curves, thighs);
            case CURVES -> new BodyShape(bust, hips, seat, waist, weight, muscle, shoulders, chest, gut, height, v, thighs);
            case THIGHS -> new BodyShape(bust, hips, seat, waist, weight, muscle, shoulders, chest, gut, height, curves, v);
        };
    }

    public float get(Channel channel) {
        return switch (channel) {
            case BUST -> bust;
            case HIPS -> hips;
            case SEAT -> seat;
            case WAIST -> waist;
            case WEIGHT -> weight;
            case MUSCLE -> muscle;
            case SHOULDERS -> shoulders;
            case CHEST -> chest;
            case GUT -> gut;
            case HEIGHT -> height;
            case CURVES -> curves;
            case THIGHS -> thighs;
        };
    }

    public boolean isVanilla() {
        return equals(VANILLA);
    }

    /**
     * Returns saved shape data if present, otherwise {@link #VANILLA}, without writing
     * a default onto the entity. Writing a default onto a child would block random
     * assignment later, when that villager becomes an adult.
     */
    public static BodyShape get(Entity entity) {
        return entity.getExistingData(BodyShapeAttachments.BODY_SHAPE).orElse(VANILLA);
    }

    public static boolean hasSaved(Entity entity) {
        return entity.hasData(BodyShapeAttachments.BODY_SHAPE);
    }

    public static void set(Entity entity, BodyShape shape) {
        entity.setData(BodyShapeAttachments.BODY_SHAPE, shape);
    }

    /**
     * True when every channel is still at {@link #DEFAULT}. Used to detect
     * untouched 0.3.0 adults that never received a real roll.
     */
    public boolean isNearDefault() {
        return maxDeviationFromDefault() <= 0.20f;
    }

    public float maxDeviationFromDefault() {
        float max = 0.0f;
        for (Channel channel : Channel.values()) {
            if (channel == Channel.CURVES) {
                // Curves 0 is the historical default; do not treat it as
                // "untouched 0.3.0 sliders still at 1.0".
                continue;
            }
            max = Math.max(max, Math.abs(get(channel) - DEFAULT));
        }
        return max;
    }

    /**
     * A clearly varied adult body. Values use a wide range (not a tight
     * Gaussian around 1.0) so bust, hips, seat, and waist are visibly
     * different from villager to villager. Weight still tints hips, seat, and
     * gut so the figure stays coherent.
     */
    public static BodyShape random(RandomSource random) {
        float weight = sampleWide(random, 0.70f);
        float hips = clamp(sampleWide(random, 0.80f) + (weight - DEFAULT) * 0.20f);
        float seat = clamp(Channel.SEAT, sampleSeat(random) + (weight - DEFAULT) * 0.18f + (hips - DEFAULT) * 0.12f);
        float bust = sampleWide(random, 0.85f);
        float waist = sampleWide(random, 0.85f);
        float muscle = sampleWide(random, 0.70f);
        float shoulders = clamp(sampleWide(random, 0.65f) + (muscle - DEFAULT) * 0.20f);
        float chest = clamp(sampleWide(random, 0.65f) + (muscle - DEFAULT) * 0.15f);
        float gut = clamp(sampleWide(random, 0.70f) + (weight - DEFAULT) * 0.25f);
        float height = sampleWide(random, 0.55f);
        float curves = sampleCurves(random);
        float thighs = clamp(sampleWide(random, 0.80f) + (hips - DEFAULT) * 0.10f + (seat - DEFAULT) * 0.06f);
        return new BodyShape(bust, hips, seat, waist, weight, muscle, shoulders, chest, gut, height, curves, thighs);
    }

    /**
     * Seat rolls stay mostly in the mid band (around the default modest
     * bulge). Flat (near 0) and extra-thick (near 3) results are uncommon.
     */
    /**
     * A real spread across 0–2: some straight, some fully curvy, the rest
     * anywhere in between. Does not use {@link #sampleWide}.
     */
    private static float sampleCurves(RandomSource random) {
        float roll = random.nextFloat();
        if (roll < 0.18f) {
            return random.nextFloat() * 0.30f;
        }
        if (roll < 0.36f) {
            return 1.55f + random.nextFloat() * (MAX - 1.55f);
        }
        return random.nextFloat() * MAX;
    }

    private static float sampleSeat(RandomSource random) {
        float roll = random.nextFloat();
        if (roll < 0.06f) {
            return random.nextFloat() * 0.35f;
        }
        if (roll < 0.12f) {
            return 2.15f + random.nextFloat() * (SEAT_MAX - 2.15f);
        }
        return sampleWide(random, 0.70f);
    }

    /**
     * Spread a channel across most of 0–2. 35% of rolls use the full min–max
     * range so strong features actually appear; the rest are a wide uniform
     * band around the default (about 0.3–1.7 for {@code halfRange} 0.7+).
     */
    private static float sampleWide(RandomSource random, float halfRange) {
        if (random.nextFloat() < 0.35f) {
            float min = Math.max(MIN, DEFAULT - halfRange);
            float max = Math.min(MAX, DEFAULT + halfRange);
            return min + random.nextFloat() * (max - min);
        }
        return clamp(DEFAULT + (random.nextFloat() - 0.5f) * 2.0f * halfRange * 0.85f);
    }

    public enum Channel {
        BUST("bust", "bust_size", "chest_fullness"),
        HIPS("hips", "hip_width"),
        SEAT("seat", "butt_size", "rear_depth"),
        WAIST("waist", "waist_width"),
        WEIGHT("weight", null),
        MUSCLE("muscle", null),
        SHOULDERS("shoulders", null),
        CHEST("chest", null),
        GUT("gut", null),
        HEIGHT("height", null),
        CURVES("curves", null),
        THIGHS("thighs", "thigh_thickness");

        private final String key;
        private final String alias;
        private final String[] extraAliases;

        Channel(String key, String alias, String... extraAliases) {
            this.key = key;
            this.alias = alias;
            this.extraAliases = extraAliases;
        }

        /** Saved field name (kept from earlier versions so old saves load). */
        public String key() {
            return key;
        }

        /** Primary descriptive command / preset name, or null if none. */
        public String alias() {
            return alias;
        }

        /** Accepts the saved key or any descriptive alias (e.g. bust_size, butt_size). */
        public static Channel byKey(String key) {
            for (Channel channel : values()) {
                if (channel.key.equalsIgnoreCase(key)
                        || (channel.alias != null && channel.alias.equalsIgnoreCase(key))) {
                    return channel;
                }
                for (String extra : channel.extraAliases) {
                    if (extra.equalsIgnoreCase(key)) {
                        return channel;
                    }
                }
            }
            return null;
        }
    }
}
