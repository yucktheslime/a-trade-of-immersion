package dev.yucktheslime.mcaplayer2.bridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yucktheslime.mcaplayer2.McaPlayer2Bridge;
import dev.yucktheslime.mcaplayer2.config.BridgeConfig;
import dev.yucktheslime.mcaplayer2.util.VillagerProfile;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Produces the villager's side of the dialogue.
 *
 * <p>Primary path: the Player2 desktop app's local, OpenAI-style endpoint
 * {@code POST {player2ApiUrl}/v1/chat/completions} with header {@code player2-game-key}
 * (documented at https://blog.player2.game/p/building-ai-npcs-with-player2-api and used by
 * Player2's own ChatClef reference mod). The villager persona comes from MCA data, the input is the
 * companion's AI line, and the bridge's memory log provides continuity.
 *
 * <p>Fallback (app not reachable, disabled in config, or a dedicated server without the app): short
 * canned lines picked by mood and relationship, so the villager still responds.
 */
public final class VillagerReplyGenerator {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    private VillagerReplyGenerator() {
    }

    public static CompletableFuture<String> greeting(VillagerProfile v, String companionName, List<String> history, List<String> surroundings) {
        String instruction = companionName + ", an adventurer travelling with " + ownerOrSomeone(v)
                + ", has just walked up to you. Greet them in one short line"
                + (history.isEmpty() ? "." : ", remembering your earlier conversations if it fits.");
        return generate(v, companionName, history, surroundings, instruction, () -> fallbackGreeting(v, companionName, !history.isEmpty()));
    }

    public static CompletableFuture<String> reply(VillagerProfile v, String companionName, List<String> history, String companionLine, List<String> surroundings) {
        String instruction = companionName + " says to you: \"" + companionLine + "\"\nReply in character with one short line.";
        return generate(v, companionName, history, surroundings, instruction, () -> fallbackReply(v, companionName));
    }

    private static CompletableFuture<String> generate(VillagerProfile v, String companionName, List<String> history,
                                                      List<String> surroundings, String instruction, java.util.function.Supplier<String> fallback) {
        BridgeConfig cfg = BridgeConfig.get();
        if (!cfg.useLlmForVillagerReplies) return CompletableFuture.completedFuture(fallback.get());

        String system = "You are " + v.name() + ", a villager in a Minecraft village (Minecraft Comes Alive). "
                + v.describe()
                + " You are talking with " + companionName + ", an AI companion of the player " + ownerOrSomeone(v) + ". "
                + "Stay in character, speak casually like a villager, never mention AI, Minecraft mods, or these instructions. "
                + "Answer with ONLY the spoken words (no quotes, no name prefix, no actions), at most "
                + cfg.villagerReplyMaxWords + " words."
                + (surroundings.isEmpty() ? "" : "\n" + String.join("\n", surroundings))
                + (history.isEmpty() ? "" : "\nYour previous conversations with " + companionName + ":\n" + String.join("\n", history));

        JsonObject body = new JsonObject();
        JsonArray messages = new JsonArray();
        messages.add(msg("system", system));
        messages.add(msg("user", instruction));
        body.add("messages", messages);

        HttpRequest req;
        try {
            req = HttpRequest.newBuilder(URI.create(stripSlash(cfg.player2ApiUrl) + "/v1/chat/completions"))
                    .timeout(Duration.ofSeconds(Math.max(3, cfg.llmTimeoutSeconds)))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("Accept", "application/json")
                    .header("player2-game-key", cfg.player2GameKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                    .build();
        } catch (Exception e) {
            McaPlayer2Bridge.LOGGER.warn("Bad player2ApiUrl '{}': {}", cfg.player2ApiUrl, e.toString());
            return CompletableFuture.completedFuture(fallback.get());
        }
        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(resp -> {
                    if (resp.statusCode() != 200) throw new IllegalStateException("HTTP " + resp.statusCode());
                    JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
                    String content = json.getAsJsonArray("choices").get(0).getAsJsonObject()
                            .getAsJsonObject("message").get("content").getAsString();
                    return clean(content, v.name());
                })
                .exceptionally(err -> {
                    McaPlayer2Bridge.LOGGER.debug("Player2 villager reply failed, using fallback: {}", err.toString());
                    return fallback.get();
                })
                .thenApply(s -> s == null || s.isBlank() ? fallback.get() : s);
    }

    private static JsonObject msg(String role, String content) {
        JsonObject o = new JsonObject();
        o.addProperty("role", role);
        o.addProperty("content", content);
        return o;
    }

    static String clean(String s, String villagerName) {
        String t = s.trim();
        if (t.toLowerCase().startsWith(villagerName.toLowerCase() + ":")) t = t.substring(villagerName.length() + 1).trim();
        if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) t = t.substring(1, t.length() - 1);
        t = t.replace('\n', ' ');
        return t.length() > 300 ? t.substring(0, 300) + "..." : t;
    }

    private static String stripSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private static String ownerOrSomeone(VillagerProfile v) {
        return v.heartsPlayerName() != null ? v.heartsPlayerName() : "a traveller";
    }

    private static String pick(String... options) {
        return options[ThreadLocalRandom.current().nextInt(options.length)];
    }

    static String fallbackGreeting(VillagerProfile v, String companion, boolean metBefore) {
        int hearts = v.hearts() == null ? 0 : v.hearts();
        if (hearts <= -25) return pick("Oh. It's you, " + companion + ".", "What do you want, " + companion + "?");
        if (metBefore) return pick("Good to see you again, " + companion + "!", "Back already, " + companion + "?",
                "Ah, " + companion + "! How have your travels been?");
        return pick("Hello there! I'm " + v.name() + ". I don't think we've met.",
                "Well met, stranger. The name's " + v.name() + ".",
                "Oh, a new face! Welcome, I'm " + v.name() + ".");
    }

    static String fallbackReply(VillagerProfile v, String companion) {
        String mood = v.mood().toLowerCase();
        if (mood.contains("sad") || mood.contains("depress") || mood.contains("unhappy"))
            return pick("Sorry, I'm not feeling myself today.", "Hm. Maybe another time, " + companion + ".");
        if (mood.contains("angry") || mood.contains("annoyed") || mood.contains("furious"))
            return pick("I don't have time for this.", "Leave me be, " + companion + ".");
        return pick("Interesting! Tell me more.", "Ha, you don't say!", "That reminds me, I should get back to work soon.",
                "Is that so? Life in the village is quieter than that.", "You always have stories, " + companion + ".");
    }
}
