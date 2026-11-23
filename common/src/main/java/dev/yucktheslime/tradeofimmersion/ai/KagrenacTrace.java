package dev.yucktheslime.tradeofimmersion.ai;

import java.util.List;

/**
 * What a Kagrenac turn returned, kept until the game has run (or refused) its actions so the outcome can be
 * reported through result.php. {@code returned} are the action names the server sent; {@code clientRejected}
 * are the ones this game refused before running anything (name, reason).
 */
public record KagrenacTrace(String requestId, String sessionId, String npcId, List<String> returned,
                            List<Rejected> clientRejected) {
    public record Rejected(String name, String reason) {
    }
}
