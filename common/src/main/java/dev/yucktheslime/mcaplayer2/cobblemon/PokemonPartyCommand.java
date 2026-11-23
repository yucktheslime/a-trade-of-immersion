package dev.yucktheslime.mcaplayer2.cobblemon;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.commands.base.ArgParser;
import com.player2.playerengine.commands.base.Command;
import com.player2.playerengine.commands.base.CommandException;
import net.minecraft.world.entity.LivingEntity;

/**
 * LLM-callable PlayerEngine command: {@code pokemon_party}. Lists this companion's own Cobblemon
 * party (species, level, HP, types, moves and remaining PP, ability, held item).
 *
 * <p>Only registered when Cobblemon is present, so this class is never loaded without it.
 */
public class PokemonPartyCommand extends Command {
    public PokemonPartyCommand() throws CommandException {
        super("pokemon_party",
                "List your own Pokémon party: species, level, HP, types, each move with remaining PP, ability, and held item.");
    }

    @Override
    protected void call(PlayerEngineController mod, ArgParser parser) {
        LivingEntity self = mod.getEntity();
        if (self == null) {
            finishWithError("You don't have a body in the world right now, so you cannot see a party.");
            return;
        }
        finishWithInfo(PokemonPartyFormatter.describeParty(self.getUUID(), true));
    }
}
