package dev.yucktheslime.mcaplayer2.ai;

import java.util.Map;

/** One line for an MCA villager answering a companion. {@code line} is what the companion just said. */
public record VillagerRequest(String villagerId, String villagerName, String companionName, String system, String user,
                              String line, Map<String, String> context) {
}
