package dev.yucktheslime.mcaplayer2.fabric;

import dev.yucktheslime.mcaplayer2.McaPlayer2Bridge;
import dev.yucktheslime.mcaplayer2.platform.FakePlayers;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.FakePlayer;

/** Fabric entry point; all logic lives in the common module. */
public final class McaPlayer2BridgeFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        FakePlayers.setProvider(FakePlayer::get);
        McaPlayer2Bridge.init();
    }
}
