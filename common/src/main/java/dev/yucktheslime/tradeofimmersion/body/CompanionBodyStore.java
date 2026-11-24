package dev.yucktheslime.tradeofimmersion.body;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yucktheslime.tradeofimmersion.TradeOfImmersion;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Server-side store: {@code <world>/data/tradeofimmersion/companion_bodies.json}, keyed by companion entity UUID. */
public final class CompanionBodyStore {
    private static final CompanionBodyStore INSTANCE = new CompanionBodyStore();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Map<UUID, CompanionBodyData> data = new LinkedHashMap<>();
    private Path file;

    public static CompanionBodyStore get() {
        return INSTANCE;
    }

    public synchronized void load(MinecraftServer server) {
        data.clear();
        file = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(TradeOfImmersion.MOD_ID).resolve("companion_bodies.json");
        if (!Files.exists(file)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var e : root.entrySet()) {
                JsonObject o = e.getValue().getAsJsonObject();
                CompanionBodyData d = new CompanionBodyData();
                if (o.has("gender")) d.gender = o.get("gender").getAsString();
                if (o.has("name")) d.name = o.get("name").getAsString();
                if (o.has("genes")) {
                    for (var g : o.getAsJsonObject("genes").entrySet()) d.genes.put(g.getKey(), g.getValue().getAsFloat());
                }
                if (o.has("look")) d.look = o.get("look").getAsString();
                if (o.has("mca")) {
                    try {
                        d.mca = net.minecraft.nbt.TagParser.parseTag(o.get("mca").getAsString());
                    } catch (Exception bad) {
                        TradeOfImmersion.LOGGER.warn("Ignoring unreadable MCA appearance for companion {}", e.getKey());
                    }
                }
                data.put(UUID.fromString(e.getKey()), d);
            }
        } catch (Exception ex) {
            TradeOfImmersion.LOGGER.error("Could not read {}", file, ex);
        }
    }

    public synchronized void unload() {
        data.clear();
        file = null;
    }

    public synchronized CompanionBodyData getOrCreate(UUID id) {
        return data.computeIfAbsent(id, k -> new CompanionBodyData());
    }

    public synchronized CompanionBodyData find(UUID id) {
        return data.get(id);
    }

    public synchronized void reset(UUID id) {
        data.remove(id);
    }

    public synchronized void save() {
        data.values().removeIf(CompanionBodyData::isEmpty);
        if (file == null) return;
        JsonObject root = new JsonObject();
        data.forEach((id, d) -> {
            JsonObject o = new JsonObject();
            if (d.gender != null) o.addProperty("gender", d.gender);
            if (d.name != null) o.addProperty("name", d.name);
            JsonObject g = new JsonObject();
            d.genes.forEach(g::addProperty);
            o.add("genes", g);
            if (d.look != null) o.addProperty("look", d.look);
            if (d.mca != null && !d.mca.isEmpty()) o.addProperty("mca", d.mca.toString()); // SNBT
            root.add(id.toString(), o);
        });
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            TradeOfImmersion.LOGGER.error("Could not write {}", file, ex);
        }
    }

    /** Full snapshot for clients. */
    public synchronized CompoundTag snapshot() {
        CompoundTag t = new CompoundTag();
        data.forEach((id, d) -> {
            if (!d.isEmpty()) t.put(id.toString(), d.toTag());
        });
        return t;
    }
}
