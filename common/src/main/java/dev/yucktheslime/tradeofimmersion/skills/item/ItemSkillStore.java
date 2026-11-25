package dev.yucktheslime.tradeofimmersion.skills.item;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.yucktheslime.tradeofimmersion.TradeOfImmersion;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * How companions can use each item, keyed by item id: tooltip lines and the hints parsed from them, key packets
 * learned by watching players, failure counts and the ways marked unusable. Shared by all companions in the world:
 * {@code <world>/data/tradeofimmersion/item_skills.json}.
 */
public final class ItemSkillStore {
    private static final ItemSkillStore INSTANCE = new ItemSkillStore();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private Map<String, Skill> items = new LinkedHashMap<>();
    private Path file;
    private boolean dirty;

    public static ItemSkillStore get() {
        return INSTANCE;
    }

    public static final class Skill {
        public List<String> tooltip = new ArrayList<>();
        /** "server" or "client" (the client tooltip has lines the server can't build). */
        public String tooltipSource = "";
        public List<HintData> hints = new ArrayList<>();
        /** step id -> failures */
        public Map<String, Integer> failures = new LinkedHashMap<>();
        public Set<String> unusable = new LinkedHashSet<>();
        public Map<String, String> unusableWhy = new LinkedHashMap<>();
        /** key label (lower case, e.g. "v", "key.mymod.dash") -> packets that key sent while holding this item */
        public Map<String, List<KeyPacket>> keyPackets = new LinkedHashMap<>();
        public int uses;
    }

    public static final class HintData {
        public String trigger = "";
        public boolean sneak;
        public String key = "";
        public String effect = "";
        public String line = "";

        public TooltipParser.Hint toHint() {
            TooltipParser.Trigger t;
            try {
                t = TooltipParser.Trigger.valueOf(trigger);
            } catch (Exception e) {
                t = TooltipParser.Trigger.USE;
            }
            return new TooltipParser.Hint(t, sneak, key == null ? "" : key, effect == null ? "" : effect, line == null ? "" : line);
        }

        public static HintData of(TooltipParser.Hint h) {
            HintData d = new HintData();
            d.trigger = h.trigger().name();
            d.sneak = h.sneak();
            d.key = h.key();
            d.effect = h.effect();
            d.line = h.line();
            return d;
        }
    }

    /** One learned key packet: channel id, encoded body (base64) and the key's names. */
    public static final class KeyPacket {
        public String channel = "";
        public String data = "";
        public String keyMapping = "";
        public String keyLabel = "";
        public String keyBound = "";
        public int sightings;
    }

    public synchronized void open(MinecraftServer server) {
        file = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(TradeOfImmersion.MOD_ID).resolve("item_skills.json");
        items = new LinkedHashMap<>();
        try {
            if (Files.exists(file)) {
                Map<String, Skill> m = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), new TypeToken<LinkedHashMap<String, Skill>>() {
                }.getType());
                if (m != null) items = m;
            }
        } catch (Exception e) {
            TradeOfImmersion.LOGGER.warn("Bad item skills file {}", file, e);
        }
    }

    public synchronized Skill get(String id) {
        return items.get(id);
    }

    public synchronized Skill of(String id) {
        dirty = true;
        return items.computeIfAbsent(id, k -> new Skill());
    }

    public synchronized Map<String, Skill> all() {
        return new LinkedHashMap<>(items);
    }

    public synchronized void forget(String id) {
        items.remove(id);
        dirty = true;
    }

    public synchronized void markDirty() {
        dirty = true;
    }

    public synchronized void save() {
        if (file == null || !dirty) return;
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(items), StandardCharsets.UTF_8);
            dirty = false;
        } catch (Exception e) {
            TradeOfImmersion.LOGGER.warn("Could not write {}", file, e);
        }
    }

    public synchronized void close() {
        save();
        items = new LinkedHashMap<>();
        file = null;
    }
}
