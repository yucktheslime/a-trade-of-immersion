package dev.yucktheslime.mcaplayer2.ai;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Offline backend: keyword rules, no network. Used for testing and as the fallback when the real backend is
 * down. Understands e.g. "follow me", "stay", "attack the zombie", "ride the horse", "get off", "come here",
 * "eat", "what is this", "give me the bread", "pick up the items", "talk to Anna about the weather".
 */
public final class MockBackend implements AiBackend {
    private static final Pattern ATTACK = Pattern.compile("\\b(?:attack|kill|fight|hit)\\s+(?:the\\s+|that\\s+)?([a-z0-9_ ]+)");
    private static final Pattern MOUNT = Pattern.compile("\\b(?:ride|mount|get on|get in|sit on|sit in|board)\\s+(?:the\\s+|that\\s+|a\\s+)?([a-z0-9_ ]+)");
    private static final Pattern GIVE = Pattern.compile("\\bgive\\s+(?:me\\s+)?(?:the\\s+|your\\s+|some\\s+)?([a-z0-9_ ]+)");
    private static final Pattern EQUIP = Pattern.compile("\\b(?:equip|wear|hold|wield|put on)\\s+(?:the\\s+|your\\s+)?([a-z0-9_ ]+)");
    private static final Pattern USE = Pattern.compile("\\buse\\s+(?:the\\s+|your\\s+)?([a-z0-9_ ]+?)(?:\\s+on\\s+(?:the\\s+)?([a-z0-9_ ]+))?$");
    private static final Pattern INSPECT = Pattern.compile("\\b(?:inspect|look at|what is|what's|examine)\\s+(?:the\\s+|this\\s+|that\\s+|your\\s+)?([a-z0-9_ ]*)");
    private static final Pattern TALK = Pattern.compile("\\b(?:talk to|speak to|say hi to|greet|ask)\\s+([a-z]+)(?:\\s+(?:about|that)\\s+(.+))?");

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public CompletableFuture<AiReply> companionTurn(TurnRequest req) {
        return CompletableFuture.completedFuture(reply(req));
    }

