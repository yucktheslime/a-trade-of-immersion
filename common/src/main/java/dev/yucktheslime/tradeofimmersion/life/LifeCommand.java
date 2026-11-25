package dev.yucktheslime.tradeofimmersion.life;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.yucktheslime.tradeofimmersion.entity.CompanionEntity;
import dev.yucktheslime.tradeofimmersion.entity.Companions;
import dev.yucktheslime.tradeofimmersion.life.kin.Kinship;
import dev.yucktheslime.tradeofimmersion.life.marriage.Procreation;
import dev.yucktheslime.tradeofimmersion.life.marriage.Romance;
import dev.yucktheslime.tradeofimmersion.life.pregnancy.Pregnancies;
import dev.yucktheslime.tradeofimmersion.life.pregnancy.PregnancyPhases;
import dev.yucktheslime.tradeofimmersion.life.relation.Bond;
import dev.yucktheslime.tradeofimmersion.life.relation.RelationshipMath;
import dev.yucktheslime.tradeofimmersion.life.relation.RelationshipStore;
import dev.yucktheslime.tradeofimmersion.life.relation.Stage;
import dev.yucktheslime.tradeofimmersion.util.McaVillagerUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code /npc romance ...}, {@code /npc kin ...}, {@code /npc pregnancy ...} and {@code /npc life reload}. Names can
 * be companions, online players or MCA villagers near you (in quotes when they have spaces). Brigadier merges these
 * into the existing {@code /npc} command.
 */
public final class LifeCommand {
    private static final DynamicCommandExceptionType NOBODY = new DynamicCommandExceptionType(n -> Component.literal("Nobody called " + n + " nearby."));
    private static final SimpleCommandExceptionType NOT_PLAYER = new SimpleCommandExceptionType(Component.literal("Run this as a player."));

    private static final SuggestionProvider<CommandSourceStack> WHO = (ctx, b) -> {
        List<String> names = new ArrayList<>();
        for (CompanionEntity c : Companions.all(ctx.getSource().getServer())) names.add(quote(c.name()));
        for (ServerPlayer p : ctx.getSource().getServer().getPlayerList().getPlayers()) names.add(p.getGameProfile().getName());
        Entity at = ctx.getSource().getEntity();
        if (at != null) for (var v : McaVillagerUtil.villagersNear(at, 32)) names.add(quote(McaVillagerUtil.name(v)));
        return SharedSuggestionProvider.suggest(names, b);
    };

    private LifeCommand() {
    }

    static String quote(String n) {
        return n.matches("[A-Za-z0-9_.+-]+") ? n : "\"" + n.replace("\"", "") + "\"";
    }

