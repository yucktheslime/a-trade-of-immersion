package dev.yucktheslime.tradeofimmersion.neoforge.compat;

import dev.yucktheslime.tradeofimmersion.client.CompanionBodyClient;
import dev.yucktheslime.tradeofimmersion.client.CompanionRenderer;

/** Wires MCA: Female Gender Physics into the companion renderer. Only loaded when the addon is installed. */
public final class McabpHooks {
    private McabpHooks() {
    }

    public static void install() {
        CompanionRenderer.LAYER_HOOKS.add(FemaleGenderPhysicsCompat::addLayer);
        CompanionBodyClient.extraDefaultGenes = FemaleGenderPhysicsCompat::applyDefaultGenes;
        CompanionBodyClient.extraGeneType = FemaleGenderPhysicsCompat::geneType;
    }
}
