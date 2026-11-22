package dev.yucktheslime.mcaplayer2.fabric;

import dev.yucktheslime.mcaplayer2.McaPlayer2Bridge;
import net.fabricmc.api.ModInitializer;

/** Fabric entry point; all logic lives in the common module. */
public final class McaPlayer2BridgeFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        McaPlayer2Bridge.init();
    }
}
