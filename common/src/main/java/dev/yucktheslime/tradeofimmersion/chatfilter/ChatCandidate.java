package dev.yucktheslime.tradeofimmersion.chatfilter;

import java.util.List;
import java.util.UUID;

/** One nearby NPC the router can consider as a chat target. */
public record ChatCandidate(
        UUID id,
        String name,
        List<String> aliases,
        Kind kind,
        boolean ownedBySpeaker,
        double distance,
        boolean inHearingRange,
        boolean lookedAt
) {
    public enum Kind { COMPANION, VILLAGER }

    public ChatCandidate {
        if (aliases == null) aliases = List.of();
        else aliases = List.copyOf(aliases);
        if (name == null) name = "";
    }
}
