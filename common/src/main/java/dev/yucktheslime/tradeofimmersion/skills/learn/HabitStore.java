package dev.yucktheslime.tradeofimmersion.skills.learn;

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
import java.util.UUID;

/** Per-player habit models: {@code <world>/data/tradeofimmersion/habits.json}. */
public final class HabitStore {
    private static final HabitStore INSTANCE = new HabitStore();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private Map<String, HabitModel> players = new LinkedHashMap<>();
    private Path file;
    private boolean dirty;

    public static HabitStore get() {
        return INSTANCE;
    }

    public synchronized void open(MinecraftServer server) {
        file = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(TradeOfImmersion.MOD_ID).resolve("habits.json");
        players = new LinkedHashMap<>();
        try {
            if (Files.exists(file)) {
                Map<String, HabitModel> m = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), new TypeToken<LinkedHashMap<String, HabitModel>>() {
                }.getType());
                if (m != null) players = m;
            }
        } catch (Exception e) {
            TradeOfImmersion.LOGGER.warn("Bad habits file {}", file, e);
        }
    }

    public synchronized HabitModel of(UUID player) {
        dirty = true;
        return players.computeIfAbsent(player.toString(), k -> new HabitModel());
    }

    public synchronized HabitModel peek(UUID player) {
        return players.get(player.toString());
    }

    public synchronized void forget(UUID player) {
        players.remove(player.toString());
        dirty = true;
    }

    public synchronized void save() {
        if (file == null || !dirty) return;
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(players), StandardCharsets.UTF_8);
            dirty = false;
        } catch (Exception e) {
            TradeOfImmersion.LOGGER.warn("Could not write {}", file, e);
        }
    }

    public synchronized void close() {
        save();
        players = new LinkedHashMap<>();
        file = null;
    }
}