    static AiReply reply(TurnRequest req) {
        String t = req.trigger().toLowerCase(Locale.ROOT).replaceAll("[!?.,]", " ").replaceAll("\\s+", " ").trim();
        // strip "<name>," addressing
        String first = req.npcName().toLowerCase(Locale.ROOT).split(" ")[0];
        if (t.startsWith(first + " ")) t = t.substring(first.length() + 1);
        List<JsonObject> acts = new ArrayList<>();
        if (req.speaker() != null && req.speaker().startsWith("villager:")) {
            return AiReply.text("Nice talking to you!");
        }
        if (req.eventType() != null) {
            if (req.eventType().equals("item_given")) {
                return new AiReply("Thank you! Let me take a look.", List.of(act("inspect_item", "item", lastWord(req.trigger()))), List.of());
            }
            if (req.trigger().contains("hit you")) return AiReply.text("Hey! Watch it!");
            if (req.trigger().startsWith("[result]") && req.trigger().contains("inspect_item: ") && !req.trigger().contains("inspect_item: FAILED")) {
                String facts = req.trigger().substring(req.trigger().indexOf("inspect_item: ") + 14).split(" \\| ")[0];
                Matcher im = Pattern.compile("\\(([a-z0-9_.-]+:[a-z0-9_/.-]+), from ([^)]+)\\)").matcher(facts);
                String head = facts.split("\\. ")[0];
                List<AiReply.Learn> learn = im.find() ? List.of(new AiReply.Learn(im.group(1), facts.length() > 160 ? facts.substring(0, 160) : facts)) : List.of();
                return new AiReply("Interesting, that's " + head + ". I'll remember it.", List.of(), learn);
            }
            if (req.trigger().startsWith("[result]")) {
                return AiReply.text(req.trigger().contains("FAILED") ? "Hmm, that didn't work." : "Done.");
            }
            return AiReply.text("");
        }
        Matcher m;
        if (t.matches(".*\\b(get off|dismount|get out|hop off)\\b.*")) return new AiReply("Okay, getting off.", List.of(act("dismount")), List.of());
        if ((m = MOUNT.matcher(t)).find()) return new AiReply("Hop on? Sure!", List.of(act("mount", "target", m.group(1).trim())), List.of());
        if (t.matches(".*\\b(stop fighting|stand down|cease)\\b.*")) return new AiReply("Okay, stopping.", List.of(act("attack", "target", "none")), List.of());
        if ((m = ATTACK.matcher(t)).find()) return new AiReply("On it!", List.of(act("attack", "target", m.group(1).trim())), List.of());
        if (t.matches(".*\\b(follow|come with|come along)\\b.*")) return new AiReply("Right behind you.", List.of(act("follow")), List.of());
        if (t.matches(".*\\b(stay|wait|hold position|stop)\\b.*")) return new AiReply("I'll wait here.", List.of(act("stay")), List.of());
        if (t.matches(".*\\b(come here|come to me|over here)\\b.*")) return new AiReply("Coming!", List.of(act("goto", "target", "owner"), act("follow")), List.of());
        if (t.matches(".*\\b(pick up|collect|grab the items|loot)\\b.*")) return new AiReply("I'll grab those.", List.of(act("pick_up")), List.of());
        if (t.matches(".*\\b(eat|hungry|heal)\\b.*")) return new AiReply("Good idea, a snack.", List.of(act("use_item", "item", "food")), List.of());
        if ((m = GIVE.matcher(t)).find()) return new AiReply("Here you go.", List.of(act("give_item", "item", m.group(1).trim())), List.of());
        if ((m = EQUIP.matcher(t)).find()) return new AiReply("Gearing up.", List.of(act("equip", "item", m.group(1).trim())), List.of());
        if ((m = USE.matcher(t)).find()) {
            JsonObject a = act("use_item", "item", m.group(1).trim());
            if (m.group(2) != null) a.addProperty("target", m.group(2).trim());
            return new AiReply("Let's see what this does.", List.of(a), List.of());
        }
        if ((m = INSPECT.matcher(t)).find()) return new AiReply("Let me look.", List.of(act("inspect_item", "item", m.group(1).trim())), List.of());
        if ((m = TALK.matcher(t)).find()) {
            JsonObject a = act("talk_to_villager", "name", m.group(1));
            a.addProperty("message", m.group(2) != null ? "What do you think about " + m.group(2) + "?" : "Hello there! How are you today?");
            return new AiReply("Sure, I'll go say hi.", List.of(a), List.of());
        }
        if (t.matches(".*\\b(hi|hello|hey)\\b.*")) return AiReply.text("Hey " + (req.speaker() == null ? "" : req.speaker()) + "! What are we doing today?");
        return AiReply.text("I heard you: \"" + req.trigger() + "\" (mock AI - set up a real backend in config/mcaplayer2.json)");
    }

    private static String lastWord(String s) {
        String t = s.replaceAll("[.!]$", "").trim();
        int i = t.lastIndexOf(" gave you ");
        if (i >= 0) t = t.substring(i + 10);
        return t.replaceFirst("^\\d+x\\s+", "");
    }

    static JsonObject act(String name, String... kv) {
        JsonObject o = new JsonObject();
        o.addProperty("action", name);
        for (int i = 0; i + 1 < kv.length; i += 2) o.addProperty(kv[i], kv[i + 1]);
        return o;
    }

    @Override
    public CompletableFuture<String> villagerLine(VillagerRequest req) {
        String l = req.line().toLowerCase(Locale.ROOT);
        String r;
        if (l.contains("weather")) r = "The sky looks kind enough today.";
        else if (l.contains("hello") || l.contains("hi ")) r = "Oh, hello! Welcome to our village, " + req.companionName() + ".";
        else if (l.contains("?")) r = "Hmm, I'd have to think about that one.";
        else r = "Is that so? How interesting.";
        return CompletableFuture.completedFuture(r);
    }
}
