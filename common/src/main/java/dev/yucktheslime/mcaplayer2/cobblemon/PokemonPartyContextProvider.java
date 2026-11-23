package dev.yucktheslime.mcaplayer2.cobblemon;

import com.player2.playerengine.player2api.AgentConversationData;
import dev.yucktheslime.mcaplayer2.context.PromptContextProvider;
import dev.yucktheslime.mcaplayer2.player2.CompanionAdapter;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/**
 * Adds the companion's Pokémon party to the context injected when a villager conversation opens.
 * Only registered when Cobblemon is present.
 */
public final class PokemonPartyContextProvider implements PromptContextProvider {
    public static final PokemonPartyContextProvider INSTANCE = new PokemonPartyContextProvider();

    private PokemonPartyContextProvider() {
    }

    @Override
    public List<String> contextLines(AgentConversationData companion, VillagerEntityMCA villager) {
        LivingEntity self = CompanionAdapter.safeEntity(companion);
        if (self == null) return List.of();
        return List.of(PokemonPartyFormatter.describeParty(self.getUUID(), true));
    }
}
