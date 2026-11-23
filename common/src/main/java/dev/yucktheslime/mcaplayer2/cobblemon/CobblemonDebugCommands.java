package dev.yucktheslime.mcaplayer2.cobblemon;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.player2.playerengine.player2api.AgentConversationData;
import dev.yucktheslime.mcaplayer2.player2.CompanionAdapter;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Permission-level-2 debug / admin commands under {@code /mcaplayer2 pokemon}.
 *
 * <pre>
 * /mcaplayer2 pokemon give &lt;companion&gt; &lt;species&gt; [level]
 * /mcaplayer2 pokemon party &lt;companion&gt;
 * </pre>
 */
public final class CobblemonDebugCommands {
    private static final int DEFAULT_LEVEL = 5;
    private static final SimpleCommandExceptionType NO_COMPANION =
            new SimpleCommandExceptionType(Component.literal("No loaded Player2 companion with that name or UUID."));
    private static final SimpleCommandExceptionType NO_SPECIES =
            new SimpleCommandExceptionType(Component.literal("Unknown Pokémon species. Try a name like bulbasaur, or cobblemon:bulbasaur."));

    private static final SuggestionProvider<CommandSourceStack> COMPANIONS = (ctx, b) -> {
        List<String> names = new ArrayList<>();
        for (AgentConversationData d : CompanionAdapter.liveCompanions()) {
            String n = CompanionAdapter.name(d);
            names.add(n.matches("[A-Za-z0-9_.+-]+") ? n : "\"" + n.replace("\"", "") + "\"");
        }
        return SharedSuggestionProvider.suggest(names, b);
    };

    private static final SuggestionProvider<CommandSourceStack> SPECIES = (ctx, b) -> {
        List<String> names = new ArrayList<>();
        for (Species species : PokemonSpecies.INSTANCE.getImplemented()) {
            String id = species.showdownId();
            if (id != null && !id.isBlank()) names.add(id);
        }
        return SharedSuggestionProvider.suggest(names, b);
    };

    private CobblemonDebugCommands() {
    }

    public static void attach(LiteralArgumentBuilder<CommandSourceStack> root) {
        var companion = Commands.argument("companion", StringArgumentType.string()).suggests(COMPANIONS);
        root.then(Commands.literal("pokemon")
                .then(Commands.literal("give").then(companion
                        .then(Commands.argument("species", StringArgumentType.word()).suggests(SPECIES)
                                .executes(c -> give(c, DEFAULT_LEVEL))
                                .then(Commands.argument("level", IntegerArgumentType.integer(1, 100))
                                        .executes(c -> give(c, IntegerArgumentType.getInteger(c, "level")))))))
                .then(Commands.literal("party").then(
                        Commands.argument("companion", StringArgumentType.string()).suggests(COMPANIONS)
                                .executes(CobblemonDebugCommands::showParty))));
    }

    private record Target(UUID id, String name, AgentConversationData data) {
    }

    private static Target resolve(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String arg = StringArgumentType.getString(ctx, "companion");
        for (AgentConversationData d : CompanionAdapter.liveCompanions()) {
            LivingEntity e = CompanionAdapter.safeEntity(d);
            if (e == null) continue;
            String n = CompanionAdapter.name(d);
            if (n.equalsIgnoreCase(arg) || e.getUUID().toString().equalsIgnoreCase(arg)) {
                return new Target(e.getUUID(), n, d);
            }
        }
        throw NO_COMPANION.create();
    }

    private static int give(CommandContext<CommandSourceStack> ctx, int level) throws CommandSyntaxException {
        Target t = resolve(ctx);
        String raw = StringArgumentType.getString(ctx, "species");
        Species species = findSpecies(raw);
        if (species == null) throw NO_SPECIES.create();

        CompanionPartyStore store = CompanionPartyManager.get().getOrCreate(t.id());
        if (store.getFirstAvailablePosition() == null) {
            ctx.getSource().sendFailure(Component.literal(t.name() + "'s party is full (" + store.occupied() + "/" + store.size() + ")."));
            return 0;
        }

        String speciesId = species.showdownId();
        Pokemon pokemon = PokemonProperties.Companion.parse(speciesId + " level=" + level, " ", "=").create(null);
        if (!store.add(pokemon)) {
            ctx.getSource().sendFailure(Component.literal("Could not add " + species.getTranslatedName().getString() + " to " + t.name() + "'s party."));
            return 0;
        }
        CompanionPartyManager.get().save();
        CobblemonPartyBootstrap.injectPartyContext(t.data());
        String shown = PokemonPartyFormatter.describePokemon(pokemon, store.occupied());
        ctx.getSource().sendSuccess(() -> Component.literal("Gave " + t.name() + " a " + shown), true);
        return 1;
    }

    private static int showParty(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Target t = resolve(ctx);
        ctx.getSource().sendSuccess(() -> Component.literal(t.name() + " — " + PokemonPartyFormatter.describeParty(t.id(), false)), false);
        return 1;
    }

    static Species findSpecies(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.toLowerCase(Locale.ROOT).trim();
        if (s.contains(":")) {
            ResourceLocation id = ResourceLocation.tryParse(s);
            return id == null ? null : PokemonSpecies.INSTANCE.getByIdentifier(id);
        }
        Species byName = PokemonSpecies.INSTANCE.getByName(s);
        if (byName != null) return byName;
        ResourceLocation fallback = ResourceLocation.tryParse("cobblemon:" + s);
        return fallback == null ? null : PokemonSpecies.INSTANCE.getByIdentifier(fallback);
    }
}
