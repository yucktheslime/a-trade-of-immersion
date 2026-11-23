package dev.yucktheslime.mcaplayer2.ai;

import java.util.concurrent.CompletableFuture;

public interface AiBackend {
    String name();

    /** Companion reply + actions. Called off the server thread; must not touch the world. */
    CompletableFuture<AiReply> companionTurn(TurnRequest req);

    /** A single in-character line for an MCA villager. */
    CompletableFuture<String> villagerLine(VillagerRequest req);
}
