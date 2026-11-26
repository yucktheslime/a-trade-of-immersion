package dev.yucktheslime.tradeofimmersion.body;

import dev.yucktheslime.tradeofimmersion.entity.CompanionEntity;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.minecraft.world.entity.Entity;

/**
 * Who among A Trade of Immersion's companions may receive a body shape.
 *
 * <p>A companion is shaped only when it is <b>explicitly female</b> and <b>explicitly adult</b>.
 * Gender is read from the companion's own gender or its saved body data, with no fallback to
 * {@code companionGender} in the config and no default of male. Age is the saved
 * {@link CompanionBodyData#ageState} (written as {@code adult} when a look is first generated).
 * If either value is missing or unrecognized, the companion stays unshaped.
 */
public final class CompanionShapeEligibility {
    private CompanionShapeEligibility() {
    }

    /** {@code true} only for an explicitly female, explicitly adult companion. */
    public static boolean isShapeable(Entity entity) {
        if (!(entity instanceof CompanionEntity companion) || companion.isBaby()) {
            return false;
        }
        return CompanionShapeNames.isShapeable(rawGender(companion), rawAge(companion), companion.isBaby());
    }

    /**
     * The companion's own gender string, or null if it was never set. Does not use the config default.
     */
    static String rawGender(CompanionEntity companion) {
        CompanionBodyData data = data(companion);
        if (data != null && data.gender != null && !data.gender.isBlank()) {
            return data.gender;
        }
        if (companion.getGender() != null && !companion.getGender().isBlank()) {
            return companion.getGender();
        }
        return null;
    }

    /** The saved age-state string, or null if it was never set. */
    static String rawAge(CompanionEntity companion) {
        CompanionBodyData data = data(companion);
        return data == null ? null : data.ageState;
    }

    public static Gender explicitGender(CompanionEntity companion) {
        return parseExplicitGender(rawGender(companion));
    }

    public static AgeState explicitAge(CompanionEntity companion) {
        return parseExplicitAge(rawAge(companion));
    }

    /** {@code null} when {@code raw} is blank or not a known gender (no default). */
    public static Gender parseExplicitGender(String raw) {
        String name = CompanionShapeNames.normalizeGender(raw);
        if (name == null) {
            return null;
        }
        return CompanionMcaData.parseGender(name, null);
    }

    /** {@code null} when {@code raw} is blank or not a known age state. */
    public static AgeState parseExplicitAge(String raw) {
        String name = CompanionShapeNames.normalizeAge(raw);
        if (name == null) {
            return null;
        }
        return switch (name) {
            case "adult" -> AgeState.ADULT;
            case "baby" -> AgeState.BABY;
            case "toddler" -> AgeState.TODDLER;
            case "child" -> AgeState.CHILD;
            case "teen" -> AgeState.TEEN;
            case "unassigned" -> AgeState.UNASSIGNED;
            default -> null;
        };
    }

    private static CompanionBodyData data(CompanionEntity companion) {
        if (companion.level().isClientSide) {
            return ClientCompanionBodies.get(companion.getUUID());
        }
        return CompanionBodyStore.get().find(companion.getUUID());
    }
}
