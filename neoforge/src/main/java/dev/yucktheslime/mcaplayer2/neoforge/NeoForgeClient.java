package dev.yucktheslime.mcaplayer2.neoforge;

import dev.yucktheslime.mcaplayer2.client.McaPlayer2Client;
import dev.yucktheslime.mcaplayer2.neoforge.compat.McabpPresence;

/** Client-only setup; loaded only on the client dist. */
final class NeoForgeClient {
    private NeoForgeClient() {
    }

    static void init() {
        if (McabpPresence.loaded()) dev.yucktheslime.mcaplayer2.neoforge.compat.McabpHooks.install();
        McaPlayer2Client.init();
    }
}
