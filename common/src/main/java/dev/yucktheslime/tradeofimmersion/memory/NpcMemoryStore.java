package dev.yucktheslime.tradeofimmersion.memory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.yucktheslime.tradeofimmersion.TradeOfImmersion;
import dev.yucktheslime.tradeofimmersion.config.ToiConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Per-companion memory: what it saw (deaths, attacks, damage, items given, task results) and what was said.
 * Stored as {@code <world>/data/tradeofimmersion/npcs/<uuid>.json}; fed into prompts.
 */
public final class NpcMemoryStore {
    private static final NpcMemoryStore INSTANCE = new NpcMemoryStore();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Map<UUID, Memory> cache = new ConcurrentHashMap<>();
    private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();
    private Path dir;

    public static NpcMemoryStore get() {
        return INSTANCE;
    }

    /** Optional hook (Phase 4 budget) fired on every cache access so the LRU can track this NPC. */
    public static volatile Consumer<UUID> onAccess = id -> { };

    public synchronized void open(MinecraftServer server) {
        open(server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(TradeOfImmersion.MOD_ID).resolve("npcs"));
    }

    /** Open a directory directly (world load, or unit tests that do not boot a server). */
    public synchronized void open(Path directory) {
        cache.clear();
        dirty.clear();
        dir = directory;
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            TradeOfImmersion.LOGGER.error("Could not create {}", dir, e);
        }
    }

    public synchronized void close() {
        saveDirty();
        cache.clear();
        dir = null;
    }

    public Memory memory(UUID npc) {
        Memory m = cache.computeIfAbsent(npc, this::load);
        onAccess.accept(npc);
        return m;
    }

    public int cacheSize() {
        return cache.size();
    }

    /**
     * Persist this NPC if dirty, then drop it from the RAM cache. The next {@link #memory(UUID)}
     * reloads it from disk. Used by the Phase 4 memory LRU when the NPC unloads or goes idle.
     */
    public synchronized void unload(UUID id) {
        if (id == null) return;
        if (dir != null && dirty.contains(id)) {
            Memory m = cache.get(id);
            if (m != null) {
                try {
                    String json;
                    synchronized (m) {
                        json = GSON.toJson(m);
                    }
                    Files.writeString(dir.resolve(id + ".json"), json, StandardCharsets.UTF_8);
                    dirty.remove(id);
                } catch (IOException e) {
                    TradeOfImmersion.LOGGER.warn("Could not save memory for {} before unload", id, e);
                    return; // keep it in RAM so a later save can retry
                }
            } else {
                dirty.remove(id);
            }
        }
        cache.remove(id);
        dirty.remove(id);
    }

    private Memory load(UUID id) {
        if (dir != null) {
            Path f = dir.resolve(id + ".json");
            if (Files.exists(f)) {
                try {
                    Memory m = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), Memory.class);
                    if (m != null) return m;
                } catch (Exception e) {
                    TradeOfImmersion.LOGGER.warn("Bad memory file {}", f, e);
                }
            }
        }
        return new Memory();
    }

    public void event(UUID npc, String type, String text) {
        Memory m = memory(npc);
        synchronized (m) {
            m.events.add(new Line(System.currentTimeMillis(), type, text));
            int max = Math.max(20, ToiConfig.get().maxMemoryEvents);
            while (m.events.size() > max) m.events.remove(0);
        }
        dirty.add(npc);
    }

    public void chat(UUID npc, String speaker, String text) {
        Memory m = memory(npc);
        synchronized (m) {
            m.chat.add(new Line(System.currentTimeMillis(), speaker, text));
            while (m.chat.size() > 100) m.chat.remove(0);
        }
        dirty.add(npc);
    }

    public List<Line> recentEvents(UUID npc, int n) {
        Memory m = memory(npc);
        synchronized (m) {
            return new ArrayList<>(m.events.subList(Math.max(0, m.events.size() - n), m.events.size()));
        }
    }

    public List<Line> recentChat(UUID npc, int n) {
        Memory m = memory(npc);
        synchronized (m) {
            return new ArrayList<>(m.chat.subList(Math.max(0, m.chat.size() - n), m.chat.size()));
        }
    }

    public synchronized void saveDirty() {
        if (dir == null) return;
        for (UUID id : Set.copyOf(dirty)) {
            Memory m = cache.get(id);
            if (m == null) continue;
            try {
                String json;
                synchronized (m) {
                    json = GSON.toJson(m);
                }
                Files.writeString(dir.resolve(id + ".json"), json, StandardCharsets.UTF_8);
                dirty.remove(id);
            } catch (IOException e) {
                TradeOfImmersion.LOGGER.warn("Could not save memory for {}", id, e);
            }
        }
    }

    public static final class Memory {
        public int version = 1;
        public List<Line> events = new ArrayList<>();
        public List<Line> chat = new ArrayList<>();
    }

    /** {@code who} is the event type for events and the speaker for chat. */
    public record Line(long time, String who, String text) {
    }
}
