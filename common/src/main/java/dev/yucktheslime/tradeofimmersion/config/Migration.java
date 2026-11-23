package dev.yucktheslime.tradeofimmersion.config;

import dev.architectury.platform.Platform;
import dev.yucktheslime.tradeofimmersion.TradeOfImmersion;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

/**
 * One-time migration from the mod's pre-0.2.1 identifiers (mod id {@value #LEGACY_ID}). Old files are copied,
 * never moved or deleted, so downgrading still works. Only runs when the new location does not exist yet.
 */
public final class Migration {
    /** Mod id used up to 0.2.0. Only referenced here and by the loader's registry aliases. */
    public static final String LEGACY_ID = "mcaplayer2";
    public static final String LEGACY_ENV_KEY = "MCAPLAYER2_API_KEY";

    private Migration() {
    }

    /** {@code config/mcaplayer2.json} -> {@code config/tradeofimmersion.json}, {@code config/mcaplayer2/} (skins) -> {@code config/tradeofimmersion/}. */
    public static void migrateConfig(Path newConfig) {
        Path dir = Platform.getConfigFolder();
        copyFileIfMissing(dir.resolve(LEGACY_ID + ".json"), newConfig, "config");
        copyTreeIfMissing(dir.resolve(LEGACY_ID), dir.resolve(TradeOfImmersion.MOD_ID), "config folder (skins)");
    }

    /** {@code <world>/data/mcaplayer2/} -> {@code <world>/data/tradeofimmersion/} (memories, item knowledge, villager logs, bodies). */
    public static void migrateWorld(MinecraftServer server) {
        Path data = server.getWorldPath(LevelResource.ROOT).resolve("data");
        copyTreeIfMissing(data.resolve(LEGACY_ID), data.resolve(TradeOfImmersion.MOD_ID), "world data");
    }

    private static void copyFileIfMissing(Path from, Path to, String what) {
        if (Files.exists(to) || !Files.isRegularFile(from)) return;
        try {
            Files.createDirectories(to.getParent());
            Files.copy(from, to, StandardCopyOption.COPY_ATTRIBUTES);
            TradeOfImmersion.LOGGER.info("Migrated {} from {} to {} (old file left in place)", what, from, to);
        } catch (IOException e) {
            TradeOfImmersion.LOGGER.warn("Could not migrate {} from {}", what, from, e);
        }
    }

    private static void copyTreeIfMissing(Path from, Path to, String what) {
        if (Files.exists(to) || !Files.isDirectory(from)) return;
        try (Stream<Path> walk = Files.walk(from)) {
            for (Path p : (Iterable<Path>) walk::iterator) {
                Path target = to.resolve(from.relativize(p).toString());
                if (Files.isDirectory(p)) Files.createDirectories(target);
                else Files.copy(p, target, StandardCopyOption.COPY_ATTRIBUTES);
            }
            TradeOfImmersion.LOGGER.info("Migrated {} from {} to {} (old folder left in place)", what, from, to);
        } catch (IOException e) {
            TradeOfImmersion.LOGGER.warn("Could not migrate {} from {}", what, from, e);
        }
    }
}
