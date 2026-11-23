package dev.yucktheslime.mcaplayer2.cobblemon;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.storage.party.PartyStore;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.status.PersistentStatusContainer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/** Human-readable party text for the Player2 prompt and the {@code pokemon_party} command. */
public final class PokemonPartyFormatter {
    private PokemonPartyFormatter() {
    }

    public static String describeParty(UUID companionId, boolean forCompanionPrompt) {
        CompanionPartyStore store = CompanionPartyManager.get().find(companionId);
        if (store == null || store.isEmpty()) {
            return forCompanionPrompt
                    ? "Your Pokémon party is empty. You do not have any Pokémon yet."
                    : "Your Pokémon party is empty.";
        }
        return describe(store, forCompanionPrompt);
    }

    public static String describe(PartyStore store, boolean forCompanionPrompt) {
        List<String> lines = new ArrayList<>();
        int occupied = store.occupied();
        int size = store.size();
        lines.add((forCompanionPrompt ? "Your Pokémon party" : "Pokémon party") + " (" + occupied + "/" + size + "):");
        int slot = 0;
        for (Pokemon p : store) {
            slot++;
            lines.add("- " + describePokemon(p, slot));
        }
        return String.join("\n", lines);
    }

    public static String describePokemon(Pokemon p, int slot) {
        String species = p.getSpecies().getTranslatedName().getString();
        String types = StreamSupport.stream(p.getTypes().spliterator(), false)
                .map(PokemonPartyFormatter::typeName)
                .collect(Collectors.joining("/"));
        PersistentStatusContainer status = p.getStatus();
        String statusText = "";
        if (status != null && status.getStatus() != null) {
            statusText = " status " + status.getStatus().getShowdownName();
        }
        String ability = abilityName(p);
        String item = heldItemName(p);
        String moves = describeMoves(p);
        return species + " (slot " + slot + ") Lv." + p.getLevel()
                + " HP " + p.getCurrentHealth() + "/" + p.getMaxHealth()
                + " types " + (types.isEmpty() ? "unknown" : types)
                + " ability " + ability
                + " item " + item
                + statusText
                + ". Moves: " + moves;
    }

    private static String describeMoves(Pokemon p) {
        List<Move> moves = p.getMoveSet().getMoves();
        if (moves == null || moves.isEmpty()) return "none";
        List<String> parts = new ArrayList<>();
        for (Move move : moves) {
            String name = move.getDisplayName() != null ? move.getDisplayName().getString() : move.getName();
            parts.add(name + " " + move.getCurrentPp() + "/" + move.getMaxPp() + " PP");
        }
        return String.join(", ", parts);
    }

    private static String typeName(ElementalType type) {
        if (type == null) return "unknown";
        Component display = type.getDisplayName();
        if (display != null) {
            String s = display.getString();
            if (!s.isBlank()) return s;
        }
        return type.getName();
    }

    private static String abilityName(Pokemon p) {
        try {
            String raw = p.getAbility().getDisplayName();
            if (raw != null && !raw.isBlank()) {
                String translated = Component.translatable(raw).getString();
                if (!translated.isBlank()) return translated;
            }
            return p.getAbility().getName();
        } catch (Throwable t) {
            return "unknown";
        }
    }

    private static String heldItemName(Pokemon p) {
        ItemStack stack = p.heldItem();
        if (stack == null || stack.isEmpty()) return "none";
        return stack.getHoverName().getString();
    }
}
