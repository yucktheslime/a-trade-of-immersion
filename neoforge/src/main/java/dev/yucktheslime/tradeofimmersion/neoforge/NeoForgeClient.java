package dev.yucktheslime.tradeofimmersion.neoforge;

import dev.yucktheslime.tradeofimmersion.client.TradeOfImmersionClient;
import dev.yucktheslime.tradeofimmersion.neoforge.compat.McabpPresence;

/** Client-only setup; loaded only on the client dist. */
final class NeoForgeClient {
    private NeoForgeClient() {
    }

    static void init() {
        if (McabpPresence.loaded()) dev.yucktheslime.tradeofimmersion.neoforge.compat.McabpHooks.install();
        TradeOfImmersionClient.init();
    }
}
