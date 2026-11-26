package dev.yucktheslime.mcabodyshapes.compat;

import dev.yucktheslime.mcabodyshapes.McaBodyShapes;
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
 *   <li>the bust slider is always written through to {@link Genetics#BREAST}
 *       (on spawn and when edited), so both this layer and MCA's built-in
 *       breasts follow the same size</li>
 * </ul>
 *
 * <p>No hard or compile-time dependency: detection is {@link ModList#isLoaded}.
 */
public final class McaPhysicsCompat {
    public static final String MOD_ID = "mcabp";

    private static boolean loaded;

    private McaPhysicsCompat() {
    }

    public static void probe() {
        loaded = ModList.get().isLoaded(MOD_ID);
        if (loaded) {
            McaBodyShapes.LOGGER.info("Detected MCA: Female Gender Physics ({}). Bust slider will drive MCA's breast gene and will not scale MCA's built-in bust mesh.", MOD_ID);
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
        if (!loaded) {
            return true;
        }
        try {
            Class<?> cls = Class.forName("com.mcabp.wildfire.main.config.GeneralClientConfig");
            Object instance = cls.getField("INSTANCE").get(null);
            Object value = cls.getField("physicsEnabled").get(instance);
            Object result = value.getClass().getMethod("get").invoke(value);
            return result instanceof Boolean enabled ? enabled : true;
        } catch (Throwable ignored) {
            return true;
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
                return (float) (number.doubleValue() / 0.5d);
            }
        } catch (Throwable ignored) {
            // Standalone intensity is used as-is.
        }
        return 1.0f;
    }
}
