package dev.yucktheslime.mcabodyshapes.compat;

import dev.yucktheslime.mcabodyshapes.McaBodyShapes;
import dev.yucktheslime.mcabodyshapes.body.BodyShape;
import net.conczin.mca.entity.ai.Genetics;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.ModList;

/**
 * Soft hook for <em>MCA: Female Gender Physics</em> ({@code mcabp}).
 *
 * <p>Since 0.6.2 every female NPC draws exactly one bust model, chosen per entity ({@code BustModels}):
 * <ul>
 *   <li>{@code toi}: TOI's own {@code BustRenderer} mesh and {@code BodyPhysics} jiggle; the breast gene is 0
 *       so mcabp draws nothing</li>
 *   <li>{@code mcabp}: TOI's mesh is hidden, the breast gene carries the Bust Size, and mcabp draws and jiggles it</li>
 * </ul>
 * mcabp is read by reflection only (no compile-time dependency); a missing or failed read never disables anything.
 */
public final class McaPhysicsCompat {
    public static final String MOD_ID = "mcabp";

    private static boolean loaded;

    private McaPhysicsCompat() {
    }

    public static void probe() {
        loaded = ModList.get().isLoaded(MOD_ID);
        if (loaded) {
            McaBodyShapes.LOGGER.info("Detected MCA: Female Gender Physics ({}). Female NPCs are rolled between its bust and TOI's own (boobs config); players use TOI's.", MOD_ID);
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
     * mcabp's master physics toggle, or {@code null} if mcabp is absent or the
     * value could not be read. {@code null} must be treated as enabled.
     */
    public static Boolean physicsEnabledOrNull() {
        return readBoolean("physicsEnabled");
    }

    /** mcabp's master rendering toggle, or {@code null} if absent or unreadable ({@code null} means on). */
    public static Boolean renderingEnabledOrNull() {
        return readBoolean("renderingEnabled");
    }

    private static Boolean readBoolean(String field) {
        if (!loaded) {
            return null;
        }
        try {
            Class<?> cls = Class.forName("com.mcabp.wildfire.main.config.GeneralClientConfig");
            Object instance = cls.getField("INSTANCE").get(null);
            Object value = cls.getField(field).get(instance);
            Object result = value.getClass().getMethod("get").invoke(value);
            return result instanceof Boolean enabled ? enabled : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** What mcabp itself currently holds for one entity (for {@code /mcabody physics}). */
    public record McabpState(boolean supported, String gender, float bustSize, float physicsY, float physicsX) {
    }

    /**
     * Reads mcabp's live per-entity state by reflection: whether it treats the entity as supported (and so
     * ticks its physics), its gender, bust size, and the current physics offset. {@code null} if mcabp is
     * absent or the read fails.
     */
    public static McabpState probeEntity(LivingEntity entity) {
        if (!loaded) {
            return null;
        }
        try {
            Class<?> cfg = Class.forName("com.mcabp.wildfire.main.entitydata.EntityConfig");
            boolean supported = (Boolean) cfg.getMethod("isSupportedEntity", LivingEntity.class).invoke(null, entity);
            if (!supported) {
                return new McabpState(false, "-", 0.0f, 0.0f, 0.0f);
            }
            Object config = cfg.getMethod("getEntity", LivingEntity.class).invoke(null, entity);
            Object gender = cfg.getMethod("getGender").invoke(config);
            float bust = (Float) cfg.getMethod("getBustSize").invoke(config);
            Object left = cfg.getMethod("getLeftBreastPhysics").invoke(config);
            float y = (Float) left.getClass().getMethod("getPositionY").invoke(left);
            float x = (Float) left.getClass().getMethod("getPositionX").invoke(left);
            return new McabpState(true, String.valueOf(gender), bust, y, x);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
