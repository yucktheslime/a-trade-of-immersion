package dev.yucktheslime.tradeofimmersion.life;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.yucktheslime.tradeofimmersion.TradeOfImmersion;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Supplier;

/** Small helper: one JSON file per world under {@code <world>/data/tradeofimmersion/}. */
public final class JsonStore<T> {
    static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final String file;
    private final Class<T> type;
    private final Supplier<T> empty;
    private Path path;
    private T data;
    private boolean dirty;

    public JsonStore(String file, Class<T> type, Supplier<T> empty) {
        this.file = file;
        this.type = type;
        this.empty = empty;
        this.data = empty.get();
    }

    public synchronized void open(MinecraftServer server) {
        path = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(TradeOfImmersion.MOD_ID).resolve(file);
        data = null;
        try {
            if (Files.exists(path)) data = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), type);
        } catch (Exception e) {
            TradeOfImmersion.LOGGER.warn("[tradeofimmersion] Bad {}, starting empty", path, e);
        }
        if (data == null) data = empty.get();
        dirty = false;
    }

    public T data() {
        return data;
    }

    public void dirty() {
        dirty = true;
    }

    public synchronized void save() {
        if (path == null || !dirty) return;
        try {
            Files.createDirectories(path.getParent());
            Path tmp = path.resolveSibling(file + ".tmp");
            Files.writeString(tmp, GSON.toJson(data), StandardCharsets.UTF_8);
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            dirty = false;
        } catch (Exception e) {
            TradeOfImmersion.LOGGER.warn("[tradeofimmersion] Could not save {}", path, e);
        }
    }

    public synchronized void close() {
        save();
        path = null;
        data = empty.get();
    }
}
