package dev.yucktheslime.tradeofimmersion.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.yucktheslime.tradeofimmersion.body.CompanionBodyData;
import dev.yucktheslime.tradeofimmersion.body.CompanionBodyStore;
import dev.yucktheslime.tradeofimmersion.body.CompanionBodySync;
import dev.yucktheslime.tradeofimmersion.entity.CompanionEntity;
import dev.yucktheslime.tradeofimmersion.entity.Companions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * {@code /toi gender <companion> <male|female|neutral|auto>}
 * {@code /toi body <companion> <gene> <0.0-1.0>} | {@code reset} | (no args = show).
 * Permission level 2. Saved per companion UUID in world data and synced to clients.
 */
public final class CompanionBodyCommand {
    /** Command token -> MCA Reborn {@code Genetics.GeneType} name. */
    public static final Map<String, String> GENES = new LinkedHashMap<>();

    static {
        GENES.put("breast", "Breast");
        GENES.put("size", "Size");
        GENES.put("height", "Size");
        GENES.put("width", "Width");
        GENES.put("weight", "Width");
        GENES.put("melanin", "Melanin");
        GENES.put("skin_tone", "Melanin");
        GENES.put("hemoglobin", "Hemoglobin");
        GENES.put("eumelanin", "Eumelanin");
        GENES.put("pheomelanin", "Pheomelanin");
        GENES.put("skin", "Skin");
        GENES.put("face", "Face");
        GENES.put("eye_brightness", "EyeBrightness");
    }

    /** Extra genes added by "MCA: Female Gender Physics" (mod id mcabp); names verified in its MCAVillagerGenes. */
    public static final Map<String, String> ADDON_GENES = new LinkedHashMap<>();

    static {
        ADDON_GENES.put("separation", "BreastSeparation");
        ADDON_GENES.put("breast_height", "BreastHeight");
        ADDON_GENES.put("depth", "BreastDepth");
        ADDON_GENES.put("rotation", "BreastRotation");
        ADDON_GENES.put("growth", "BreastGrowth");
    }

    private static Map<String, String> availableGenes() {
        if (!dev.architectury.platform.Platform.isModLoaded("mcabp")) return GENES;
        Map<String, String> all = new LinkedHashMap<>(GENES);
        all.putAll(ADDON_GENES);
        return all;
    }

    private static final SimpleCommandExceptionType NO_COMPANION =
            new SimpleCommandExceptionType(Component.literal("No loaded companion with that name or UUID."));

    private static final SuggestionProvider<CommandSourceStack> COMPANIONS = (ctx, b) -> {
        List<String> names = new ArrayList<>();
        for (CompanionEntity c : Companions.all(ctx.getSource().getServer())) {
            String n = c.name();
            names.add(n.matches("[A-Za-z0-9_.+-]+") ? n : "\"" + n.replace("\"", "") + "\"");
        }
        return SharedSuggestionProvider.suggest(names, b);
    };

    private CompanionBodyCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var companion = Commands.argument("companion", StringArgumentType.string()).suggests(COMPANIONS);
        dispatcher.register(Commands.literal("toi").requires(s -> s.hasPermission(2))
                .then(Commands.literal("gender").then(Commands.argument("companion", StringArgumentType.string()).suggests(COMPANIONS)
                        .then(Commands.argument("gender", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("male", "female", "neutral", "auto"), b))
                                .executes(CompanionBodyCommand::gender))))
                .then(Commands.literal("body").then(companion
                        .executes(CompanionBodyCommand::show)
                        .then(Commands.literal("reset").executes(CompanionBodyCommand::reset))
                        .then(Commands.argument("gene", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(availableGenes().keySet(), b))
                                .then(Commands.argument("value", FloatArgumentType.floatArg(0f, 1f))
                                        .executes(CompanionBodyCommand::setGene))))));
    }

    private record Target(UUID id, String name) {
    }

    private static Target resolve(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String arg = StringArgumentType.getString(ctx, "companion");
        CompanionEntity c = Companions.find(ctx.getSource().getServer(), arg, null);
        if (c != null) return new Target(c.getUUID(), c.name());
        throw NO_COMPANION.create();
    }

    private static void commit(CommandContext<CommandSourceStack> ctx) {
        CompanionBodyStore.get().save();
        CompanionBodySync.broadcast(ctx.getSource().getServer());
    }

    private static int gender(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Target t = resolve(ctx);
        String g = StringArgumentType.getString(ctx, "gender").toLowerCase(Locale.ROOT);
        if (!List.of("male", "female", "neutral", "auto").contains(g)) {
            ctx.getSource().sendFailure(Component.literal("Gender must be male, female, neutral or auto."));
            return 0;
        }
        CompanionBodyData d = CompanionBodyStore.get().getOrCreate(t.id());
        d.name = t.name();
        d.gender = g.equals("auto") ? null : g;
        CompanionEntity ce = Companions.find(ctx.getSource().getServer(), t.id().toString(), null);
        if (ce != null && !g.equals("auto")) ce.setGender(g);
        commit(ctx);
        ctx.getSource().sendSuccess(() -> Component.literal(t.name() + " gender: " + g), true);
        return 1;
    }

    private static int setGene(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Target t = resolve(ctx);
        String token = StringArgumentType.getString(ctx, "gene").toLowerCase(Locale.ROOT);
        String gene = availableGenes().get(token);
        if (gene == null) {
            String hint = ADDON_GENES.containsKey(token) ? " (needs MCA: Female Gender Physics)" : "";
            ctx.getSource().sendFailure(Component.literal("Unknown trait '" + token + "'" + hint + ". Use one of: "
                    + String.join(", ", availableGenes().keySet())));
            return 0;
        }
        float v = FloatArgumentType.getFloat(ctx, "value");
        CompanionBodyData d = CompanionBodyStore.get().getOrCreate(t.id());
        d.name = t.name();
        d.genes.put(gene, v);
        commit(ctx);
        ctx.getSource().sendSuccess(() -> Component.literal(t.name() + " " + gene + " = " + v), true);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Target t = resolve(ctx);
        CompanionBodyData d = CompanionBodyStore.get().find(t.id());
        if (d != null) d.genes.clear();
        commit(ctx);
        ctx.getSource().sendSuccess(() -> Component.literal(t.name() + " body traits reset (gender kept; use '/toi gender "
                + t.name() + " auto' to clear it)"), true);
        return 1;
    }

    private static int show(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Target t = resolve(ctx);
        CompanionBodyData d = CompanionBodyStore.get().find(t.id());
        String s = d == null || d.isEmpty() ? "no overrides" : "gender=" + (d.gender == null ? "auto" : d.gender) + " genes=" + d.genes;
        ctx.getSource().sendSuccess(() -> Component.literal(t.name() + ": " + s), false);
        return 1;
    }
}
