package dev.yucktheslime.tradeofimmersion.body;

import dev.yucktheslime.tradeofimmersion.TradeOfImmersion;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.registry.EntitiesMCA;
import net.minecraft.world.level.Level;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The MCA villager record behind a companion's look, built the same way on the server (for MCA's villager
 * editor) and on the client (for rendering): adult, the companion's gender, genes seeded from its UUID, then the
 * appearance saved with MCA's editor ({@link CompanionBodyData#mca}), then {@code /toi body} gene edits.
 * The villager is never added to a level.
 */
public final class CompanionMcaData {
    /** MCA Genetics.GeneType name -> type (names verified in MCA 7.7.36's Genetics). */
    public static final Map<String, Genetics.GeneType> GENE_TYPES = new LinkedHashMap<>();

    static {
        GENE_TYPES.put("Size", Genetics.SIZE);
        GENE_TYPES.put("Width", Genetics.WIDTH);
        GENE_TYPES.put("Breast", Genetics.BREAST);
        GENE_TYPES.put("Melanin", Genetics.MELANIN);
        GENE_TYPES.put("Hemoglobin", Genetics.HEMOGLOBIN);
        GENE_TYPES.put("Eumelanin", Genetics.EUMELANIN);
        GENE_TYPES.put("Pheomelanin", Genetics.PHEOMELANIN);
        GENE_TYPES.put("Skin", Genetics.SKIN);
        GENE_TYPES.put("Face", Genetics.FACE);
        GENE_TYPES.put("EyeBrightness", Genetics.EYE_BRIGHTNESS);
    }

    private CompanionMcaData() {
    }

    public static Gender parseGender(String s, Gender def) {
        if (s == null) return def;
        return switch (s.trim().toLowerCase(Locale.ROOT)) {
            case "female", "f", "woman" -> Gender.FEMALE;
            case "male", "m", "man" -> Gender.MALE;
            case "neutral", "n" -> Gender.NEUTRAL;
            default -> def;
        };
    }

    public static String genderName(Gender g) {
        return g == Gender.FEMALE ? "female" : g == Gender.NEUTRAL ? "neutral" : "male";
    }

    /**
     * @param breast       breast gene when no edit sets it (config companionBreastSize, negative = 0.5)
     * @param extraDefault client compat hook for extra default genes (may be null)
     * @param extraType    client compat hook mapping extra gene names (may be null)
     */
    public static VillagerEntityMCA create(Level level, UUID id, Gender gender, CompanionBodyData edits, double breast,
                                           Consumer<Genetics> extraDefault, Function<String, Genetics.GeneType> extraType) {
        VillagerEntityMCA v = (gender == Gender.FEMALE ? EntitiesMCA.FEMALE_VILLAGER : EntitiesMCA.MALE_VILLAGER).create(level);
        if (v == null) return null;
        Genetics g = v.getGenetics();
        g.setGender(gender);
        Random r = new Random(id.getMostSignificantBits() ^ id.getLeastSignificantBits());
        for (Genetics.GeneType type : GENE_TYPES.values()) g.setGene(type, r.nextFloat());
        g.setGene(Genetics.BREAST, breast >= 0 ? (float) Math.min(1.0, breast) : 0.5f);
        if (extraDefault != null) extraDefault.accept(g);
        if (edits != null && edits.mca != null && !edits.mca.isEmpty()) {
            try {
                v.readAdditionalSaveDataForEditor(edits.mca.copy());
            } catch (Throwable t) {
                TradeOfImmersion.LOGGER.warn("[tradeofimmersion] Could not apply the MCA editor look of companion {}", id, t);
            }
            g.setGender(gender);
        }
        if (edits != null) {
            edits.genes.forEach((name, value) -> {
                Genetics.GeneType type = GENE_TYPES.get(name);
                if (type == null && extraType != null) type = extraType.apply(name);
                if (type != null) g.setGene(type, Math.max(0f, Math.min(1f, value)));
            });
        }
        // EntityType.create() never runs finalizeSpawn(): without this the record is a scaled-up child.
        v.setAge(0);
        v.setAgeState(AgeState.ADULT);
        v.refreshDimensions();
        return v;
    }
}
