package dev.yucktheslime.mcaplayer2.cobblemon;

import dev.yucktheslime.mcaplayer2.McaPlayer2Bridge;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side store: {@code <world>/data/mcaplayer2/companion_parties.nbt}, keyed by companion entity UUID.
 * Loaded on {@code SERVER_STARTED} and saved on {@code SERVER_STOPPING}, matching {@code CompanionBodyStore}.
 */
public final class CompanionPartyManager {
    private static final CompanionPartyManager INSTANCE = new CompanionPartyManager();
    private static final String FILE_NAME = "companion_parties.nbt";
    private static final String PARTIES_KEY = "parties";

    private final Map<UUID, CompanionPartyStore> stores = new LinkedHashMap<>();
    private Path file;
    private RegistryAccess registryAccess;

    public static CompanionPartyManager get() {
        return INSTANCE;
    }

    public synchronized void load(MinecraftServer server) {
        stores.clear();
        registryAccess = server.registryAccess();
        file = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(McaPlayer2Bridge.MOD_ID).resolve(FILE_NAME);
        if (!Files.exists(file)) return;
        try {
            CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            CompoundTag parties = root.getCompound(PARTIES_KEY);
            for (String key : parties.getAllKeys()) {
                UUID id;
                try {
                    id = UUID.fromString(key);
                } catch (IllegalArgumentException ex) {
                    McaPlayer2Bridge.LOGGER.warn("Skipping invalid companion party key {}", key);
                    continue;
                }
                CompanionPartyStore store = new CompanionPartyStore(id);
                try {
                    store.loadFromNBT(parties.getCompound(key), registryAccess);
                    store.initialize();
                    stores.put(id, store);
                } catch (Exception ex) {
                    McaPlayer2Bridge.LOGGER.error("Could not load party for companion {}", id, ex);
                }
            }
            McaPlayer2Bridge.LOGGER.info("Loaded {} companion Pokémon part{}", stores.size(), stores.size() == 1 ? "y" : "ies");
        } catch (Exception ex) {
            McaPlayer2Bridge.LOGGER.error("Could not read {}", file, ex);
        }
    }

    public synchronized void save() {
        if (file == null || registryAccess == null) return;
        CompoundTag parties = new CompoundTag();
        stores.forEach((id, store) -> {
            try {
                parties.put(id.toString(), store.saveToNBT(new CompoundTag(), registryAccess));
            } catch (Exception ex) {
                McaPlayer2Bridge.LOGGER.error("Could not save party for companion {}", id, ex);
            }
        });
        CompoundTag root = new CompoundTag();
        root.put(PARTIES_KEY, parties);
        try {
            Files.createDirectories(file.getParent());
            NbtIo.writeCompressed(root, file);
        } catch (IOException ex) {
            McaPlayer2Bridge.LOGGER.error("Could not write {}", file, ex);
        }
    }

    public synchronized void unload() {
        stores.clear();
        file = null;
        registryAccess = null;
    }

    public synchronized CompanionPartyStore getOrCreate(UUID companionId) {
        return stores.computeIfAbsent(companionId, CompanionPartyStore::new);
    }

    public synchronized CompanionPartyStore find(UUID companionId) {
        return stores.get(companionId);
    }

    /** Registry access from the last {@link #load(MinecraftServer)}; may be null if no server is running. */
    public synchronized RegistryAccess registryAccess() {
        return registryAccess;
    }
}