    static RequiredArgumentBuilder<CommandSourceStack, String> arg(String name) {
        return Commands.argument(name, StringArgumentType.string()).suggests(WHO);
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("npc");
        root.then(Commands.literal("life").then(Commands.literal("reload").requires(s -> s.hasPermission(2)).executes(c -> {
            LifeConfig.load();
            c.getSource().sendSuccess(() -> Component.literal("Reloaded " + LifeConfig.path().getFileName()), false);
            return 1;
        })));

        LiteralArgumentBuilder<CommandSourceStack> romance = Commands.literal("romance");
        romance.then(Commands.literal("status").executes(c -> status(c, self(c))).then(arg("who").executes(c -> status(c, who(c, "who")))));
        for (String s : List.of("date", "engage", "marry")) {
            romance.then(Commands.literal(s).then(arg("npc").executes(c -> ask(c, s))));
        }
        romance.then(Commands.literal("accept").then(arg("npc").executes(c -> {
            ServerPlayer p = player(c);
            CompanionEntity n = companion(c, "npc");
            String msg = Romance.accept(p, n);
            c.getSource().sendSuccess(() -> Component.literal(msg), false);
            return 1;
        })));
        romance.then(Commands.literal("breakup").then(arg("npc").executes(c -> {
            ServerPlayer p = player(c);
            Entity n = who(c, "npc");
            if (!Romance.stage(p.server, p.getUUID(), n.getUUID()).romantic()) {
                c.getSource().sendFailure(Component.literal("You and " + LifeMca.name(n) + " aren't together."));
                return 0;
            }
            Romance.end(p, n.getUUID(), true);
            return 1;
        })));
        romance.then(Commands.literal("procreate").then(arg("npc").executes(c -> {
            ServerPlayer p = player(c);
            CompanionEntity n = companion(c, "npc");
            String msg = Procreation.conceive(p, n);
            if (msg != null) c.getSource().sendFailure(Component.literal(msg));
            return msg == null ? 1 : 0;
        })));
        romance.then(Commands.literal("pair").requires(s -> s.hasPermission(2)).then(arg("a").then(arg("b")
                .then(Commands.literal("dating").executes(c -> pair(c, Stage.DATING)))
                .then(Commands.literal("engaged").executes(c -> pair(c, Stage.ENGAGED)))
                .then(Commands.literal("married").executes(c -> pair(c, Stage.MARRIED)))
                .then(Commands.literal("single").executes(c -> pair(c, Stage.NONE))))));
        romance.then(Commands.literal("affection").requires(s -> s.hasPermission(2)).then(arg("a").then(arg("b")
                .then(Commands.argument("value", DoubleArgumentType.doubleArg(-100, 100)).executes(c -> {
                    Entity a = who(c, "a"), b = who(c, "b");
                    Bond bd = RelationshipStore.get().bond(a.getUUID(), b.getUUID());
                    bd.affection = DoubleArgumentType.getDouble(c, "value");
                    bd.lastInteraction = LifeMca.now(c.getSource().getServer());
                    c.getSource().sendSuccess(() -> Component.literal(LifeMca.name(a) + " -> " + LifeMca.name(b) + ": affection " + Math.round(bd.affection)), false);
                    return 1;
                })))));
        root.then(romance);

        root.then(Commands.literal("kin").then(arg("a").then(arg("b").executes(c -> {
            Entity a = who(c, "a"), b = who(c, "b");
            Kinship.Relation r = LifeMca.kin(c.getSource().getServer(), a.getUUID(), b.getUUID());
            boolean no = Kinship.forbidden(r, LifeConfig.get().kinBlockDepth);
            c.getSource().sendSuccess(() -> Component.literal(LifeMca.name(a) + " and " + LifeMca.name(b) + ": " + r.describe()
                    + (r.related() ? " (" + r.upA() + "/" + r.upB() + " generations to the shared ancestor)" : "")
                    + (no ? " - can't marry" : " - may marry") + " (kinBlockDepth " + LifeConfig.get().kinBlockDepth + ")"), false);
            return 1;
        }))));

        LiteralArgumentBuilder<CommandSourceStack> preg = Commands.literal("pregnancy");
        preg.then(Commands.literal("status").executes(c -> pregStatus(c, self(c))).then(arg("who").executes(c -> pregStatus(c, who(c, "who")))));
        preg.then(Commands.literal("list").requires(s -> s.hasPermission(2)).executes(c -> {
            MinecraftServer srv = c.getSource().getServer();
            if (Pregnancies.STORE.data().active.isEmpty()) c.getSource().sendSuccess(() -> Component.literal("Nobody is pregnant."), false);
            for (Pregnancies.Rec r : Pregnancies.STORE.data().active.values()) {
                String line = Pregnancies.describe(srv, UUID.fromString(r.carrier), false);
                c.getSource().sendSuccess(() -> Component.literal(line.isEmpty() ? r.carrierName + ": " + r.state.outcome + " (waiting for them to be loaded)" : line), false);
            }
            return 1;
        }));
        preg.then(Commands.literal("start").requires(s -> s.hasPermission(2)).then(arg("carrier").executes(c -> pregStart(c, who(c, "carrier"), null))
                .then(arg("partner").executes(c -> pregStart(c, who(c, "carrier"), who(c, "partner"))))));
        preg.then(Commands.literal("advance").requires(s -> s.hasPermission(2)).then(arg("who").then(Commands.argument("days", DoubleArgumentType.doubleArg(0, 100))
                .executes(c -> {
                    Entity e = who(c, "who");
                    Pregnancies.Rec r = Pregnancies.get(e.getUUID());
                    if (r == null) {
                        c.getSource().sendFailure(Component.literal(LifeMca.name(e) + " isn't pregnant."));
                        return 0;
                    }
                    r.state.start -= Math.round(DoubleArgumentType.getDouble(c, "days") * PregnancyPhases.DAY);
                    Pregnancies.STORE.dirty();
                    c.getSource().sendSuccess(() -> Component.literal("Moved " + LifeMca.name(e) + "'s pregnancy forward."), false);
                    return 1;
                }))));
        preg.then(Commands.literal("end").requires(s -> s.hasPermission(2)).then(arg("who").executes(c -> {
            Entity e = who(c, "who");
            Pregnancies.Rec r = Pregnancies.get(e.getUUID());
            if (r == null) return 0;
            Pregnancies.lose(c.getSource().getServer(), r, e, "ended");
            c.getSource().sendSuccess(() -> Component.literal("Ended " + LifeMca.name(e) + "'s pregnancy."), false);
            return 1;
        })));
        root.then(preg);
        d.register(root);
    }

    // ---------------------------------------------------------------- helpers

    static ServerPlayer player(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        if (c.getSource().getEntity() instanceof ServerPlayer p) return p;
        throw NOT_PLAYER.create();
    }

    static Entity self(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        return player(c);
    }

