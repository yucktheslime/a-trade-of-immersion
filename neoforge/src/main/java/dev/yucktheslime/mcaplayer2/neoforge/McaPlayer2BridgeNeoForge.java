package dev.yucktheslime.mcaplayer2.neoforge;

import dev.yucktheslime.mcaplayer2.McaPlayer2Bridge;
import net.neoforged.fml.common.Mod;

/** NeoForge entry point; all logic lives in the common module (events via Architectury API). */
@Mod(McaPlayer2Bridge.MOD_ID)
public final class McaPlayer2BridgeNeoForge {
    public McaPlayer2BridgeNeoForge() {
        McaPlayer2Bridge.init();
    }
}
