package dev.yucktheslime.mcaplayer2.client;

import dev.architectury.platform.Platform;
import dev.yucktheslime.mcaplayer2.entity.CompanionEntity;
import dev.yucktheslime.mcaplayer2.McaPlayer2Bridge;
import dev.yucktheslime.mcaplayer2.body.ClientCompanionBodies;
import dev.yucktheslime.mcaplayer2.body.CompanionBodyData;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.yucktheslime.mcaplayer2.config.BridgeConfig;
import net.conczin.mca.MCAClient;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.registry.EntitiesMCA;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.Level;
import java.util.function.Consumer;
import java.util.function.Function;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Client-only helper that gives each companion an MCA "genetics" record so MCA's own body model (and the
 * optional MCA: Female Gender Physics addon) can render it.
 *
 * <p>How MCA does it (verified by decompiling MCA 7.7.36): MCA's {@code PlayerEntityExtendedModel} (a
 * {@code PlayerModel} with MCA breast parts) reads gender/breast size in {@code setupAnim} through
 * {@code CommonVillagerModel.getVillager(entity)}, which for non-villager entities looks up
 * {@code MCAClient.playerData.get(entity UUID)} (falling back to a male villager). MCA fills that map for
 * players; we put a client-side {@link VillagerEntityMCA} there for each companion UUID.
 */
public final class CompanionBodyClient {
    public static final ResourceLocation TRANSPARENT =
            ResourceLocation.fromNamespaceAndPath(McaPlayer2Bridge.MOD_ID, "textures/entity/transparent.png");

    private static final Map<UUID, Entry> CACHE = new HashMap<>();
    /** Breast gene used when companionBreastSize is negative. */
    private static final float DEFAULT_BREAST = 0.5f;
    /** MCA Genetics.GeneType name -> type (names verified in MCA 7.7.36's Genetics). */
    private static final Map<String, Genetics.GeneType> GENE_TYPES = Map.of(
            "Size", Genetics.SIZE, "Width", Genetics.WIDTH, "Breast", Genetics.BREAST, "Melanin", Genetics.MELANIN,
            "Hemoglobin", Genetics.HEMOGLOBIN, "Eumelanin", Genetics.EUMELANIN, "Pheomelanin", Genetics.PHEOMELANIN,
            "Skin", Genetics.SKIN, "Face", Genetics.FACE, "EyeBrightness", Genetics.EYE_BRIGHTNESS);
    private static Boolean disabledByMods;
    /** Loader compat hooks (set by the NeoForge module when MCA: Female Gender Physics is installed). */
    public static Consumer<Genetics> extraDefaultGenes = g -> {
    };
    public static Function<String, Genetics.GeneType> extraGeneType = n -> null;

    private CompanionBodyClient() {
    }

    public static boolean featureEnabled() {
        BridgeConfig cfg = BridgeConfig.get();
        if (!cfg.companionMcaBody) return false;
        if (disabledByMods == null) {
            disabledByMods = false;
            for (String id : cfg.companionMcaBodyDisableIfModsLoaded) {
                if (Platform.isModLoaded(id)) {
                    disabledByMods = true;
                    McaPlayer2Bridge.LOGGER.info("[mcaplayer2] MCA companion body disabled because mod '{}' is loaded", id);
                }
            }
            if (Platform.isModLoaded("wildfire_gender")) {
                McaPlayer2Bridge.LOGGER.info("[mcaplayer2] Wildfire's Female Gender Mod detected. It renders players, not "
                        + "these companions, so companions use MCA's (static) body; add 'wildfire_gender' to "
                        + "companionMcaBodyDisableIfModsLoaded to turn this off.");
            }
        }
        return !disabledByMods;
    }

    public static boolean generatedAppearance() {
        return "mca_generated".equalsIgnoreCase(BridgeConfig.get().companionAppearance);
    }

    /** The client-side MCA villager record backing this companion (null if the feature is off). */
    public static VillagerEntityMCA villagerFor(net.minecraft.world.entity.LivingEntity e) {
        if (!(e instanceof CompanionEntity a) || !featureEnabled()) return null;
        Entry entry = ensure(a);
        return entry == null ? null : entry.villager;
    }

    /** Whether this companion should currently use the MCA body (as opposed to the vanilla player model). */
    public static boolean useMcaBody(CompanionEntity e) {
        if (!featureEnabled()) return false;
        if (generatedAppearance()) return true;
        Entry entry = ensure(e);
        if (entry == null || entry.gender != Gender.FEMALE) return false; // male MCA body == vanilla player body
        return !(BridgeConfig.get().companionFlatWhenChestArmor && !e.getItemBySlot(EquipmentSlot.CHEST).isEmpty());
    }

    /** Create/refresh the MCA genetics record for this companion and register it in {@code MCAClient.playerData}. */
    public static Entry ensure(CompanionEntity e) {
        Gender gender = genderFor(e);
        Entry entry = CACHE.get(e.getUUID());
        int rev = ClientCompanionBodies.revision();
        if (entry == null || entry.gender != gender || entry.revision != rev) {
            entry = create(e.level(), e.getUUID(), gender, rev);
            if (entry == null) return null;
            CACHE.put(e.getUUID(), entry);
        }
        if (MCAClient.playerData.get(e.getUUID()) != entry.villager) {
            MCAClient.playerData.put(e.getUUID(), entry.villager); // re-added after MCAClient.onLogin() clears the map
        }
        return entry;
    }

