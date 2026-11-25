package dev.yucktheslime.tradeofimmersion.chatfilter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Decides whether a player's line should start an AI turn, and which NPC it is for.
 * Pure Java: no Minecraft types, so the rules can be unit-tested.
 *
 * <p>Public chat starts a turn only when the player addresses an NPC (by name, a group vocative,
 * looking at them, a recent conversation, or — if enabled — an owned companion standing next to
 * them). The dialogue box always has an explicit target.
 */
public final class ChatRouter {
    public enum Channel { PUBLIC_CHAT, DIALOGUE_BOX }

    private ChatRouter() {
    }

    public static RouteDecision route(
            Channel channel,
            String text,
            List<ChatCandidate> nearby,
            ChatCandidate explicit,
            UUID continuedNpc,
            ChatFilterSettings cfg,
            NpcTurnCooldown cooldown,
            long nowMillis,
            boolean mcaChatAiEnabled
    ) {
        if (cfg == null || !cfg.enabled) return RouteDecision.of(RouteDecision.Action.DISABLED, "mod disabled");
        if (channel == Channel.PUBLIC_CHAT && !cfg.chatFilterEnabled) {
            return RouteDecision.of(RouteDecision.Action.DISABLED, "chat filter off");
        }
        if (!ChatFilter.isDialogue(text)) return RouteDecision.of(RouteDecision.Action.SKIP, "not dialogue");

        if (channel == Channel.DIALOGUE_BOX) {
            if (explicit == null) return RouteDecision.of(RouteDecision.Action.SKIP, "no target");
            return finish(explicit, channel, cfg, cooldown, nowMillis, mcaChatAiEnabled, "dialogue box");
        }

        List<ChatCandidate> pool = nearby == null ? List.of() : sorted(nearby);

        ChatCandidate named = firstNamed(text, pool);
        if (named != null) return finish(named, channel, cfg, cooldown, nowMillis, mcaChatAiEnabled, "named");

        if (NameMatcher.isGroupAddress(text, cfg.groupWords())) {
            List<ChatCandidate> group = new ArrayList<>();
            for (ChatCandidate c : pool) {
                if (c.kind() == ChatCandidate.Kind.COMPANION && c.ownedBySpeaker() && c.inHearingRange()
                        && cooldownAllows(c, cfg, cooldown, nowMillis)) {
                    group.add(c);
                }
            }
            if (group.isEmpty()) return RouteDecision.of(RouteDecision.Action.OVERHEAR_ONLY, "group address, none available");
            return RouteDecision.of(RouteDecision.Action.AI_TURN, "group address", group);
        }

        ChatCandidate looked = firstLooked(pool);
        if (looked != null) return finish(looked, channel, cfg, cooldown, nowMillis, mcaChatAiEnabled, "look");

        if (continuedNpc != null) {
            for (ChatCandidate c : pool) {
                if (continuedNpc.equals(c.id()) && c.inHearingRange()) {
                    return finish(c, channel, cfg, cooldown, nowMillis, mcaChatAiEnabled, "continue");
                }
            }
        }

        if (cfg.ownerChatWithoutAddress) {
            List<ChatCandidate> owned = new ArrayList<>();
            for (ChatCandidate c : pool) {
                if (c.kind() == ChatCandidate.Kind.COMPANION && c.ownedBySpeaker()
                        && c.distance() <= cfg.ownerChatRadius) {
                    owned.add(c);
                }
            }
            if (owned.size() >= 2) {
                return RouteDecision.of(RouteDecision.Action.DECIDE, "several owned companions", owned);
            }
            if (owned.size() == 1) {
                return finish(owned.get(0), channel, cfg, cooldown, nowMillis, mcaChatAiEnabled, "owner proximity");
            }
        }

        return RouteDecision.of(RouteDecision.Action.OVERHEAR_ONLY, "not addressed");
    }

    private static RouteDecision finish(
            ChatCandidate target,
            Channel channel,
            ChatFilterSettings cfg,
            NpcTurnCooldown cooldown,
            long nowMillis,
            boolean mcaChatAiEnabled,
            String reason
    ) {
        if (target.kind() == ChatCandidate.Kind.VILLAGER && channel == Channel.PUBLIC_CHAT && !cfg.aiVillagerPublicChat) {
            return RouteDecision.of(RouteDecision.Action.OVERHEAR_ONLY, "villager public chat off", target);
        }
        if (target.kind() == ChatCandidate.Kind.VILLAGER && channel == Channel.PUBLIC_CHAT
                && mcaChatAiEnabled && cfg.aiSkipVillagersWhenMcaChatAi) {
            return RouteDecision.of(RouteDecision.Action.MCA_CHAT_AI, "mca chat AI on", target);
        }
        if (!cooldownAllows(target, cfg, cooldown, nowMillis)) {
            return RouteDecision.of(RouteDecision.Action.RATE_LIMITED, reason, target);
        }
        return RouteDecision.of(RouteDecision.Action.AI_TURN, reason, target);
    }

    private static boolean cooldownAllows(ChatCandidate c, ChatFilterSettings cfg, NpcTurnCooldown cooldown, long now) {
        if (cooldown == null) return true;
        return cooldown.allowed(c.id(), now, cfg.cooldownMs());
    }

    private static ChatCandidate firstNamed(String text, List<ChatCandidate> pool) {
        for (ChatCandidate c : pool) {
            if (NameMatcher.mentions(text, c.name(), c.aliases())) return c;
        }
        return null;
    }

    private static ChatCandidate firstLooked(List<ChatCandidate> pool) {
        for (ChatCandidate c : pool) {
            if (c.lookedAt()) return c;
        }
        return null;
    }

    private static List<ChatCandidate> sorted(List<ChatCandidate> nearby) {
        List<ChatCandidate> copy = new ArrayList<>(nearby);
        copy.sort(Comparator.comparingDouble(ChatCandidate::distance));
        return copy;
    }
}
