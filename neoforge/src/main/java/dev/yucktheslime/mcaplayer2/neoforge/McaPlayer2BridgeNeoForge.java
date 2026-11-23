package dev.yucktheslime.mcaplayer2.neoforge;

import dev.yucktheslime.mcaplayer2.McaPlayer2Bridge;
import dev.yucktheslime.mcaplayer2.platform.FakePlayers;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/** NeoForge entry point; all logic lives in the common module (events via Architectury API). */
@Mod(McaPlayer2Bridge.MOD_ID)
public final class McaPlayer2BridgeNeoForge {
    public McaPlayer2BridgeNeoForge() {
        FakePlayers.setProvider(FakePlayerFactory::get);
        McaPlayer2Bridge.init();
        if (FMLEnvironment.dist == Dist.CLIENT) NeoForgeClient.init();
    }
}
