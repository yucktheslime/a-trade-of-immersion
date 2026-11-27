package dev.yucktheslime.tradeofimmersion.client;

import dev.architectury.platform.Platform;
import dev.yucktheslime.tradeofimmersion.entity.CompanionEntity;
import dev.yucktheslime.tradeofimmersion.TradeOfImmersion;
import dev.yucktheslime.tradeofimmersion.body.ClientCompanionBodies;
import dev.yucktheslime.tradeofimmersion.body.CompanionBodyData;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.yucktheslime.tradeofimmersion.config.ToiConfig;
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
            ResourceLocation.fromNamespaceAndPath(TradeOfImmersion.MOD_ID, "textures/entity/transparent.png");

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
    /**
     * Set by MCA Body Shapes (NeoForge): the Breast gene a companion's record should hold, or null to leave it.
     * Applied on every {@link #ensure} so the choice between TOI's bust and mcabp's (gene 0 vs the bust size) holds.
     */
    public static Function<net.minecraft.world.entity.LivingEntity, Float> breastGeneOverride = e -> null;

    private CompanionBodyClient() {
    }

    public static boolean featureEnabled() {
        ToiConfig cfg = ToiConfig.get();
        if (!cfg.companionMcaBody) return false;
        if (disabledByMods == null) {
            disabledByMods = false;
            for (String id : cfg.companionMcaBodyDisableIfModsLoaded) {
                if (Platform.isModLoaded(id)) {
                    disabledByMods = true;
                    TradeOfImmersion.LOGGER.info("[tradeofimmersion] MCA companion body disabled because mod '{}' is loaded", id);
                }
            }
            if (Platform.isModLoaded("wildfire_gender")) {
                TradeOfImmersion.LOGGER.info("[tradeofimmersion] Wildfire's Female Gender Mod detected. It renders players, not "
                        + "these companions, so companions use MCA's (static) body; add 'wildfire_gender' to "
                        + "companionMcaBodyDisableIfModsLoaded to turn this off.");
            }
        }
        return !disabledByMods;
    }

    public static boolean generatedAppearance() {
        return "mca_generated".equalsIgnoreCase(ToiConfig.get().companionAppearance);
    }

    /** The client-side MCA villager record backing this companion (null if the feature is off). */
    public static VillagerEntityMCA villagerFor(net.minecraft.world.entity.LivingEntity e) {
        if (!(e instanceof CompanionEntity a) || !featureEnabled()) return null;
        Entry entry = ensure(a);
        return entry == null ? null : entry.villager;
    }

    /**
     * Whether this companion renders with MCA's villager look (skin, face, clothes, hair layers) instead of its
     * player skin: always with companionAppearance mca_generated, else once its look was set with MCA's editor.
     */
    public static boolean useGeneratedLook(CompanionEntity e) {
        if (!featureEnabled()) return false;
        CompanionBodyData edits = ClientCompanionBodies.get(e.getUUID());
        // The server decides per companion (synced); this client's config is only the fallback before the first sync.
        if (edits != null && edits.look != null) return edits.mcaLook();
        return generatedAppearance();
    }

    /** Whether this companion should currently use the MCA body (as opposed to the vanilla player model). */
    public static boolean useMcaBody(CompanionEntity e) {
        if (!featureEnabled()) return false;
        if (useGeneratedLook(e)) return true;
        Entry entry = ensure(e);
        if (entry == null || entry.gender != Gender.FEMALE) return false; // male MCA body == vanilla player body
        return !(ToiConfig.get().companionFlatWhenChestArmor && !e.getItemBySlot(EquipmentSlot.CHEST).isEmpty());
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
        try {
            Float gene = breastGeneOverride.apply(e);
            if (gene != null && entry.villager.getGenetics().getGene(Genetics.BREAST) != gene) {
                entry.villager.getGenetics().setGene(Genetics.BREAST, gene);
            }
        } catch (Throwable t) {
            // Never let the body-shape hook break companion rendering.
        }
        if (MCAClient.playerData.get(e.getUUID()) != entry.villager) {
            MCAClient.playerData.put(e.getUUID(), entry.villager); // re-added after MCAClient.onLogin() clears the map
        }
        return entry;
    }

    private static Entry create(Level level, UUID id, Gender gender, int rev) {
        try {
            CompanionBodyData edits = ClientCompanionBodies.get(id);
            VillagerEntityMCA v = dev.yucktheslime.tradeofimmersion.body.CompanionMcaData.create(level, id, gender, edits,
                    ToiConfig.get().companionBreastSize, extraDefaultGenes, extraGeneType);
            if (v == null) return null;
            // Skin, clothes and hair come from the server (CompanionLooks), so every player sees the same companion.
            var dims = v.getVillagerDimensions();
            if (dims.getHead() != 1.0f || dims.getBreasts() != 1.0f) {
                TradeOfImmersion.LOGGER.warn("[tradeofimmersion] Companion MCA data is not adult-sized (head={}, breasts={}, age={})",
                        dims.getHead(), dims.getBreasts(), v.getAgeState());
            }
            return new Entry(v, gender, rev);
        } catch (Throwable t) {
            TradeOfImmersion.LOGGER.warn("[tradeofimmersion] Could not create MCA body data for companion {}", id, t);
            return null;
        }
    }

    /**
     * Height/width like MCA's own player renderer ({@code MixinPlayerRenderer.scale}: {@code pose.scale(rawWidth,
     * rawHeight, rawWidth)}), applied only when Size or Width was set with {@code /toi body}, so
     * unedited companions keep the player size. Visual only; the server hitbox is unchanged.
     */
    public static void applyScale(CompanionEntity e, PoseStack pose) {
        if (!featureEnabled()) return;
        boolean villagerLook = useGeneratedLook(e);
        CompanionBodyData edits = ClientCompanionBodies.get(e.getUUID());
        if (!villagerLook && (edits == null || !(edits.genes.containsKey("Size") || edits.genes.containsKey("Width")
                || edits.mca != null && !edits.mca.isEmpty()))) return;
        Entry entry = ensure(e);
        if (entry == null) return;
        // Like MCA's VillagerLikeEntityMCARenderer.scale: width and height from the Size/Width genes.
        float w = entry.villager.getRawHorizontalScaleFactor();
        float h = entry.villager.getRawVerticalScaleFactor();
        if (w > 0 && h > 0) pose.scale(w, h, w);
        if (villagerLook) {
            float bh = BodyShapesClient.heightMultiplier(e);
            if (bh != 1.0f) pose.scale(1.0f, bh, 1.0f);
        }
    }

    static Gender genderFor(CompanionEntity e) {
        CompanionBodyData edits = ClientCompanionBodies.get(e.getUUID());
        if (edits != null && edits.gender != null) return dev.yucktheslime.tradeofimmersion.body.CompanionMcaData.parseGender(edits.gender, Gender.MALE); // /toi gender wins
        String g = e.getGender();
        if (g == null || g.isBlank()) g = ToiConfig.get().companionGender;
        return dev.yucktheslime.tradeofimmersion.body.CompanionMcaData.parseGender(g, Gender.MALE);
    }

    public record Entry(VillagerEntityMCA villager, Gender gender, int revision) {
    }
}
