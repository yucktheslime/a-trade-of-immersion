package dev.yucktheslime.tradeofimmersion.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yucktheslime.tradeofimmersion.config.ToiConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Any OpenAI-compatible {@code /chat/completions} endpoint: local Ollama ({@code http://localhost:11434/v1}),
 * OpenRouter, LM Studio, llama.cpp server, vLLM, ... The key comes from the config or the
 * {@code TOI_API_KEY}/{@code OPENROUTER_API_KEY} environment variable and is only sent when set.
 */
public final class OpenAiBackend implements AiBackend {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @Override
    public String name() {
        ToiConfig cfg = ToiConfig.get();
        return "openai(" + cfg.effectiveBaseUrl() + ", " + cfg.effectiveModel() + ")";
    }

    @Override
    public CompletableFuture<AiReply> companionTurn(TurnRequest req) {
        return complete(req.system(), req.user(), true).thenApply(AiReply::parse);
    }

    @Override
    public CompletableFuture<String> villagerLine(VillagerRequest req) {
        return complete(req.system(), req.user(), false).thenApply(s -> {
            AiReply r = AiReply.parse(s);
            return r.say();
        });
    }

    private CompletableFuture<String> complete(String system, String user, boolean json) {
        ToiConfig cfg = ToiConfig.get();
        JsonObject body = new JsonObject();
        body.addProperty("model", cfg.effectiveModel());
        body.addProperty("temperature", cfg.aiTemperature);
        body.addProperty("max_tokens", cfg.aiMaxTokens);
        JsonArray msgs = new JsonArray();
        msgs.add(msg("system", system));
        msgs.add(msg("user", user));
        body.add("messages", msgs);
        if (json && !cfg.effectiveBaseUrl().contains("openrouter")) {
            // Ollama / most local servers honor this; OpenRouter free models often reject it
            JsonObject fmt = new JsonObject();
            fmt.addProperty("type", "json_object");
            body.add("response_format", fmt);
        }
        String base = cfg.effectiveBaseUrl().replaceAll("/+$", "");
        HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(base + "/chat/completions"))
                .timeout(Duration.ofSeconds(Math.max(5, cfg.aiTimeoutSeconds)))
                .header("Content-Type", "application/json")
                .header("X-Title", "A Trade of Immersion")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        String key = cfg.effectiveApiKey();
        if (!key.isBlank()) rb.header("Authorization", "Bearer " + key);
        return http.sendAsync(rb.build(), HttpResponse.BodyHandlers.ofString()).thenApply(resp -> {
            if (resp.statusCode() / 100 != 2) {
                String b = resp.body() == null ? "" : resp.body();
                throw new IllegalStateException("HTTP " + resp.statusCode() + ": " + (b.length() > 200 ? b.substring(0, 200) : b));
            }
            JsonObject o = JsonParser.parseString(resp.body()).getAsJsonObject();
            return o.getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("message").get("content").getAsString();
        });
    }

    private static JsonObject msg(String role, String content) {
        JsonObject m = new JsonObject();
        m.addProperty("role", role);
        m.addProperty("content", content);
        return m;
    }
}
