package dev.yucktheslime.tradeofimmersion.fabric;

import dev.yucktheslime.tradeofimmersion.TradeOfImmersion;
import dev.yucktheslime.tradeofimmersion.platform.FakePlayers;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.FakePlayer;

/** Fabric entry point; all logic lives in the common module. */
public final class TradeOfImmersionFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        FakePlayers.setProvider(FakePlayer::get);
        TradeOfImmersion.init();
    }
}
