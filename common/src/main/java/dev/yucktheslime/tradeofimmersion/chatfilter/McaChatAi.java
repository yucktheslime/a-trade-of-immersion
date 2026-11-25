package dev.yucktheslime.tradeofimmersion.chatfilter;

import dev.yucktheslime.tradeofimmersion.TradeOfImmersion;

/**
 * Detects MCA Reborn's own GPT chat ({@code Config.enableVillagerChatAI}, default false).
 * When it is on, MCA injects into {@code handleChat} and answers villagers addressed by name;
 * we then leave public-chat villager lines to MCA so both AIs do not answer the same line.
 */
public final class McaChatAi {
    private static volatile Boolean cached;
    private static volatile boolean warned;

    private McaChatAi() {
    }

    public static boolean enabled() {
        Boolean c = cached;
        if (c != null) return c;
        return refresh();
    }

    public static boolean refresh() {
        boolean on = false;
        try {
            Class<?> cls = Class.forName("net.conczin.mca.Config");
            Object inst = cls.getMethod("getInstance").invoke(null);
            Object v = cls.getField("enableVillagerChatAI").get(inst);
            on = v instanceof Boolean b && b;
        } catch (Throwable ignored) {
            on = false;
        }
        cached = on;
        return on;
    }

    public static void detectAndLog() {
        if (refresh() && !warned) {
            warned = true;
            TradeOfImmersion.LOGGER.warn(
                    "MCA Reborn's villager ChatAI is on (enableVillagerChatAI). "
                            + "A Trade of Immersion will leave public chat with MCA villagers to MCA, "
                            + "so both AIs do not answer the same line. Dialogue-box talk still uses this mod's AI. "
                            + "Set aiSkipVillagersWhenMcaChatAi to false to let both run, or turn MCA's ChatAI off in its config.");
        }
    }

    /** For tests: pin the detected value. {@code null} clears the cache. */
    public static void override(Boolean value) {
        cached = value;
        if (value == null) warned = false;
    }
}
