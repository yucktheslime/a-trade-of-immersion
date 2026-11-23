package dev.yucktheslime.mcaplayer2.ai;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One companion turn, built on the server thread.
 *
 * @param system    system prompt (persona + rules + action format)
 * @param user      context + the trigger, for chat-completion backends
 * @param trigger   just the line that caused the turn (chat text or event description)
 * @param speaker   who spoke ("" for events)
 * @param eventType null for chat, else item_given / combat_started / ...
 * @param context   short key/value facts (for Kagrenac's context map)
 */
public record TurnRequest(String npcId, String npcName, String ownerName, String system, String user, String trigger,
                          String speaker, String eventType, Map<String, String> context) {
    public static Map<String, String> ctx() {
        return new LinkedHashMap<>();
    }
}
