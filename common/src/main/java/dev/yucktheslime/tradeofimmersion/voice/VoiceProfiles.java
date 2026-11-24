package dev.yucktheslime.tradeofimmersion.voice;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.yucktheslime.tradeofimmersion.ai.KagrenacBackend;
import dev.yucktheslime.tradeofimmersion.ai.KagrenacWorld;
import dev.yucktheslime.tradeofimmersion.entity.CompanionEntity;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Per-NPC voice profiles kept by the Kagrenac server (npc_voice.php, table ex_npc_voices): each companion and MCA
 * villager has its own PocketTTS speaker, optional language and pitch. The server gives every NPC a distinct
 * voice the first time it speaks; {@code /npc voice}, the companion menu's Voice button and the dashboard's
 * NPC voices page change it.
 */
public final class VoiceProfiles {
    /** Speakers from the last list answer (command suggestions). */
    public static volatile List<String> knownSpeakers = List.of();

    private VoiceProfiles() {
    }

    /** The Kagrenac npc id for an entity (c-uuid companions, v-uuid MCA villagers), or null. */
    public static String npcId(Entity e) {
        if (e instanceof CompanionEntity) return KagrenacBackend.companionId(e.getUUID().toString());
        if (e instanceof VillagerEntityMCA) return KagrenacBackend.villagerId(e.getUUID().toString());
        return null;
    }

    private static CompletableFuture<JsonObject> call(JsonObject body) {
        KagrenacBackend k = KagrenacWorld.kagrenac();
        if (k == null) return CompletableFuture.failedFuture(new IllegalStateException("NPC voices need aiBackend kagrenac (a Kagrenac server with voice)."));
        return k.npcVoice(body).thenApply(o -> {
            if (o.has("speakers") && o.get("speakers").isJsonArray()) knownSpeakers = strings(o.getAsJsonArray("speakers"));
            return o;
        });
    }

    public static CompletableFuture<JsonObject> list() {
        JsonObject b = new JsonObject();
        b.addProperty("action", "list");
        return call(b);
    }

    public static CompletableFuture<JsonObject> get(String npc) {
        JsonObject b = new JsonObject();
        b.addProperty("action", "get");
        b.addProperty("npc", npc);
        return call(b);
    }

    /**
     * Sets a voice. {@code voice} "" = automatic; {@code language}/{@code pitch} null = keep. {@code npcName} names a
     * new NPC on the dashboard.
     */
    public static CompletableFuture<JsonObject> set(String npc, String npcName, String voice, String language, Double pitch) {
        JsonObject b = new JsonObject();
        b.addProperty("action", "set");
        b.addProperty("npc", npc);
        if (npcName != null) b.addProperty("npc_name", npcName);
        if (voice != null) b.addProperty("voice", voice);
        if (language != null) b.addProperty("language", language);
        if (pitch != null) b.addProperty("pitch", pitch);
        return call(b);
    }

    public static boolean ok(JsonObject o) {
        return o.has("ok") && o.get("ok").getAsBoolean();
    }

    public static String error(JsonObject o) {
        try {
            String m = o.getAsJsonObject("error").get("message").getAsString();
            if (o.has("matches")) {
                List<String> ids = new ArrayList<>();
                for (JsonElement e : o.getAsJsonArray("matches")) ids.add(e.getAsJsonObject().get("name").getAsString() + " = " + e.getAsJsonObject().get("id").getAsString());
                m += " " + String.join(", ", ids);
            }
            return m;
        } catch (Exception e) {
            return "Kagrenac answered HTTP " + (o.has("_status") ? o.get("_status").getAsInt() : 0);
        }
    }

    /** "name: voice (how it was chosen)" for one npc object of an answer. */
    public static String describe(JsonObject npc) {
        String name = str(npc, "name");
        String voice = str(npc, "voice");
        String source = str(npc, "source");
        StringBuilder sb = new StringBuilder(name.isEmpty() ? str(npc, "id") : name).append(": ")
                .append(voice.isEmpty() ? "automatic (picked on its next line)" : voice)
                .append(" (").append(switch (source) {
                    case "game" -> "set in game";
                    case "operator" -> "set on the dashboard";
                    default -> "automatic";
                }).append(")");
        if (!str(npc, "language").isEmpty()) sb.append(", language ").append(str(npc, "language"));
        if (!str(npc, "pitch").isEmpty()) sb.append(", pitch ").append(str(npc, "pitch"));
        return sb.toString();
    }

    public static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
    }

    public static List<String> strings(JsonArray a) {
        List<String> out = new ArrayList<>();
        for (JsonElement e : a) if (e.isJsonPrimitive()) out.add(e.getAsString());
        return out;
    }
}
