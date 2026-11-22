package dev.yucktheslime.mcaplayer2.memory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.yucktheslime.mcaplayer2.McaPlayer2Bridge;
import dev.yucktheslime.mcaplayer2.config.BridgeConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-villager conversation log persisted as JSON in the world save:
 * {@code <world>/data/mca_player2_bridge/villagers/<villager-uuid>.json}.
 *
 * <p>Both sides read it: the companion gets the recent lines injected as context when it meets the
 * villager, and the villager reply generator uses them in the villager's prompt. Companions are
 * keyed by their character <em>name</em> (not entity UUID) because Player2NPC can respawn a
 * companion with a fresh entity UUID.
 */
public final class ConversationMemoryStore {
    private static final ConversationMemoryStore INSTANCE = new ConversationMemoryStore();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Map<UUID, VillagerLog> logs = new ConcurrentHashMap<>();
    private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();
    private Path dir;

    public static ConversationMemoryStore get() {
        return INSTANCE;
    }

    public synchronized void open(MinecraftServer server) {
        logs.clear();
        dirty.clear();
        dir = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(McaPlayer2Bridge.MOD_ID).resolve("villagers");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            McaPlayer2Bridge.LOGGER.error("Could not create memory dir {}", dir, e);
        }
    }

    public synchronized void close() {
        logs.clear();
        dirty.clear();
        dir = null;
    }

    public VillagerLog log(UUID villager) {
        return logs.computeIfAbsent(villager, this::load);
    }

    public int entryCount(UUID villager) {
        return log(villager).entries.size();
    }

    public void append(UUID villager, String villagerName, String companionName, Speaker speaker, String text, long gameTime) {
        VillagerLog log = log(villager);
        synchronized (log) {
            log.villagerUuid = villager.toString();
            log.villagerName = villagerName;
            log.entries.add(new Entry(System.currentTimeMillis(), gameTime, speaker, companionName, text));
            int max = Math.max(10, BridgeConfig.get().maxLogEntries);
            while (log.entries.size() > max) log.entries.remove(0);
            log.exchangesByCompanion.merge(companionName, speaker == Speaker.VILLAGER ? 1 : 0, Integer::sum);
        }
        dirty.add(villager);
    }

    /** Last {@code n} lines between this villager and (optionally) one companion, oldest first. */
    public List<String> recentLines(UUID villager, String companionName, int n) {
        VillagerLog log = log(villager);
        List<String> out = new ArrayList<>();
        synchronized (log) {
            for (int i = log.entries.size() - 1; i >= 0 && out.size() < n; i--) {
                Entry e = log.entries.get(i);
                if (companionName != null && !companionName.equals(e.companion)) continue;
                String who = e.speaker == Speaker.VILLAGER ? log.villagerName : e.companion;
                out.add(0, who + ": " + e.text);
            }
        }
        return out;
    }

    public synchronized void saveDirty() {
        if (dir == null) return;
        for (UUID id : List.copyOf(dirty)) {
            VillagerLog log = logs.get(id);
            if (log == null) continue;
            Path file = dir.resolve(id + ".json");
            Path tmp = dir.resolve(id + ".json.tmp");
            try {
                synchronized (log) {
                    try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                        GSON.toJson(log, w);
                    }
                }
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                dirty.remove(id);
            } catch (IOException e) {
                McaPlayer2Bridge.LOGGER.warn("Failed to save conversation log {}", file, e);
            }
        }
    }

    private VillagerLog load(UUID id) {
        if (dir != null) {
            Path file = dir.resolve(id + ".json");
            if (Files.exists(file)) {
                try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    VillagerLog log = GSON.fromJson(r, VillagerLog.class);
                    if (log != null) {
                        if (log.entries == null) log.entries = new ArrayList<>();
                        if (log.exchangesByCompanion == null) log.exchangesByCompanion = new LinkedHashMap<>();
                        return log;
                    }
                } catch (Exception e) {
                    McaPlayer2Bridge.LOGGER.warn("Corrupt conversation log {}, starting fresh", file, e);
                }
            }
        }
        VillagerLog log = new VillagerLog();
        log.villagerUuid = id.toString();
        return log;
    }

    public enum Speaker { COMPANION, VILLAGER }

    public static final class VillagerLog {
        public int version = 1;
        public String villagerUuid;
        public String villagerName = "";
        /** Number of villager lines exchanged per companion name. */
        public Map<String, Integer> exchangesByCompanion = new LinkedHashMap<>();
        public List<Entry> entries = new ArrayList<>();
    }

    public record Entry(long realTimeMs, long gameTime, Speaker speaker, String companion, String text) {
    }
}
