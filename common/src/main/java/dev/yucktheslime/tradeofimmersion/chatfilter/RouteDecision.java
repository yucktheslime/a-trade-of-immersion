package dev.yucktheslime.tradeofimmersion.chatfilter;

import java.util.List;

public record RouteDecision(Action action, List<ChatCandidate> targets, String reason) {
    public enum Action {
        /** Line is not dialogue, or there is no target. */
        SKIP,
        /** Nearby NPCs may remember the line; nobody should start an AI turn. */
        OVERHEAR_ONLY,
        /** Start an AI turn for each of {@link #targets()}. */
        AI_TURN,
        /** Several owned companions could answer an unaddressed line: let Kagrenac pick, if available. */
        DECIDE,
        /** An MCA villager was addressed in public chat, and MCA's own ChatAI should answer alone. */
        MCA_CHAT_AI,
        /** The target was addressed, but its per-NPC cooldown has not elapsed. */
        RATE_LIMITED,
        /** The mod or the public-chat filter is switched off. */
        DISABLED
    }

    public RouteDecision {
        targets = targets == null ? List.of() : List.copyOf(targets);
        if (reason == null) reason = "";
    }

    public static RouteDecision of(Action action, String reason, ChatCandidate... targets) {
        return new RouteDecision(action, List.of(targets), reason);
    }

    public static RouteDecision of(Action action, String reason, List<ChatCandidate> targets) {
        return new RouteDecision(action, targets, reason);
    }

    public ChatCandidate target() {
        return targets.isEmpty() ? null : targets.get(0);
    }
}
