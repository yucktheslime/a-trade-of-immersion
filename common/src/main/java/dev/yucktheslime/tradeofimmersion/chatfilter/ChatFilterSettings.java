package dev.yucktheslime.tradeofimmersion.chatfilter;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Snapshot of the chat-filter settings. Filled from {@code ToiConfig} in game, or built by tests.
 */
public final class ChatFilterSettings {
    public boolean enabled = true;
    public boolean chatFilterEnabled = true;
    public boolean ownerChatWithoutAddress = false;
    public int chatContinueSeconds = 60;
    public boolean aiVillagerPublicChat = true;
    public boolean aiSkipVillagersWhenMcaChatAi = true;
    public double aiNpcChatCooldownSeconds = 2.0;
    public double chatHearingRadius = 16.0;
    public double ownerChatRadius = 10.0;
    public List<String> groupAddressWords = List.of("everyone", "everybody", "guys", "team", "all of you");

    public Set<String> groupWords() {
        if (groupAddressWords == null) return Set.of();
        return groupAddressWords.stream()
                .map(s -> s == null ? "" : s.toLowerCase(Locale.ROOT).trim())
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public long cooldownMs() {
        return Math.max(0, Math.round(aiNpcChatCooldownSeconds * 1000.0));
    }

    public long continueMs() {
        return Math.max(0L, (long) chatContinueSeconds * 1000L);
    }
}
