package dev.yucktheslime.mcaplayer2.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yucktheslime.mcaplayer2.config.BridgeConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Kagrenac server (https://github.com/Dwemer-Dynamics/Kagrenac), PROTOCOL.md version 1. The server owns the
 * prompt, history, bios and memory; we send the line plus a compact context map and only offer its three
 * built-in actions ({@code follow_player}, {@code hold_position}, {@code attack_hostile}). Never retries.
 */
public final class KagrenacBackend implements AiBackend {
    private static final Set<String> EVENT_TYPES = Set.of("location_entered", "item_given", "combat_started", "combat_ended");
    private static final List<String> OFFERED = List.of("follow_player", "hold_position", "attack_hostile");
    public static volatile String worldSession = "minecraft";
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Override
    public String name() {
        return "kagrenac(" + BridgeConfig.get().kagrenacBaseUrl + ")";
    }

    private static String session() {
        String s = BridgeConfig.get().kagrenacSessionId;
        return safeId(s == null || s.isBlank() ? worldSession : s);
    }

    static String safeId(String s) {
        String t = s.replaceAll("[^A-Za-z0-9_.:-]", "_");
        if (t.isEmpty()) t = "minecraft";
        return t.length() > 64 ? t.substring(0, 64) : t;
    }

    private static String cut(String s, int n) {
        if (s == null || s.isBlank()) return "-";
        return s.length() > n ? s.substring(0, n) : s;
    }

    private static String newRequestId() {
        return "mc-" + UUID.randomUUID().toString().replace("-", "");
    }

    private JsonObject base(String npcId, String npcName, String playerName) {
        JsonObject o = new JsonObject();
        o.addProperty("protocol", 1);
        o.addProperty("request_id", newRequestId());
        o.addProperty("session_id", session());
        JsonObject npc = new JsonObject();
        npc.addProperty("id", safeId(npcId));
        npc.addProperty("name", cut(npcName, 64));
        o.add("npc", npc);
        JsonObject pl = new JsonObject();
        pl.addProperty("name", cut(playerName, 64));
        o.add("player", pl);
        return o;
    }

    private static JsonObject context(Map<String, String> ctx) {
        JsonObject c = new JsonObject();
        int n = 0;
        for (var e : ctx.entrySet()) {
            if (n++ >= 10) break;
            String k = e.getKey().toLowerCase().replaceAll("[^a-z_]", "_");
            if (k.length() > 32) k = k.substring(0, 32);
            c.addProperty(k, cut(e.getValue(), 200));
        }
        return c;
    }

    @Override
    public CompletableFuture<AiReply> companionTurn(TurnRequest req) {
        String player = req.speaker() == null || req.speaker().isBlank() ? req.ownerName() : req.speaker();
        JsonObject body = base("c-" + req.npcId(), req.npcName(), player);
        body.add("context", context(req.context()));
        JsonArray offered = new JsonArray();
        OFFERED.forEach(offered::add);
        body.add("offered_actions", offered);
        String endpoint;
        if (req.eventType() != null && EVENT_TYPES.contains(req.eventType())) {
            endpoint = "event.php";
            body.addProperty("type", req.eventType());
            body.addProperty("text", cut(req.trigger(), 300));
            body.addProperty("respond", true);
        } else {
            endpoint = "turn.php";
            body.addProperty("text", cut(req.eventType() != null ? "Game event: " + req.trigger() : req.trigger(), 1000));
        }
        String expectedNpc = body.getAsJsonObject("npc").get("id").getAsString();
        String expectedReq = body.get("request_id").getAsString();
        return post(endpoint, body).thenApply(o -> {
            if (o.has("request_id") && !expectedReq.equals(o.get("request_id").getAsString())) throw new IllegalStateException("reply for another request");
            String reply = o.has("reply") ? o.get("reply").getAsString() : "";
            List<JsonObject> actions = new ArrayList<>();
            Set<String> seen = new java.util.HashSet<>();
            if (o.get("actions") instanceof JsonArray arr) {
                for (JsonElement e : arr) {
                    if (!e.isJsonObject() || actions.size() >= 4) continue;
                    JsonObject a = e.getAsJsonObject();
                    String n = a.has("name") ? a.get("name").getAsString() : "";
                    if (!seen.add(n)) continue;
                    if (a.has("args") && a.get("args").isJsonObject()) {
                        JsonObject args = a.getAsJsonObject("args");
                        if (args.size() > 1 || args.has("target_id") && !expectedNpc.equals(args.get("target_id").getAsString())) continue;
                    }
                    JsonObject mapped = new JsonObject();
                    switch (n) {
                        case "follow_player" -> mapped.addProperty("action", "follow");
                        case "hold_position" -> mapped.addProperty("action", "stay");
                        case "attack_hostile" -> {
                            mapped.addProperty("action", "attack");
                            mapped.addProperty("target", "hostile");
                        }
                        default -> {
                            continue;
                        }
                    }
                    actions.add(mapped);
                }
            }
            return new AiReply(AiReply.clean(reply), actions, List.of());
        });
    }

    @Override
    public CompletableFuture<String> villagerLine(VillagerRequest req) {
        JsonObject body = base("v-" + req.villagerId(), req.villagerName(), req.companionName());
        body.add("context", context(req.context()));
        body.add("offered_actions", new JsonArray());
        body.addProperty("text", cut(req.line(), 1000));
        return post("turn.php", body).thenApply(o -> o.has("reply") ? AiReply.clean(o.get("reply").getAsString()) : "");
    }

    public CompletableFuture<String> health() {
        HttpRequest r = HttpRequest.newBuilder(URI.create(url("health.php"))).timeout(Duration.ofSeconds(5)).GET().build();
        return http.sendAsync(r, HttpResponse.BodyHandlers.ofString()).thenApply(HttpResponse::body);
    }

    private static String url(String endpoint) {
        return BridgeConfig.get().kagrenacBaseUrl.replaceAll("/+$", "") + "/" + endpoint;
    }

    private CompletableFuture<JsonObject> post(String endpoint, JsonObject body) {
        HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(url(endpoint)))
                .timeout(Duration.ofSeconds(Math.max(5, BridgeConfig.get().aiTimeoutSeconds)))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        String token = BridgeConfig.get().effectiveKagrenacToken();
        if (!token.isBlank()) rb.header("Authorization", "Bearer " + token);
        return http.sendAsync(rb.build(), HttpResponse.BodyHandlers.ofString()).thenApply(resp -> {
            JsonObject o;
            try {
                o = JsonParser.parseString(resp.body()).getAsJsonObject();
            } catch (Exception e) {
                throw new IllegalStateException("Kagrenac HTTP " + resp.statusCode() + " (not JSON)");
            }
            if (resp.statusCode() / 100 != 2 || o.has("ok") && !o.get("ok").getAsBoolean()) {
                String code = o.has("error") ? o.getAsJsonObject("error").get("code").getAsString() : "";
                throw new IllegalStateException("Kagrenac HTTP " + resp.statusCode() + " " + code);
            }
            return o;
        });
    }
}
