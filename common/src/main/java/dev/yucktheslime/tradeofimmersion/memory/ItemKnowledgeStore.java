package dev.yucktheslime.tradeofimmersion.memory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.yucktheslime.tradeofimmersion.TradeOfImmersion;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What companions have learned about items, keyed by registry id: facts from {@code inspect_item} (mod, tooltip,
 * tags, stats) plus short notes the AI wrote itself. Shared by all companions in the world:
 * {@code <world>/data/tradeofimmersion/item_knowledge.json}.
 */
public final class ItemKnowledgeStore {
    private static final ItemKnowledgeStore INSTANCE = new ItemKnowledgeStore();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private Map<String, Knowledge> items = new LinkedHashMap<>();
    private Path file;
    private boolean dirty;

    public static ItemKnowledgeStore get() {
        return INSTANCE;
    }

    public synchronized void open(MinecraftServer server) {
        file = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(TradeOfImmersion.MOD_ID).resolve("item_knowledge.json");
        items = new LinkedHashMap<>();
        try {
            if (Files.exists(file)) {
                Map<String, Knowledge> m = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), new TypeToken<LinkedHashMap<String, Knowledge>>() {
                }.getType());
                if (m != null) items = m;
            }
        } catch (Exception e) {
            TradeOfImmersion.LOGGER.warn("Bad item knowledge file {}", file, e);
        }
    }

    public synchronized void close() {
        save();
        items = new LinkedHashMap<>();
        file = null;
    }

    public synchronized Knowledge get(String id) {
        return items.get(id);
    }

    public synchronized void put(String id, Knowledge k) {
        Knowledge old = items.get(id);
        if (old != null && (k.notes == null || k.notes.isBlank())) k.notes = old.notes;
        items.put(id, k);
        dirty = true;
    }

    public synchronized void addNote(String id, String note) {
        Knowledge k = items.computeIfAbsent(id, x -> new Knowledge());
        k.notes = note.length() > 300 ? note.substring(0, 300) : note;
        dirty = true;
    }

    public synchronized int size() {
        return items.size();
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

    public static final class Knowledge {
        public String name = "";
        public String mod = "";
        public String facts = "";
        /** The AI's own summary of what the item is for. */
        public String notes = "";
    }
}
