package dev.yucktheslime.tradeofimmersion.util;

import java.util.List;
import java.util.UUID;

/**
 * Snapshot of the MCA Reborn data the bridge feeds into prompts.
 *
 * @param hearts hearts the villager has towards the companion's owner, or {@code null} if MCA has
 *               no memory entry for that player yet. MCA tracks hearts per <em>player</em>, not per
 *               companion entity, so this is the closest relationship score available.
 */
public record VillagerProfile(
        UUID uuid,
        String name,
        String gender,
        String ageState,
        String profession,
        String personality,
        String mood,
        int moodValue,
        List<String> traits,
        Integer hearts,
        String heartsPlayerName
) {
    /** One-paragraph description used in both the companion and villager prompts. */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append(" is a");
        sb.append(isVowel(ageState) ? "n " : " ").append(ageState);
        if (!gender.isEmpty()) sb.append(' ').append(gender);
        sb.append(" villager");
        if (!profession.isEmpty() && !profession.equals("none")) sb.append(" working as a ").append(profession);
        sb.append(". Personality: ").append(personality).append(". Current mood: ").append(mood)
                .append(" (").append(moodValue).append(").");
        if (!traits.isEmpty()) sb.append(" Traits: ").append(String.join(", ", traits)).append('.');
        if (hearts != null) {
            sb.append(" Relationship with ").append(heartsPlayerName).append(": ").append(hearts)
                    .append(" hearts (").append(heartsLabel(hearts)).append(").");
        } else if (heartsPlayerName != null) {
            sb.append(" ").append(name).append(" has not met ").append(heartsPlayerName).append(" yet.");
        }
        return sb.toString();
    }

    private static boolean isVowel(String s) {
        return !s.isEmpty() && "aeiou".indexOf(Character.toLowerCase(s.charAt(0))) >= 0;
    }

    public static String heartsLabel(int hearts) {
        if (hearts >= 75) return "very close";
        if (hearts >= 25) return "friendly";
        if (hearts > -25) return "neutral";
        if (hearts > -75) return "unfriendly";
        return "hostile";
    }
}
