package dev.yucktheslime.tradeofimmersion.body;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanionShapeEligibilityTest {

    @Test
    void onlyExplicitFemaleAdultsAreShapeable() {
        assertTrue(CompanionShapeNames.isShapeable("female", "adult", false));
        assertTrue(CompanionShapeNames.isShapeable("F", "ADULT", false));
        assertTrue(CompanionShapeNames.isShapeable("woman", "adult", false));
    }

    @Test
    void unknownGenderOrAgeIsNotShapeable() {
        assertFalse(CompanionShapeNames.isShapeable(null, "adult", false));
        assertFalse(CompanionShapeNames.isShapeable("female", null, false));
        assertFalse(CompanionShapeNames.isShapeable(null, null, false));
        assertFalse(CompanionShapeNames.isShapeable("", "adult", false));
        assertFalse(CompanionShapeNames.isShapeable("female", "", false));
        assertFalse(CompanionShapeNames.isShapeable("unknown", "adult", false));
        assertFalse(CompanionShapeNames.isShapeable("female", "maybe-grown", false));
    }

    @Test
    void maleNeutralChildTeenUnassignedAndBabiesAreNotShapeable() {
        assertFalse(CompanionShapeNames.isShapeable("male", "adult", false));
        assertFalse(CompanionShapeNames.isShapeable("m", "adult", false));
        assertFalse(CompanionShapeNames.isShapeable("neutral", "adult", false));
        assertFalse(CompanionShapeNames.isShapeable("female", "child", false));
        assertFalse(CompanionShapeNames.isShapeable("female", "teen", false));
        assertFalse(CompanionShapeNames.isShapeable("female", "teenager", false));
        assertFalse(CompanionShapeNames.isShapeable("female", "baby", false));
        assertFalse(CompanionShapeNames.isShapeable("female", "toddler", false));
        assertFalse(CompanionShapeNames.isShapeable("female", "unassigned", false));
        assertFalse(CompanionShapeNames.isShapeable("female", "adult", true));
    }

    @Test
    void normalizeGenderDoesNotDefault() {
        assertEquals("female", CompanionShapeNames.normalizeGender("female"));
        assertEquals("female", CompanionShapeNames.normalizeGender("F"));
        assertEquals("male", CompanionShapeNames.normalizeGender("male"));
        assertEquals("neutral", CompanionShapeNames.normalizeGender("neutral"));
        assertNull(CompanionShapeNames.normalizeGender(null));
        assertNull(CompanionShapeNames.normalizeGender(""));
        assertNull(CompanionShapeNames.normalizeGender("  "));
        assertNull(CompanionShapeNames.normalizeGender("unknown"));
    }

    @Test
    void normalizeAgeDoesNotDefault() {
        assertEquals("adult", CompanionShapeNames.normalizeAge("adult"));
        assertEquals("child", CompanionShapeNames.normalizeAge("Child"));
        assertEquals("teen", CompanionShapeNames.normalizeAge("teenager"));
        assertEquals("unassigned", CompanionShapeNames.normalizeAge("unassigned"));
        assertNull(CompanionShapeNames.normalizeAge(null));
        assertNull(CompanionShapeNames.normalizeAge(""));
        assertNull(CompanionShapeNames.normalizeAge("maybe-grown"));
    }
}
