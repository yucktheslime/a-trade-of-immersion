package dev.yucktheslime.mcabodyshapes.body;

import dev.yucktheslime.mcabodyshapes.config.BodyShapeCommonConfig;
import dev.yucktheslime.mcabodyshapes.config.BodyShapeCommonConfig.Control;
import net.minecraft.util.Mth;

/**
 * Applies the configured per-control slider ranges. Ranges can only narrow
 * the hard 0..max slider limits (see {@link BodyShapeCommonConfig}), so the
 * geometry caps can never be raised by a config file.
 */
public final class BodyShapeLimits {
    private BodyShapeLimits() {
    }

    public static Control control(BodyShape.Channel channel) {
        return switch (channel) {
            case BUST -> Control.CHEST_FULLNESS;
            case WAIST -> Control.WAIST_WIDTH;
            case HIPS -> Control.HIP_WIDTH;
            case SEAT -> Control.REAR_DEPTH;
            case THIGHS -> Control.THIGH_THICKNESS;
            default -> null;
        };
    }

    public static float min(BodyShape.Channel channel) {
        Control control = control(channel);
        return control == null ? BodyShape.MIN : BodyShapeCommonConfig.min(control);
    }

    public static float max(BodyShape.Channel channel) {
        Control control = control(channel);
        float hard = BodyShape.max(channel);
        return control == null ? hard : Math.min(hard, BodyShapeCommonConfig.max(control));
    }

    public static float clamp(BodyShape.Channel channel, float value) {
        float lo = min(channel);
        float hi = Math.max(lo, max(channel));
        return Mth.clamp(BodyShape.clamp(channel, value), lo, hi);
    }

    public static BodyShape clamp(BodyShape shape) {
        BodyShape out = shape;
        for (BodyShape.Channel channel : new BodyShape.Channel[] {
                BodyShape.Channel.BUST, BodyShape.Channel.WAIST, BodyShape.Channel.HIPS,
                BodyShape.Channel.SEAT, BodyShape.Channel.THIGHS}) {
            float v = shape.get(channel);
            float c = clamp(channel, v);
            if (c != v) {
                out = out.with(channel, c);
            }
        }
        return out;
    }
}
