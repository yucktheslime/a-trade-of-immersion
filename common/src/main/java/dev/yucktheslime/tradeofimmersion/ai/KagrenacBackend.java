package dev.yucktheslime.tradeofimmersion.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yucktheslime.tradeofimmersion.TradeOfImmersion;
import dev.yucktheslime.tradeofimmersion.config.ToiConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * Kagrenac server (https://github.com/Dwemer-Dynamics/Kagrenac, PROTOCOL.md version 1), normally the
 * {@code kagrenac-server/} folder of this repository installed in DwemerDistro. The server owns the prompt,
 * history, bios, memory, profiles and voice; the game sends the line plus a compact context map, offers the
 * actions it can run right now, validates every returned action against its own allowlist and reports what it
 * did through result.php.
 *
 * <p>With the A Trade of Immersion server ({@code minecraft_actions} in health capabilities) all thirteen
 * Minecraft actions and the extra event types are used; against a plain Kagrenac template only its three
 * built-in actions and four event types are. Turns are never retried (PROTOCOL.md); log-only world events wait in
 * {@link KagrenacOutbox} and are sent again, each attempt with a new request id, while Kagrenac is unreachable.
 */
public final class KagrenacBackend implements AiBackend {
    /** Protocol action names, in the order they are offered. */
    public static final List<String> ALL_ACTIONS = List.of("follow_player", "hold_position", "attack_hostile", "attack_target",
            "go_to", "mount", "dismount", "equip", "use_item", "give_item", "pick_up", "inspect_item", "talk_to_villager");
    static final Set<String> TEMPLATE_ACTIONS = Set.of("follow_player", "hold_position", "attack_hostile");
    /** Actions whose single argument is required. */
    static final Set<String> NEEDS_VALUE = Set.of("attack_target", "go_to", "equip", "use_item", "give_item", "inspect_item", "talk_to_villager");
    static final Set<String> TEMPLATE_EVENTS = Set.of("location_entered", "item_given", "combat_started", "combat_ended");
    static final Set<String> MINECRAFT_EVENTS = Set.of("entity_died", "npc_hurt", "player_hurt", "villager_spoke", "relationship",
            "action_result", "world_note");
    private static final Pattern VALUE = Pattern.compile("^[\\p{L}\\p{N} _.,:'#~-]{1,80}$");

    public static volatile String worldSession = "minecraft";

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private volatile Set<String> capabilities = Set.of();
    private volatile String serverInfo = "not checked yet";
    /** The last health.php answered with ok true. */
    private volatile boolean healthy;
    /** World events waiting to be sent (event.php); keeps them while Kagrenac is unreachable. */
    final KagrenacOutbox outbox = new KagrenacOutbox(this);
    /** request id currently awaited per npc id; replies for anything else are dropped (stale). */
    private final Map<String, String> awaited = new java.util.concurrent.ConcurrentHashMap<>();

    public KagrenacBackend() {
        securityCheck();
        refreshHealth();
    }

    // ------------------------------------------------------------------ remote mode

    /** True when the URL points at this machine or a private/local network (LAN, WSL, Tailscale tailnet). */
    public static boolean isLocalUrl(String url) {
        try {
            String host = URI.create(url.trim()).getHost();
            if (host == null) return false;
            host = host.toLowerCase(Locale.ROOT);
            if (host.equals("localhost") || host.endsWith(".local") || host.endsWith(".lan") || host.endsWith(".internal")) return true;
            if (!host.matches("[0-9.]+|\\[?[0-9a-f:]+]?")) return false; // a public DNS name (tunnel, domain)
            java.net.InetAddress a = java.net.InetAddress.getByName(host); // a literal: no DNS lookup
            if (a.isLoopbackAddress() || a.isSiteLocalAddress() || a.isLinkLocalAddress() || a.isAnyLocalAddress()) return true;
            byte[] b = a.getAddress();
            return b.length == 4 && (b[0] & 0xFF) == 100 && (b[1] & 0xC0) == 64; // 100.64.0.0/10 (Tailscale)
        } catch (Exception e) {
            return false;
        }
    }

    /** Remote mode: a URL outside this machine and the local network. */
    public static boolean remote() {
        return !isLocalUrl(ToiConfig.get().kagrenacBaseUrl);
    }

    /** Logged once per (re)load. Never prints the token. */
    private static void securityCheck() {
        String url = ToiConfig.get().kagrenacBaseUrl == null ? "" : ToiConfig.get().kagrenacBaseUrl.trim();
        if (!remote()) return;
        if (ToiConfig.get().effectiveKagrenacToken().isBlank()) {
            TradeOfImmersion.LOGGER.warn("Kagrenac URL {} is not local: a token is required (kagrenacToken or env TOI_KAGRENAC_TOKEN). Nothing is sent until one is set.", safeUrl(url));
        }
        if (url.toLowerCase(Locale.ROOT).startsWith("http:")) {
            TradeOfImmersion.LOGGER.warn("Kagrenac URL {} uses plain http over the internet: the token and conversations travel unencrypted. Use an https tunnel (Cloudflare Tunnel, Tailscale Funnel).", safeUrl(url));
        }
    }

    /** The URL without any user:password part, for logs and status. */
    public static String safeUrl(String url) {
        try {
            URI u = URI.create(url.trim());
            return u.getUserInfo() == null ? url.trim() : new URI(u.getScheme(), null, u.getHost(), u.getPort(), u.getPath(), null, null).toString();
        } catch (Exception e) {
            return "(invalid URL)";
        }
    }

    public boolean healthy() {
        return healthy;
    }

    /** Thrown instead of sending anything to a remote URL without a token. */
    public static final class TokenRequiredException extends IllegalStateException {
        TokenRequiredException() {
            super("a token is required for a remote Kagrenac URL");
        }
    }

    @Override
    public String name() {
        return "kagrenac(" + safeUrl(ToiConfig.get().kagrenacBaseUrl) + (remote() ? ", remote" : "") + ")";
    }

    // ------------------------------------------------------------------ helpers

    public static String session() {
        String s = ToiConfig.get().kagrenacSessionId;
        return safeId(s == null || s.isBlank() ? worldSession : s);
    }

    public static String safeId(String s) {
        String t = s.replaceAll("[^A-Za-z0-9_.:-]", "_");
        if (t.isEmpty()) t = "minecraft";
        return t.length() > 64 ? t.substring(0, 64) : t;
    }

    public static String companionId(String uuid) {
        return safeId("c-" + uuid);
    }

    public static String villagerId(String uuid) {
        return safeId("v-" + uuid);
    }

    static String cut(String s, int n) {
        if (s == null || s.isBlank()) return "-";
        String t = s.replaceAll("[\\x00-\\x1F\\x7F]+", " ").trim();
        if (t.isEmpty()) return "-";
        return t.length() > n ? t.substring(0, n) : t;
    }

    static String newRequestId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "");
    }

    public boolean has(String capability) {
        return capabilities.contains(capability);
    }

    public boolean minecraftServer() {
        return has("minecraft_actions");
    }

    public String serverInfo() {
        return serverInfo;
    }

    /** The action names this game offers: its own allowlist, narrowed to what the server can understand. */
    public List<String> offeredActions() {
        Set<String> allowed = new HashSet<>(ToiConfig.get().kagrenacAllowedActions == null ? List.of() : ToiConfig.get().kagrenacAllowedActions);
        List<String> out = new ArrayList<>();
        for (String a : ALL_ACTIONS) {
            if (!allowed.contains(a)) continue;
            if (!minecraftServer() && !TEMPLATE_ACTIONS.contains(a)) continue;
            out.add(a);
        }
        return out;
    }

    private JsonObject base(String prefix, String npcId, String npcName, String playerName) {
        JsonObject o = new JsonObject();
        o.addProperty("protocol", 1);
        o.addProperty("request_id", newRequestId(prefix));
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

    static JsonObject context(Map<String, String> ctx) {
        JsonObject c = new JsonObject();
        int n = 0;
        for (var e : ctx.entrySet()) {
            if (n >= 10) break;
            String k = e.getKey().toLowerCase(Locale.ROOT).replaceAll("[^a-z_]", "_");
            if (k.length() > 32) k = k.substring(0, 32);
            if (k.isEmpty() || c.has(k)) continue;
            c.addProperty(k, cut(e.getValue(), 200));
            n++;
        }
        return c;
    }

    // ------------------------------------------------------------------ turns

    @Override
    public CompletableFuture<AiReply> companionTurn(TurnRequest req) {
        String player = req.speaker() == null || req.speaker().isBlank() || req.speaker().startsWith("villager:")
                ? req.ownerName() : req.speaker();
        String npcId = companionId(req.npcId());
        JsonObject body = base("mc", npcId, req.npcName(), player);
        body.add("context", context(req.context()));
        List<String> offered = offeredActions();
        JsonArray off = new JsonArray();
        offered.forEach(off::add);
        body.add("offered_actions", off);
        String endpoint;
        String type = eventType(req.eventType(), req.speaker());
        if (type != null) {
            endpoint = "event.php";
            body.addProperty("type", type);
            body.addProperty("text", cut(req.trigger(), 300));
            body.addProperty("respond", true);
        } else {
            endpoint = "turn.php";
            body.addProperty("text", cut(req.eventType() != null ? "Game event: " + req.trigger() : req.trigger(), 1000));
        }
        String requestId = body.get("request_id").getAsString();
        awaited.put(npcId, requestId);
        return post(endpoint, body).thenApply(o -> {
            if (!requestId.equals(awaited.get(npcId))) throw new StaleTurnException("replaced by a newer turn");
            awaited.remove(npcId, requestId);
            if (o.has("request_id") && !requestId.equals(o.get("request_id").getAsString())) throw new StaleTurnException("reply for another request");
            if (o.has("npc_id") && !npcId.equals(o.get("npc_id").getAsString())) throw new StaleTurnException("reply for another NPC");
            return parseReply(o, requestId, npcId, offered, player);
        });
    }

    /** Maps our internal event names to the event.php type, or null to send a plain turn. */
    String eventType(String internal, String speaker) {
        if (internal == null) return null;
        String t = switch (internal) {
            case "death" -> "entity_died";
            case "villager_line" -> "villager_spoke";
            default -> internal;
        };
        if (TEMPLATE_EVENTS.contains(t) && has("game_event")) return t;
        if (MINECRAFT_EVENTS.contains(t) && has("minecraft_events")) return t;
        return null;
    }

    /** Validates the reply: only allowlisted, offered names, at most 4 distinct, only target_id/value args. */
    AiReply parseReply(JsonObject o, String requestId, String npcId, List<String> offered, String player) {
        String reply = o.has("reply") && o.get("reply").isJsonPrimitive() ? o.get("reply").getAsString() : "";
        List<JsonObject> actions = new ArrayList<>();
        List<String> returned = new ArrayList<>();
        List<KagrenacTrace.Rejected> rejected = new ArrayList<>();
        Set<String> seen = new HashSet<>(), repeated = new HashSet<>();
        if (o.get("actions") instanceof JsonArray arr) {
            for (JsonElement e : arr) {
                if (!e.isJsonObject()) continue;
                JsonObject a = e.getAsJsonObject();
                String n = a.has("name") && a.get("name").isJsonPrimitive() ? a.get("name").getAsString() : "";
                if (n.isEmpty()) continue;
                if (!seen.add(n)) {
                    // a repeated name is malformed: reject it once and never run it
                    if (repeated.add(n)) {
                        actions.removeIf(x -> n.equals(x.get("_proto").getAsString()));
                        rejected.add(new KagrenacTrace.Rejected(n, "name repeated in reply"));
                    }
                    continue;
                }
                returned.add(n);
                String why = check(a, n, npcId, offered, seen.size());
                if (why != null) {
                    rejected.add(new KagrenacTrace.Rejected(n, why));
                    continue;
                }
                String value = a.has("args") && a.getAsJsonObject("args").has("value") ? a.getAsJsonObject("args").get("value").getAsString().trim() : "";
                JsonObject mapped = map(n, value, reply, player);
                mapped.addProperty("_proto", n);
                actions.add(mapped);
            }
        }
        String say = reply;
        if (actions.stream().anyMatch(x -> "talk_to_villager".equals(x.get("action").getAsString()))) say = ""; // said to the villager instead
        return new AiReply(AiReply.clean(say), actions, List.of(), new KagrenacTrace(requestId, session(), npcId, returned, rejected));
    }

    private String check(JsonObject a, String n, String npcId, List<String> offered, int count) {
        if (count > 4) return "more than 4 actions";
        for (String key : a.keySet()) if (!key.equals("name") && !key.equals("args")) return "unexpected field " + key;
        if (!offered.contains(n)) return "not on the game's allowlist";
        if (a.has("args")) {
            if (!a.get("args").isJsonObject()) return "bad args";
            JsonObject args = a.getAsJsonObject("args");
            for (String key : args.keySet()) {
                if (!key.equals("target_id") && !key.equals("value")) return "unexpected argument " + key;
                if (!args.get(key).isJsonPrimitive()) return "bad argument " + key;
            }
            if (args.has("target_id") && !npcId.equals(args.get("target_id").getAsString())) return "target_id is another NPC";
            if (args.has("value") && !VALUE.matcher(args.get("value").getAsString().trim()).matches()) return "argument is not a plain name";
        }
        boolean hasValue = a.has("args") && a.getAsJsonObject("args").has("value");
        if (NEEDS_VALUE.contains(n) && !hasValue) return "missing argument";
        return null;
    }

    /** One protocol action as the ActionExecutor JSON it runs. */
    static JsonObject map(String n, String value, String reply, String player) {
        JsonObject m = new JsonObject();
        switch (n) {
            case "follow_player" -> m.addProperty("action", "follow");
            case "hold_position" -> m.addProperty("action", "stay");
            case "attack_hostile" -> {
                m.addProperty("action", "attack");
                m.addProperty("target", "hostile");
            }
            case "attack_target" -> {
                m.addProperty("action", "attack");
                m.addProperty("target", value);
            }
            case "go_to" -> {
                m.addProperty("action", "goto");
                m.addProperty("target", value);
            }
            case "mount" -> {
                m.addProperty("action", "mount");
                m.addProperty("target", value.isEmpty() ? "nearest" : value);
            }
            case "dismount" -> m.addProperty("action", "dismount");
            case "equip" -> {
                m.addProperty("action", "equip");
                m.addProperty("item", value);
                m.addProperty("slot", "auto");
            }
            case "use_item" -> {
                m.addProperty("action", "use_item");
                m.addProperty("item", value);
            }
            case "give_item" -> {
                m.addProperty("action", "give_item");
                m.addProperty("item", value);
                m.addProperty("count", 1);
                m.addProperty("to", player);
            }
            case "pick_up" -> m.addProperty("action", "pick_up");
            case "inspect_item" -> {
                m.addProperty("action", "inspect_item");
                m.addProperty("item", value);
            }
            case "talk_to_villager" -> {
                m.addProperty("action", "talk_to_villager");
                m.addProperty("name", value);
                m.addProperty("message", reply == null || reply.isBlank() ? "Hello!" : reply);
            }
            default -> m.addProperty("action", n);
        }
        return m;
    }

    @Override
    public CompletableFuture<String> villagerLine(VillagerRequest req) {
        String npcId = villagerId(req.villagerId());
        JsonObject body = base("mcv", npcId, req.villagerName(), req.companionName());
        body.add("context", context(req.context()));
        body.add("offered_actions", new JsonArray());
        body.addProperty("text", cut(req.line(), 1000));
        return post("turn.php", body).thenApply(o -> o.has("reply") ? AiReply.clean(o.get("reply").getAsString()) : "");
    }

    // ------------------------------------------------------------------ cancel / result / events / bios / decision

    /** Drops any in-flight turn for this NPC (cancel.php). The old reply, if it still arrives, is ignored. */
    public void cancel(String npcId) {
        awaited.remove(npcId);
        JsonObject b = new JsonObject();
        b.addProperty("protocol", 1);
        b.addProperty("session_id", session());
        b.addProperty("npc_id", npcId);
        post("cancel.php", b).exceptionally(err -> {
            TradeOfImmersion.LOGGER.debug("Kagrenac cancel failed: {}", err.toString());
            return null;
        });
    }

    /** One result.php report: what the game did with the actions of a turn it actually used. */
    public void reportResults(KagrenacTrace trace, Map<String, String[]> outcomes) {
        if (trace == null || trace.returned().isEmpty() || !has("result")) return;
        JsonArray results = new JsonArray();
        Set<String> done = new HashSet<>();
        for (var r : trace.clientRejected()) {
            if (!trace.returned().contains(r.name()) || !done.add(r.name()) || results.size() >= 4) continue;
            results.add(result(r.name(), "rejected", r.reason()));
        }
        for (var e : outcomes.entrySet()) {
            if (!trace.returned().contains(e.getKey()) || !done.add(e.getKey()) || results.size() >= 4) continue;
            results.add(result(e.getKey(), e.getValue()[0], e.getValue()[1]));
        }
        if (results.isEmpty()) return;
        JsonObject b = new JsonObject();
        b.addProperty("protocol", 1);
        b.addProperty("request_id", trace.requestId());
        b.addProperty("session_id", trace.sessionId());
        b.addProperty("npc_id", trace.npcId());
        b.add("results", results);
        post("result.php", b).exceptionally(err -> {
            TradeOfImmersion.LOGGER.debug("Kagrenac result report failed: {}", err.toString());
            return null;
        });
    }

    private static JsonObject result(String name, String status, String reason) {
        JsonObject r = new JsonObject();
        r.addProperty("name", name);
        r.addProperty("status", status);
        if (reason != null && !reason.isBlank()) r.addProperty("reason", cut(reason, 120));
        return r;
    }

    /** A log-only game event (event.php, respond false). Unknown types for this server are dropped. */
    public void logEvent(String type, String npcId, String npcName, String text, Map<String, String> ctx) {
        outbox.add(type, npcId, npcName, text, ctx);
    }

    /** Whether this server takes this log-only event type (known only after a health check). */
    boolean acceptsEvent(String type) {
        if (!has("game_event")) return false;
        return TEMPLATE_EVENTS.contains(type) || MINECRAFT_EVENTS.contains(type) && has("minecraft_events");
    }

    /** Sends one queued event; the outbox handles failures and retries. */
    CompletableFuture<JsonObject> sendEvent(String type, String npcId, String npcName, String text, Map<String, String> ctx) {
        JsonObject b = new JsonObject();
        b.addProperty("protocol", 1);
        b.addProperty("request_id", newRequestId("ev"));
        b.addProperty("session_id", session());
        b.addProperty("type", type);
        if (npcId != null) {
            JsonObject npc = new JsonObject();
            npc.addProperty("id", safeId(npcId));
            npc.addProperty("name", cut(npcName, 64));
            b.add("npc", npc);
        }
        b.addProperty("text", cut(text, 300));
        if (ctx != null && !ctx.isEmpty()) b.add("context", context(ctx));
        b.addProperty("respond", false);
        return post("event.php", b);
    }

    public record Bio(String id, String name, String role, String bio) {
    }

    /** Pushes bios (npc_bio.php, A Trade of Immersion server only), at most 16 per request. */
    public CompletableFuture<Void> syncBios(List<Bio> bios) {
        if (!has("bio_sync") || bios.isEmpty()) return CompletableFuture.completedFuture(null);
        List<CompletableFuture<JsonObject>> calls = new ArrayList<>();
        for (int i = 0; i < bios.size(); i += 16) {
            JsonArray arr = new JsonArray();
            for (Bio bio : bios.subList(i, Math.min(bios.size(), i + 16))) {
                JsonObject n = new JsonObject();
                n.addProperty("id", safeId(bio.id()));
                n.addProperty("name", cut(bio.name(), 64));
                if (bio.role() != null && !bio.role().isBlank()) n.addProperty("role", cut(bio.role(), 64));
                n.addProperty("bio", cut(bio.bio(), 1000));
                arr.add(n);
            }
            JsonObject b = new JsonObject();
            b.addProperty("protocol", 1);
            b.add("npcs", arr);
            calls.add(post("npc_bio.php", b));
        }
        return CompletableFuture.allOf(calls.toArray(new CompletableFuture[0]));
    }

    public record Candidate(String id, String name, String cues) {
    }

    /** decision.php: which offered NPC should answer. Always completes with one of the ids (baseline on any problem). */
    public CompletableFuture<String> decide(String transcript, String baselineId, List<Candidate> candidates) {
        if (!has("decision") || candidates.size() < 2) return CompletableFuture.completedFuture(baselineId);
        JsonObject b = new JsonObject();
        b.addProperty("protocol", 1);
        b.addProperty("request_id", newRequestId("dec"));
        b.addProperty("transcript", cut(transcript, 2000));
        b.addProperty("baseline_id", baselineId);
        JsonArray arr = new JsonArray();
        Set<String> ids = new HashSet<>();
        for (Candidate c : candidates.subList(0, Math.min(8, candidates.size()))) {
            JsonObject x = new JsonObject();
            x.addProperty("id", c.id());
            x.addProperty("name", cut(c.name(), 64));
            if (c.cues() != null && !c.cues().isBlank()) x.addProperty("cues", cut(c.cues(), 200));
            arr.add(x);
            ids.add(c.id());
        }
        b.add("candidates", arr);
        return post("decision.php", b).handle((o, err) -> {
            if (err != null || o == null || !o.has("chosen_id")) return baselineId;
            String chosen = o.get("chosen_id").getAsString();
            return ids.contains(chosen) ? chosen : baselineId; // check it against our own list anyway
        });
    }

    /** speak.php: WAV bytes for a line (fetched by the Minecraft server, then streamed to nearby players). */
    public CompletableFuture<byte[]> speak(String text, String npcId, String parentRequestId, String voiceHint) {
        JsonObject b = new JsonObject();
        b.addProperty("protocol", 1);
        b.addProperty("request_id", newRequestId("tts"));
        if (parentRequestId != null && parentRequestId.matches("[A-Za-z0-9_.:-]{8,64}")) b.addProperty("parent_request_id", parentRequestId);
        if (npcId != null && !npcId.isBlank()) b.addProperty("npc_id", safeId(npcId));
        if (voiceHint != null && voiceHint.matches("[a-z_]{1,16}")) b.addProperty("voice_hint", voiceHint);
        b.addProperty("text", cut(text, 1000));
        HttpRequest.Builder rb;
        try {
            rb = request("speak.php", Math.max(10, ToiConfig.get().aiTimeoutSeconds)).POST(HttpRequest.BodyPublishers.ofString(b.toString()));
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
        return http.sendAsync(rb.build(), HttpResponse.BodyHandlers.ofByteArray()).thenApply(resp -> {
            byte[] wav = resp.body();
            if (resp.statusCode() != 200) {
                String code = "";
                try {
                    code = JsonParser.parseString(new String(wav, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject()
                            .getAsJsonObject("error").get("code").getAsString();
                } catch (Exception ignored) {
                }
                throw new IllegalStateException("speak.php HTTP " + resp.statusCode() + " " + code);
            }
            if (wav.length < 44 || wav[0] != 'R' || wav[1] != 'I' || wav[2] != 'F' || wav[3] != 'F' || wav[8] != 'W' || wav[9] != 'A' || wav[10] != 'V' || wav[11] != 'E') {
                throw new IllegalStateException("speak.php did not return a WAV");
            }
            return wav;
        });
    }

    // ------------------------------------------------------------------ transport

    /** GET health.php (no token) and remember the server's capabilities. */
    public CompletableFuture<String> refreshHealth() {
        HttpRequest r;
        try {
            r = HttpRequest.newBuilder(URI.create(url("health.php"))).timeout(Duration.ofSeconds(5)).GET().build();
        } catch (RuntimeException e) {
            healthy = false;
            serverInfo = "bad kagrenacBaseUrl";
            return CompletableFuture.completedFuture(serverInfo);
        }
        return http.sendAsync(r, HttpResponse.BodyHandlers.ofString()).handle((resp, err) -> {
            if (err != null) {
                healthy = false;
                serverInfo = "unreachable (" + (err.getCause() != null ? err.getCause() : err).getClass().getSimpleName() + ")";
                return serverInfo;
            }
            try {
                JsonObject o = JsonParser.parseString(resp.body()).getAsJsonObject();
                Set<String> caps = new HashSet<>();
                if (o.get("capabilities") instanceof JsonArray arr) arr.forEach(e -> caps.add(e.getAsString()));
                capabilities = Set.copyOf(caps);
                boolean ok = o.has("ok") && o.get("ok").getAsBoolean();
                healthy = ok;
                serverInfo = (ok ? "ready" : "not ready (" + (o.has("error") ? o.getAsJsonObject("error").get("code").getAsString() : "HTTP " + resp.statusCode()) + ")")
                        + ", server " + (o.has("server_version") ? o.get("server_version").getAsString() : "?")
                        + ", schema " + (o.has("schema") ? o.get("schema").getAsString() : "?")
                        + (minecraftServer() ? ", Minecraft actions (" + offeredActions().size() + " offered)" : ", template actions only (install kagrenac-server/ for all actions)");
            } catch (Exception e) {
                healthy = false;
                serverInfo = "HTTP " + resp.statusCode() + " (not a Kagrenac server?)";
            }
            return serverInfo;
        });
    }

    private static String url(String endpoint) {
        return ToiConfig.get().kagrenacBaseUrl.trim().replaceAll("/+$", "") + "/" + endpoint;
    }

    private HttpRequest.Builder request(String endpoint, int timeoutSeconds) {
        HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(url(endpoint)))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "application/json");
        String token = ToiConfig.get().effectiveKagrenacToken();
        if (token.isBlank() && remote()) throw new TokenRequiredException();
        if (!token.isBlank()) rb.header("Authorization", "Bearer " + token);
        return rb;
    }

    private CompletableFuture<JsonObject> post(String endpoint, JsonObject body) {
        HttpRequest.Builder rb;
        try {
            rb = request(endpoint, Math.max(5, ToiConfig.get().aiTimeoutSeconds)).POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
        return http.sendAsync(rb.build(), HttpResponse.BodyHandlers.ofString()).thenApply(resp -> {
            JsonObject o;
            try {
                o = JsonParser.parseString(resp.body()).getAsJsonObject();
            } catch (Exception e) {
                throw new IllegalStateException("Kagrenac " + endpoint + " HTTP " + resp.statusCode() + " (not JSON)");
            }
            if (resp.statusCode() / 100 != 2 || o.has("ok") && !o.get("ok").getAsBoolean()) {
                String code = o.has("error") && o.get("error").isJsonObject() && o.getAsJsonObject("error").has("code")
                        ? o.getAsJsonObject("error").get("code").getAsString() : "";
                if (resp.statusCode() == 409 && code.equals("stale")) throw new StaleTurnException("cancelled or replaced");
                throw new IllegalStateException("Kagrenac " + endpoint + " HTTP " + resp.statusCode() + " " + code);
            }
            return o;
        });
    }

    /** The turn was cancelled or replaced: drop it silently (not a backend failure). */
    public static final class StaleTurnException extends RuntimeException {
        public StaleTurnException(String message) {
            super(message);
        }
    }

    static Map<String, String> ctx() {
        return new LinkedHashMap<>();
    }
}
