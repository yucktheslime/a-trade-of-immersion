package dev.yucktheslime.mcaplayer2.cobblemon;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.player2api.AgentConversationData;
import dev.architectury.event.events.common.TickEvent;
import dev.yucktheslime.mcaplayer2.McaPlayer2Bridge;
import dev.yucktheslime.mcaplayer2.context.PromptContextProvider;
import dev.yucktheslime.mcaplayer2.player2.CompanionAdapter;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * All Cobblemon class references used at runtime live here. Only entered through
 * {@link CobblemonHooks} after a successful soft-load check.
 */
public final class CobblemonPartyBootstrap {
    private static final Set<UUID> injected = new HashSet<>();
    private static boolean eventsRegistered;

    private CobblemonPartyBootstrap() {
    }

    public static void init() {
        PromptContextProvider.register(PokemonPartyContextProvider.INSTANCE);
        if (!eventsRegistered) {
            TickEvent.SERVER_POST.register(CobblemonPartyBootstrap::onServerTick);
            eventsRegistered = true;
        }
        McaPlayer2Bridge.LOGGER.info("Cobblemon detected; companion Pokémon party store enabled (target 1.7.3)");
    }

    public static void onServerStarted(MinecraftServer server) {
        injected.clear();
        CompanionPartyManager.get().load(server);
    }

    public static void onServerStopping() {
        CompanionPartyManager.get().save();
        CompanionPartyManager.get().unload();
        injected.clear();
    }

    public static void registerCompanionCommands(PlayerEngineController controller) {
        try {
            controller.getCommandExecutor().registerNewCommand(new PokemonPartyCommand());
        } catch (Throwable t) {
            McaPlayer2Bridge.LOGGER.error("Failed to register pokemon_party command", t);
        }
    }

    /**
     * Pushes the current party into the companion's next Player2 prompt (without starting a turn)
     * and remembers that this companion has been told, so the login tick will not repeat it.
     */
    public static void injectPartyContext(AgentConversationData companion) {
        LivingEntity self = CompanionAdapter.safeEntity(companion);
        if (self == null) return;
        CompanionAdapter.injectInfo(companion, PokemonPartyFormatter.describeParty(self.getUUID(), true));
        injected.add(self.getUUID());
    }

    private static void onServerTick(MinecraftServer server) {
        if (CompanionPartyManager.get().registryAccess() == null) return;
        for (AgentConversationData d : CompanionAdapter.liveCompanions()) {
            LivingEntity e = CompanionAdapter.safeEntity(d);
            if (e == null) continue;
            UUID id = e.getUUID();
            if (!injected.add(id)) continue;
            CompanionAdapter.injectInfo(d, PokemonPartyFormatter.describeParty(id, true));
        }
    }
}
