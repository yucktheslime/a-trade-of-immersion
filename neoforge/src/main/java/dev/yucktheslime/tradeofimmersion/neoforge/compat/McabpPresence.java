package dev.yucktheslime.tradeofimmersion.neoforge.compat;

import net.neoforged.fml.ModList;

/**
 * Presence check for "MCA: Female Gender Physics" (mod id {@code mcabp}). Kept separate from
 * {@link FemaleGenderPhysicsCompat}: loading that class makes the JVM verifier resolve the addon's classes, which
 * crashes with NoClassDefFoundError when the addon is not installed.
 */
public final class McabpPresence {
    public static final String MOD_ID = "mcabp";
    private static Boolean loaded;

    private McabpPresence() {
    }

    public static boolean loaded() {
        if (loaded == null) loaded = ModList.get() != null && ModList.get().isLoaded(MOD_ID);
        return loaded;
    }
}