    static Entity who(CommandContext<CommandSourceStack> c, String arg) throws CommandSyntaxException {
        String q = StringArgumentType.getString(c, arg);
        MinecraftServer server = c.getSource().getServer();
        ServerPlayer asker = c.getSource().getEntity() instanceof ServerPlayer p ? p : null;
        CompanionEntity comp = Companions.find(server, q, asker);
        if (comp != null) return comp;
        ServerPlayer p = server.getPlayerList().getPlayerByName(q);
        if (p != null) return p;
        Entity at = c.getSource().getEntity();
        if (at != null) {
            Optional<net.conczin.mca.entity.VillagerEntityMCA> v = McaVillagerUtil.find(server, at, q, 64);
            if (v.isPresent()) return v.get();
        }
        throw NOBODY.create(q);
    }

    static CompanionEntity companion(CommandContext<CommandSourceStack> c, String arg) throws CommandSyntaxException {
        if (who(c, arg) instanceof CompanionEntity ce) return ce;
        throw new SimpleCommandExceptionType(Component.literal("That isn't an AI companion.")).create();
    }

    static int ask(CommandContext<CommandSourceStack> c, String what) throws CommandSyntaxException {
        ServerPlayer p = player(c);
        CompanionEntity n = companion(c, "npc");
        Stage to = what.equals("date") ? Stage.DATING : what.equals("engage") ? Stage.ENGAGED : Stage.MARRIED;
        String msg = Romance.ask(p, n, to);
        c.getSource().sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    static int pair(CommandContext<CommandSourceStack> c, Stage to) throws CommandSyntaxException {
        Entity a = who(c, "a"), b = who(c, "b");
        if (!Romance.involvesCompanion(a, b)) {
            c.getSource().sendFailure(Component.literal("One of them has to be an AI companion (MCA handles the rest with its rings)."));
            return 0;
        }
        if (to == Stage.NONE) {
            Romance.end(a, b.getUUID(), true);
            return 1;
        }
        Optional<String> block = Romance.blockReason(a, b, to);
        if (block.isPresent() && !block.get().startsWith("they need to be")) {
            c.getSource().sendFailure(Component.literal(block.get()));
            return 0;
        }
        Romance.setStage(a, b, to);
        c.getSource().sendSuccess(() -> Component.literal(LifeMca.name(a) + " and " + LifeMca.name(b) + " are now " + Romance.words(to) + "."), true);
        return 1;
    }

    static int status(CommandContext<CommandSourceStack> c, Entity e) {
        MinecraftServer server = c.getSource().getServer();
        List<String> lines = new ArrayList<>();
        String fam = LifeMca.familyLine(server, e.getUUID());
        lines.add(LifeMca.name(e) + ": " + (fam.isEmpty() ? "single, no family recorded" : fam));
        long now = LifeMca.now(server);
        for (Map.Entry<String, Bond> en : RelationshipStore.get().from(e.getUUID()).entrySet()) {
            Bond b = en.getValue();
            UUID other = UUID.fromString(en.getKey());
            RelationshipMath.decay(b, now, Romance.stage(server, e.getUUID(), other).romantic(), LifeConfig.get().relationParams());
            lines.add("  -> " + LifeMca.nameOf(server, other) + ": affection " + Math.round(b.affection) + ", jealousy " + Math.round(b.jealousy)
                    + (b.jealousOf.isEmpty() ? "" : " (of " + b.jealousOf + ")") + ", " + Romance.words(Romance.stage(server, e.getUUID(), other)));
        }
        if (c.getSource().getEntity() instanceof ServerPlayer p && e != p) {
            Bond b = RelationshipStore.get().find(e.getUUID(), p.getUUID());
            if (b == null) lines.add("  " + LifeMca.name(e) + " has no feelings about you yet.");
        }
        String preg = Pregnancies.describe(server, e.getUUID(), false);
        if (!preg.isEmpty()) lines.add("  " + preg);
        for (String l : lines) c.getSource().sendSuccess(() -> Component.literal(l), false);
        return 1;
    }

    static int pregStatus(CommandContext<CommandSourceStack> c, Entity e) {
        String d = Pregnancies.describe(c.getSource().getServer(), e.getUUID(), false);
        c.getSource().sendSuccess(() -> Component.literal(d.isEmpty() ? LifeMca.name(e) + " isn't pregnant." : d), false);
        return 1;
    }

    static int pregStart(CommandContext<CommandSourceStack> c, Entity carrier, Entity partner) {
        if (!Pregnancies.phasesOn()) {
            c.getSource().sendFailure(Component.literal("pregnancyPhases is off."));
            return 0;
        }
        var r = Pregnancies.start(carrier, partner, carrier.level().getRandom().nextLong(), false);
        if (r == null) c.getSource().sendFailure(Component.literal(LifeMca.name(carrier) + " is already pregnant."));
        return r == null ? 0 : 1;
    }
}
