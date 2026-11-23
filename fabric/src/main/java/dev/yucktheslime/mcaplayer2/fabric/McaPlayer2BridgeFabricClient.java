package dev.yucktheslime.mcaplayer2.fabric;

import dev.yucktheslime.mcaplayer2.client.McaPlayer2Client;
import net.fabricmc.api.ClientModInitializer;

public final class McaPlayer2BridgeFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        McaPlayer2Client.init();
    }
}
