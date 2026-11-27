package dev.yucktheslime.mcabodyshapes.compat;

import dev.yucktheslime.mcabodyshapes.McaBodyShapes;
import dev.yucktheslime.tradeofimmersion.body.physics.PhysicsGating;
import dev.yucktheslime.mcabodyshapes.body.BodyShape;
import net.conczin.mca.entity.ai.Genetics;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.ModList;

/**
 * Soft hook for <em>MCA: Female Gender Physics</em> ({@code mcabp}).
 *
 * <p>That add-on renders its own bust mesh with spring-damper physics and reads size
 * from MCA's {@link Genetics#BREAST} gene plus its own {@code BREAST_GROWTH} gene.
 * When it is loaded we:
 * <ul>
 *   <li>skip scaling MCA's built-in bust cubes, so the two meshes don't stack</li>
 *   <li>keep {@link Genetics#BREAST} at 0 so mcabp does not draw a second bust</li>
 *   <li>still jiggle TOI's own {@code BustRenderer} mesh ({@code BodyPhysics}),
 *       optionally scaled by mcabp's bounce multiplier and master toggle</li>
 * </ul>
 *
 * <p>No hard or compile-time dependency: detection is {@link ModList#isLoaded}.
 * A missing or failed config reflection never disables TOI physics.
 */
public final class McaPhysicsCompat {
    public static final String MOD_ID = "mcabp";

    private static boolean loaded;

    private McaPhysicsCompat() {
    }

    public static void probe() {
        loaded = ModList.get().isLoaded(MOD_ID);
        if (loaded) {
            McaBodyShapes.LOGGER.info("Detected MCA: Female Gender Physics ({}). Breast gene stays at 0 so its bust stays flat; TOI's BustRenderer provides the jiggle.", MOD_ID);
        }
    }

    public static boolean isLoaded() {
        return loaded;
    }

    /**
     * Maps our 0–2 bust slider onto MCA's 0–1 breast gene (1.0 slider → 0.5 gene).
     */
    public static void applyBustToGenetics(LivingEntity entity, BodyShape shape) {
        // Always write MCA's breast gene, not only when mcabp is loaded,
        // so bust variety is visible with the built-in breast cubes too.
        dev.yucktheslime.mcabodyshapes.body.BodyShapeWrites.writeBustGene(entity, shape);
    }

    /**
     * When mcabp is present, honor its master physics toggle. Missing or
     * unreadable config is treated as enabled.
     */
    public static boolean effectivePhysicsEnabled() {
        return !Boolean.FALSE.equals(physicsEnabledOrNull());
    }

    /**
     * mcabp's master physics toggle, or {@code null} if mcabp is absent or the
     * value could not be read. {@code null} must be treated as enabled.
     */
    public static Boolean physicsEnabledOrNull() {
        if (!loaded) {
            return null;
        }
        try {
            Class<?> cls = Class.forName("com.mcabp.wildfire.main.config.GeneralClientConfig");
            Object instance = cls.getField("INSTANCE").get(null);
            Object value = cls.getField("physicsEnabled").get(instance);
            Object result = value.getClass().getMethod("get").invoke(value);
            return result instanceof Boolean enabled ? enabled : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Maps mcabp's 0–1 bounceMultiplier (default 0.5) onto a 1.0 scale factor.
     * Returns 1 when mcabp is absent or the value cannot be read.
     */
    public static float effectiveBounceScale() {
        if (!loaded) {
            return 1.0f;
        }
        try {
            Class<?> cls = Class.forName("com.mcabp.wildfire.main.config.GeneralClientConfig");
            Object instance = cls.getField("INSTANCE").get(null);
            Object value = cls.getField("bounceMultiplier").get(instance);
            Object result = value.getClass().getMethod("get").invoke(value);
            if (result instanceof Number number) {
                return PhysicsGating.bounceScale(number.doubleValue());
            }
        } catch (Throwable ignored) {
            // Standalone intensity is used as-is.
        }
        return 1.0f;
    }
}
