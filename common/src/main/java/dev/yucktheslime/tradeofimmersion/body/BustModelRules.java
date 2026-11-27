package dev.yucktheslime.tradeofimmersion.body;

import java.util.Locale;

/**
 * Loader-agnostic rules for which bust model a female NPC uses: TOI's own Body Shapes mesh
 * ({@code toi}) or the mesh of MCA: Female Gender Physics ({@code mcabp}). Exactly one is ever drawn.
 *
 * <p>The choice is rolled once on the server and stored per entity; the {@code boobs} config
 * ({@code random}, {@code toi}, {@code mcabp}) either keeps that random roll or forces everyone.
 * Clients then resolve the stored value against what they have installed
 * ({@link #rendersMcabp}), so a client without mcabp always draws TOI's bust.
 */
public final class BustModelRules {
    public static final String TOI = "toi";
    public static final String MCABP = "mcabp";
    public static final String RANDOM = "random";

    private BustModelRules() {
    }

    /** {@code toi} or {@code mcabp} (any case); anything else is {@code null}. */
    public static String parseModel(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        return s.equals(TOI) || s.equals(MCABP) ? s : null;
    }

    /** {@code toi}, {@code mcabp} or {@code random}; unknown values mean {@code random}. */
    public static String parseMode(String raw) {
        String model = parseModel(raw);
        return model != null ? model : RANDOM;
    }

    /** Rolls a model: a forced mode wins, otherwise mcabp when {@code roll01 < mcabpChance}. */
    public static String roll(String mode, double mcabpChance, double roll01) {
        String forced = parseModel(mode);
        if (forced != null) {
            return forced;
        }
        double chance = Double.isFinite(mcabpChance) ? Math.max(0.0, Math.min(1.0, mcabpChance)) : 0.5;
        return roll01 < chance ? MCABP : TOI;
    }

    /**
     * The model to store for an entity: the stored one if it is valid and the mode does not force
     * another; the forced one if the mode forces; otherwise a fresh roll.
     */
    public static String resolveStored(String stored, String mode, double mcabpChance, double roll01) {
        String forced = parseModel(mode);
        if (forced != null) {
            return forced;
        }
        String valid = parseModel(stored);
        return valid != null ? valid : roll(mode, mcabpChance, roll01);
    }

    /**
     * Client-side: does this entity draw mcabp's bust (and so hide TOI's mesh)?
     *
     * @param mcabpRenderingEnabled mcabp's master rendering toggle; {@code null} if unreadable (treated as on)
     * @param player                players never use the mcabp model
     */
    public static boolean rendersMcabp(String stored, boolean mcabpLoaded, Boolean mcabpRenderingEnabled, boolean player) {
        if (player || !mcabpLoaded || Boolean.FALSE.equals(mcabpRenderingEnabled)) {
            return false;
        }
        return MCABP.equals(parseModel(stored));
    }

    /** MCA breast gene for a Bust Size slider (0..2): the 0..1 gene range covers the whole slider. */
    public static float mcabpGene(float bust) {
        float gene = bust * 0.5f;
        return Float.isFinite(gene) ? Math.max(0.0f, Math.min(1.0f, gene)) : 0.0f;
    }

    /** Gene while TOI's own mesh is the bust: 0, so mcabp draws nothing (no double bust). */
    public static float toiGene() {
        return 0.0f;
    }

    /** The gene to write for a stored model and Bust Size. */
    public static float gene(String storedModel, float bust) {
        return MCABP.equals(parseModel(storedModel)) ? mcabpGene(bust) : toiGene();
    }
}
