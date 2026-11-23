package dev.yucktheslime.mcaplayer2.neoforge.compat;

import com.mcabp.wildfire.client.render.GenderLayer;
import com.mcabp.wildfire.main.entitydata.MCAVillagerGenes;
import dev.yucktheslime.mcaplayer2.McaPlayer2Bridge;
import net.conczin.mca.entity.ai.Genetics;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;

import java.util.Map;

/**
 * Optional compat with "MCA: Female Gender Physics" (mod id {@code mcabp}, CurseForge 1710127, verified against
 * the NeoForge 1.21.1 V0.1.8 jar). Only call into this class after {@link McabpPresence#loaded()} returned true.
 *
 * <p>The addon turns MCA's own breast cubes off ({@code Config.enableBoobs = false}) and draws its own
 * {@code GenderLayer} with spring physics on MCA villager renderers (and on players when Wildfire's mod is absent),
 * reading gender and genes through {@code EntityConfig.resolveVillagerLike}. Companions are neither villagers nor
 * players, so we add its layer to Player2NPC's renderer and feed it our per-companion MCA record
 * (see {@code mixin.compat.*}).
 */
public final class FemaleGenderPhysicsCompat {
    private FemaleGenderPhysicsCompat() {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void addLayer(LivingEntityRenderer renderer, EntityRendererProvider.Context ctx) {
        try {
            renderer.addLayer(new GenderLayer(renderer, ctx.getModelManager()));
            McaPlayer2Bridge.LOGGER.info("[mcaplayer2] MCA: Female Gender Physics detected; companions use its breast rendering + physics");
        } catch (Throwable t) {
            McaPlayer2Bridge.LOGGER.warn("[mcaplayer2] Could not attach MCA: Female Gender Physics layer to companions", t);
        }
    }

    /** Same defaults the addon uses for an untouched villager (0.5 = neutral; depth 1, rotation 0, growth baseline). */
    public static void applyDefaultGenes(Genetics g) {
        g.setGene(MCAVillagerGenes.SEPARATION, 0.5f);
        g.setGene(MCAVillagerGenes.HEIGHT, 0.5f);
        g.setGene(MCAVillagerGenes.DEPTH, 1.0f);
        g.setGene(MCAVillagerGenes.ROTATION, 0.0f);
        g.setGene(MCAVillagerGenes.BREAST_GROWTH, 0.5f);
        g.setGene(MCAVillagerGenes.CHEST_INITIALIZED, 1.0f);
    }

    private static final Map<String, Genetics.GeneType> TYPES = Map.of(
            "BreastSeparation", MCAVillagerGenes.SEPARATION,
            "BreastHeight", MCAVillagerGenes.HEIGHT,
            "BreastDepth", MCAVillagerGenes.DEPTH,
            "BreastRotation", MCAVillagerGenes.ROTATION,
            "BreastGrowth", MCAVillagerGenes.BREAST_GROWTH);

    public static Genetics.GeneType geneType(String name) {
        return TYPES.get(name);
    }
}
