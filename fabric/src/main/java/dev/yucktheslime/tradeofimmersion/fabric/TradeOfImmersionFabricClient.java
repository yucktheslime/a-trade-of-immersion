package dev.yucktheslime.tradeofimmersion.fabric;

import dev.yucktheslime.tradeofimmersion.client.TradeOfImmersionClient;
import net.fabricmc.api.ClientModInitializer;

public final class TradeOfImmersionFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        TradeOfImmersionClient.init();
    }
}
