package dev.yucktheslime.tradeofimmersion.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/**
 * What the AI wants the companion to do: a spoken line, actions, and item notes to remember.
 * Parsed leniently from model output ({@code {"say":..,"actions":[{"action":..,...}],"learn":[{"item":..,"notes":..}]}}).
 */
public record AiReply(String say, List<JsonObject> actions, List<Learn> learn, KagrenacTrace trace) {
    public record Learn(String item, String notes) {
    }

    public AiReply(String say, List<JsonObject> actions, List<Learn> learn) {
        this(say, actions, learn, null);
    }

    public static AiReply text(String say) {
        return new AiReply(say, List.of(), List.of());
    }

    public static AiReply parse(String raw) {
        if (raw == null) return text("");
        String s = raw.trim();
        // drop <think>...</think> blocks some local models emit
        s = s.replaceAll("(?s)<think>.*?</think>", "").trim();
        s = s.replaceAll("(?s)^```(?:json)?\\s*", "").replaceAll("(?s)\\s*```\\s*$", "").trim();
        int a = s.indexOf('{'), b = s.lastIndexOf('}');
        if (a >= 0 && b > a) {
            try {
                JsonObject o = JsonParser.parseString(s.substring(a, b + 1)).getAsJsonObject();
                String say = o.has("say") && o.get("say").isJsonPrimitive() ? o.get("say").getAsString()
                        : o.has("reply") && o.get("reply").isJsonPrimitive() ? o.get("reply").getAsString() : "";
                List<JsonObject> actions = new ArrayList<>();
                JsonElement acts = o.get("actions");
                if (acts instanceof JsonArray arr) {
                    for (JsonElement e : arr) {
                        if (e.isJsonObject()) actions.add(e.getAsJsonObject());
                        else if (e.isJsonPrimitive()) {
                            JsonObject x = new JsonObject();
                            x.addProperty("action", e.getAsString());
                            actions.add(x);
                        }
                    }
                }
                List<Learn> learn = new ArrayList<>();
                if (o.get("learn") instanceof JsonArray arr) {
                    for (JsonElement e : arr) {
                        if (e.isJsonObject() && e.getAsJsonObject().has("item") && e.getAsJsonObject().has("notes")) {
                            learn.add(new Learn(e.getAsJsonObject().get("item").getAsString(), e.getAsJsonObject().get("notes").getAsString()));
                        }
                    }
                }
                return new AiReply(clean(say), actions, learn);
            } catch (Exception ignored) {
                // not JSON after all
            }
        }
        return text(clean(s));
    }

    static String clean(String say) {
        String t = say.replaceAll("\\s+", " ").trim();
        if (t.length() > 400) t = t.substring(0, 400) + "...";
        return t;
    }
}
