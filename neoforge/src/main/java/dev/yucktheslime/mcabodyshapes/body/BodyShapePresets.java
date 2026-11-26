package dev.yucktheslime.mcabodyshapes.body;

import dev.yucktheslime.mcabodyshapes.McaBodyShapes;
import dev.yucktheslime.mcabodyshapes.config.BodyShapeCommonConfig;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Named starting points for the sliders. Presets from
 * {@code mcabodyshapes-common.toml} (by default {@code subtle} and
 * {@code pronounced}) come first and override a built-in of the same name.
 * Every preset is clamped to the configured slider ranges when it is applied,
 * so none can exceed the hard caps.
 */
public final class BodyShapePresets {
    private static final Map<String, BodyShape> BUILT_IN = new LinkedHashMap<>();

    static {
        // Order: bust, hips, seat, waist, weight, muscle, shoulders, chest, gut, height, curves, thighs.
        BUILT_IN.put("thick_snatched", new BodyShape(1.35f, 1.55f, 1.45f, 0.25f, 1.25f, 1.10f, 1.15f, 1.20f, 0.90f, 1.00f, 1.70f, 1.15f));
        BUILT_IN.put("full_no_waist", new BodyShape(1.25f, 1.65f, 1.55f, 2.0f, 1.75f, 1.05f, 1.15f, 1.25f, 1.70f, 1.00f, 1.40f, 1.20f));
        BUILT_IN.put("seat_no_bust", new BodyShape(0.0f, 1.25f, 1.85f, 0.85f, 1.05f, 1.00f, 1.00f, 0.90f, 1.10f, 1.00f, 1.80f, 1.15f));
        BUILT_IN.put("bust_no_seat", new BodyShape(1.85f, 0.95f, 0.0f, 0.65f, 1.0f, 1.00f, 1.05f, 1.10f, 0.85f, 1.00f, 0.00f, 1.00f));
    }

    private BodyShapePresets() {
    }

    /** Preset clamped to the current slider ranges, or null if unknown. */
    public static BodyShape get(String id) {
        String key = id.toLowerCase(Locale.ROOT);
        BodyShape shape = configured().get(key);
        if (shape == null) {
            shape = BUILT_IN.get(key);
        }
        return shape == null ? null : BodyShapeLimits.clamp(shape);
    }

    public static Set<String> ids() {
        Set<String> ids = new LinkedHashSet<>(configured().keySet());
        ids.addAll(BUILT_IN.keySet());
        return ids;
    }

    /** Config preset names only, in file order (used by the Shape tab Preset button). */
    public static List<String> configuredIds() {
        return List.copyOf(configured().keySet());
    }

    private static Map<String, BodyShape> configured() {
        Map<String, BodyShape> out = new LinkedHashMap<>();
        for (String line : BodyShapeCommonConfig.presets()) {
            try {
                parse(line, out);
            } catch (RuntimeException ex) {
                McaBodyShapes.LOGGER.warn("Ignoring bad preset '{}': {}", line, ex.getMessage());
            }
        }
        return out;
    }

    static void parse(String line, Map<String, BodyShape> out) {
        int colon = line.indexOf(':');
        if (colon <= 0) {
            throw new IllegalArgumentException("missing 'name:'");
        }
        String name = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
        if (name.isEmpty() || !name.matches("[a-z0-9_\\-]+")) {
            throw new IllegalArgumentException("bad name");
        }
        BodyShape shape = BodyShape.defaults();
        String body = line.substring(colon + 1).trim();
        if (!body.isEmpty()) {
            for (String part : body.split(",")) {
                String p = part.trim();
                if (p.isEmpty()) {
                    continue;
                }
                int eq = p.indexOf('=');
                if (eq <= 0) {
                    throw new IllegalArgumentException("expected key=value in '" + p + "'");
                }
                String key = p.substring(0, eq).trim();
                BodyShape.Channel channel = BodyShape.Channel.byKey(key);
                if (channel == null) {
                    throw new IllegalArgumentException("unknown key '" + key + "'");
                }
                float value = Float.parseFloat(p.substring(eq + 1).trim());
                shape = shape.with(channel, value);
            }
        }
        out.put(name, shape);
    }
}
