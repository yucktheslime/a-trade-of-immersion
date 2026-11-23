package dev.yucktheslime.mcaplayer2.cobblemon;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.player2.playerengine.PlayerEngineController;
import dev.architectury.platform.Platform;
import dev.yucktheslime.mcaplayer2.McaPlayer2Bridge;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;

/**
 * Safe entry point for optional Cobblemon features.
 *
 * <p>This class must not import any {@code com.cobblemon} type. Calling {@link #isLoaded()} or
 * {@link #init()} is always safe, including when Cobblemon is not installed. Methods that talk to
 * Cobblemon types live in a separate {@code *Loaded} method so the JVM will not resolve those
 * classes unless Cobblemon is actually present.
 */
public final class CobblemonHooks {
    public static final String MOD_ID = "cobblemon";

    private static final boolean LOADED = detect();

    private CobblemonHooks() {
    }

    public static boolean isLoaded() {
        return LOADED;
    }

    public static void init() {
        if (!LOADED) {
            McaPlayer2Bridge.LOGGER.info("Cobblemon not present; companion Pokémon party features are disabled");
            return;
        }
        initLoaded();
    }

    public static void onServerStarted(MinecraftServer server) {
        if (!LOADED) return;
        onServerStartedLoaded(server);
    }

    public static void onServerStopping() {
        if (!LOADED) return;
        onServerStoppingLoaded();
    }

    public static void registerCompanionCommands(PlayerEngineController controller) {
        if (!LOADED) return;
        registerCompanionCommandsLoaded(controller);
    }

    public static void attachDebugCommands(LiteralArgumentBuilder<CommandSourceStack> root) {
        if (!LOADED) return;
        attachDebugCommandsLoaded(root);
    }

    private static boolean detect() {
        try {
            if (!Platform.isModLoaded(MOD_ID)) return false;
            Class.forName("com.cobblemon.mod.common.Cobblemon");
            Class.forName("com.cobblemon.mod.common.api.storage.party.PartyStore");
            Class.forName("com.cobblemon.mod.common.pokemon.Pokemon");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void initLoaded() {
        CobblemonPartyBootstrap.init();
    }

    private static void onServerStartedLoaded(MinecraftServer server) {
        CobblemonPartyBootstrap.onServerStarted(server);
    }

    private static void onServerStoppingLoaded() {
        CobblemonPartyBootstrap.onServerStopping();
    }

    private static void registerCompanionCommandsLoaded(PlayerEngineController controller) {
        CobblemonPartyBootstrap.registerCompanionCommands(controller);
    }

    private static void attachDebugCommandsLoaded(LiteralArgumentBuilder<CommandSourceStack> root) {
        CobblemonDebugCommands.attach(root);
    }
}