    private static Entry create(Level level, UUID id, Gender gender, int rev) {
        try {
            VillagerEntityMCA v = (gender == Gender.FEMALE ? EntitiesMCA.FEMALE_VILLAGER : EntitiesMCA.MALE_VILLAGER).create(level);
            if (v == null) return null;
            // Root cause of the 0.1.2 "scaled-up child" look and missing breasts: an entity made with
            // EntityType.create() never runs finalizeSpawn(), so AgeState stays UNASSIGNED and MCA's
            // refreshDimensions() interpolates UNASSIGNED -> BABY with the default GrowthAmount (-maxAge),
            // giving BABY dimensions (head 1.5x, breasts 0). setAge(0) = adult: AgeState.ADULT, dimensions ADULT.
            v.setAge(0);
            v.setAgeState(AgeState.ADULT);
            v.refreshDimensions();
            Genetics g = v.getGenetics();
            g.setGender(gender);
            Random r = new Random(id.getMostSignificantBits() ^ id.getLeastSignificantBits());
            for (Genetics.GeneType type : new Genetics.GeneType[]{Genetics.SIZE, Genetics.WIDTH, Genetics.BREAST, Genetics.MELANIN,
                    Genetics.HEMOGLOBIN, Genetics.EUMELANIN, Genetics.PHEOMELANIN, Genetics.SKIN, Genetics.FACE, Genetics.EYE_BRIGHTNESS}) {
                g.setGene(type, r.nextFloat());
            }
            double breast = BridgeConfig.get().companionBreastSize;
            g.setGene(Genetics.BREAST, breast >= 0 ? (float) Math.min(1.0, breast) : DEFAULT_BREAST);
            extraDefaultGenes.accept(g);
            CompanionBodyData edits = ClientCompanionBodies.get(id);
            if (edits != null) {
                edits.genes.forEach((name, value) -> {
                    Genetics.GeneType type = GENE_TYPES.get(name);
                    if (type == null) type = extraGeneType.apply(name);
                    if (type != null) g.setGene(type, Math.max(0f, Math.min(1f, value)));
                });
            }
            if (generatedAppearance()) {
                try {
                    v.randomizeHair();
                    v.randomizeClothes();
                } catch (Throwable t) {
                    McaPlayer2Bridge.LOGGER.debug("MCA hair/clothes randomization unavailable on this client", t);
                }
            }
            var dims = v.getVillagerDimensions();
            if (dims.getHead() != 1.0f || dims.getBreasts() != 1.0f) {
                McaPlayer2Bridge.LOGGER.warn("[mcaplayer2] Companion MCA data is not adult-sized (head={}, breasts={}, age={})",
                        dims.getHead(), dims.getBreasts(), v.getAgeState());
            }
            return new Entry(v, gender, rev);
        } catch (Throwable t) {
            McaPlayer2Bridge.LOGGER.warn("[mcaplayer2] Could not create MCA body data for companion {}", id, t);
            return null;
        }
    }

    /**
     * Height/width like MCA's own player renderer ({@code MixinPlayerRenderer.scale}: {@code pose.scale(rawWidth,
     * rawHeight, rawWidth)}), applied only when Size or Width was set with {@code /mcaplayer2 body}, so
     * unedited companions keep the player size. Visual only; the server hitbox is unchanged.
     */
    public static void applyScale(CompanionEntity e, PoseStack pose) {
        if (!featureEnabled()) return;
        CompanionBodyData edits = ClientCompanionBodies.get(e.getUUID());
        if (edits == null || !(edits.genes.containsKey("Size") || edits.genes.containsKey("Width"))) return;
        Entry entry = ensure(e);
        if (entry == null) return;
        float w = entry.villager.getRawHorizontalScaleFactor();
        float h = entry.villager.getRawVerticalScaleFactor();
        if (w > 0 && h > 0) pose.scale(w, h, w);
    }

    static Gender genderFor(CompanionEntity e) {
        CompanionBodyData edits = ClientCompanionBodies.get(e.getUUID());
        if (edits != null && edits.gender != null) return parse(edits.gender, Gender.MALE); // /mcaplayer2 gender wins
        String g = e.getGender();
        if (g == null || g.isBlank()) g = BridgeConfig.get().companionGender;
        return parse(g, Gender.MALE);
    }

    private static Gender parse(String s, Gender def) {
        if (s == null) return def;
        return switch (s.trim().toLowerCase(Locale.ROOT)) {
            case "female", "f", "woman" -> Gender.FEMALE;
            case "male", "m", "man" -> Gender.MALE;
            case "neutral", "n" -> Gender.NEUTRAL;
            default -> def;
        };
    }

    public record Entry(VillagerEntityMCA villager, Gender gender, int revision) {
    }
}
