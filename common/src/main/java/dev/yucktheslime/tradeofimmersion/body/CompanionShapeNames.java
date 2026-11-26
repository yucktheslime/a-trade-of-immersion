package dev.yucktheslime.tradeofimmersion.body;

import java.util.Locale;

/**
 * String forms of a companion's explicit gender and age, with no defaults.
 * {@link CompanionShapeEligibility} maps these onto MCA's enums.
 */
public final class CompanionShapeNames {
    private CompanionShapeNames() {
    }

    /** {@code female}, {@code male}, {@code neutral}, or {@code null} if unset or unrecognized. */
    public static String normalizeGender(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "female", "f", "woman" -> "female";
            case "male", "m", "man" -> "male";
            case "neutral", "n" -> "neutral";
            default -> null;
        };
    }

    /**
     * {@code adult}, {@code baby}, {@code toddler}, {@code child}, {@code teen}, {@code unassigned},
     * or {@code null} if unset or unrecognized.
     */
    public static String normalizeAge(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "adult" -> "adult";
            case "baby" -> "baby";
            case "toddler" -> "toddler";
            case "child" -> "child";
            case "teen", "teenager" -> "teen";
            case "unassigned" -> "unassigned";
            default -> null;
        };
    }

    /** Only an explicit female adult that is not a baby is shapeable. Unknown values stay unshaped. */
    public static boolean isShapeable(String gender, String age, boolean isBaby) {
        return !isBaby
                && "female".equals(normalizeGender(gender))
                && "adult".equals(normalizeAge(age));
    }
}
