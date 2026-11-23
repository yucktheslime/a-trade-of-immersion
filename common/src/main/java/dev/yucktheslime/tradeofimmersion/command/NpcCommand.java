package dev.yucktheslime.tradeofimmersion.command;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.yucktheslime.tradeofimmersion.action.ActionExecutor;
import dev.yucktheslime.tradeofimmersion.ai.CompanionBrain;
import dev.yucktheslime.tradeofimmersion.ai.KagrenacBackend;
import dev.yucktheslime.tradeofimmersion.config.ToiConfig;
import dev.yucktheslime.tradeofimmersion.entity.CompanionEntity;
import dev.yucktheslime.tradeofimmersion.entity.Companions;
import dev.yucktheslime.tradeofimmersion.memory.NpcMemoryStore;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * {@code /npc ...}: spawn and manage companions. Spawning, {@code do} and {@code dismiss} of others' companions
 * need permission level 2; everything else works for the companion's owner (or an operator).
 */
public final class NpcCommand {
    private static final SimpleCommandExceptionType NOT_FOUND = new SimpleCommandExceptionType(Component.literal("No loaded companion with that name."));
    private static final SimpleCommandExceptionType NOT_YOURS = new SimpleCommandExceptionType(Component.literal("That companion isn't yours."));

    private static final SuggestionProvider<CommandSourceStack> NAMES = (ctx, b) -> {
        List<String> names = new ArrayList<>();
        for (CompanionEntity c : Companions.all(ctx.getSource().getServer())) {
            String n = c.name();
            names.add(n.matches("[A-Za-z0-9_.+-]+") ? n : "\"" + n.replace("\"", "") + "\"");
        }
        return SharedSuggestionProvider.suggest(names, b);
    };

    private NpcCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("npc");
        root.then(Commands.literal("spawn").requires(s -> s.hasPermission(2))
                .executes(c -> spawn(c, null, null, null))
                .then(Commands.argument("name", StringArgumentType.string())
                        .executes(c -> spawn(c, StringArgumentType.getString(c, "name"), null, null))
                        .then(Commands.argument("gender", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("male", "female", "neutral"), b))
                                .executes(c -> spawn(c, StringArgumentType.getString(c, "name"), StringArgumentType.getString(c, "gender"), null))
                                .then(Commands.argument("skin", StringArgumentType.greedyString())
                                        .executes(c -> spawn(c, StringArgumentType.getString(c, "name"), StringArgumentType.getString(c, "gender"),
                                                StringArgumentType.getString(c, "skin")))))));
        root.then(Commands.literal("list").executes(NpcCommand::list));
        root.then(Commands.literal("ai").then(Commands.literal("status")
                        .requires(s -> s.hasPermission(2) || ToiConfig.get().statusForEveryone).executes(NpcCommand::aiStatus))
                .then(Commands.literal("reload").requires(s -> s.hasPermission(2)).executes(c -> {
                    ToiConfig.load();
                    CompanionBrain.reload();
                    c.getSource().sendSuccess(() -> Component.literal("Reloaded config. " + CompanionBrain.status()), dev.yucktheslime.tradeofimmersion.config.ToiConfig.get().chatOpFeedback);
                    return 1;
                }))
                .then(Commands.literal("preset").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("preset", StringArgumentType.word())
                                .suggests((ctx, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(List.of("kagrenac", "openrouter", "ollama", "mock"), b))
                                .executes(NpcCommand::preset)))
                .then(Commands.literal("cancel").then(npc().executes(c -> {
                    CompanionEntity n = owned(c);
                    boolean was = CompanionBrain.cancel(n);
                    c.getSource().sendSuccess(() -> Component.literal(was ? "Cancelled " + n.name() + "'s turn." : n.name() + " was not thinking about anything."), false);
                    return 1;
                })))
                .then(Commands.literal("bios").requires(s -> s.hasPermission(2)).executes(c -> {
                    if (!(CompanionBrain.backend() instanceof KagrenacBackend k)) {
                        c.getSource().sendFailure(Component.literal("Bios are pushed only with aiBackend \"kagrenac\"."));
                        return 0;
                    }
                    if (!k.has("bio_sync")) {
                        c.getSource().sendFailure(Component.literal("This Kagrenac server has no npc_bio.php (install the kagrenac-server/ folder of this mod)."));
                        return 0;
                    }
                    dev.yucktheslime.tradeofimmersion.ai.KagrenacWorld.reset();
                    int n = dev.yucktheslime.tradeofimmersion.ai.KagrenacWorld.syncBios(c.getSource().getServer(), k);
                    c.getSource().sendSuccess(() -> Component.literal("Sending " + n + " bio(s) to Kagrenac (NPC bios page on its dashboard)."), false);
                    return n;
                })));
        root.then(Commands.literal("say").then(npc().then(Commands.argument("text", StringArgumentType.greedyString()).executes(c -> {
            CompanionEntity n = owned(c);
            String speaker = c.getSource().getEntity() instanceof ServerPlayer p ? p.getGameProfile().getName() : "Narrator";
            CompanionBrain.onChat(n, speaker, StringArgumentType.getString(c, "text"));
            return 1;
        }))));
        root.then(Commands.literal("skin").then(npc().then(Commands.argument("skin", StringArgumentType.greedyString()).executes(c -> {
            CompanionEntity n = owned(c);
            String s = StringArgumentType.getString(c, "skin").trim();
            if (s.equalsIgnoreCase("slim") || s.equalsIgnoreCase("wide")) n.setSlim(s.equalsIgnoreCase("slim"));
            else n.setSkin(s.equalsIgnoreCase("default") ? "" : s);
            c.getSource().sendSuccess(() -> Component.literal(n.name() + " skin: " + (n.getSkin().isEmpty() ? "default" : n.getSkin()) + (n.isSlim() ? " (slim arms)" : "")), false);
            return 1;
        }))));
        root.then(Commands.literal("gender").then(npc().then(Commands.argument("gender", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("male", "female", "neutral"), b)).executes(c -> {
                    CompanionEntity n = owned(c);
                    String g = StringArgumentType.getString(c, "gender").toLowerCase();
                    if (!List.of("male", "female", "neutral").contains(g)) {
                        c.getSource().sendFailure(Component.literal("male, female or neutral"));
                        return 0;
                    }
                    n.setGender(g);
                    c.getSource().sendSuccess(() -> Component.literal(n.name() + " is now " + g), false);
                    return 1;
                }))));
        root.then(Commands.literal("persona").then(npc().then(Commands.argument("text", StringArgumentType.greedyString()).executes(c -> {
            CompanionEntity n = owned(c);
            n.setPersona(StringArgumentType.getString(c, "text"));
            c.getSource().sendSuccess(() -> Component.literal(n.name() + "'s personality: " + n.getPersona()), false);
            return 1;
        }))));
        root.then(Commands.literal("follow").then(npc().executes(c -> mode(c, "follow"))));
        root.then(Commands.literal("stay").then(npc().executes(c -> mode(c, "stay"))));
        root.then(Commands.literal("wander").then(npc().executes(c -> mode(c, "wander"))));
        root.then(Commands.literal("inventory").then(npc().executes(c -> {
            CompanionEntity n = owned(c);
            n.openInventory(c.getSource().getPlayerOrException());
            return 1;
        })));
        root.then(Commands.literal("memory").then(npc().executes(c -> {
            CompanionEntity n = owned(c);
            var ev = NpcMemoryStore.get().recentEvents(n.getUUID(), 10);
            c.getSource().sendSuccess(() -> Component.literal(n.name() + " remembers:\n" + (ev.isEmpty() ? "(nothing yet)"
                    : ev.stream().map(e -> "- [" + e.who() + "] " + e.text()).collect(Collectors.joining("\n")))), false);
            return 1;
        })));
        root.then(Commands.literal("do").requires(s -> s.hasPermission(2)).then(npc().then(Commands.argument("json", StringArgumentType.greedyString()).executes(c -> {
            CompanionEntity n = find(c);
            List<JsonObject> actions = new ArrayList<>();
            try {
                JsonElement e = JsonParser.parseString(StringArgumentType.getString(c, "json"));
                if (e instanceof JsonArray a) for (JsonElement x : a) actions.add(x.getAsJsonObject());
                else actions.add(e.getAsJsonObject());
            } catch (Exception ex) {
                c.getSource().sendFailure(Component.literal("Bad JSON: " + ex.getMessage()));
                return 0;
            }
            var res = ActionExecutor.run(n, actions);
            c.getSource().sendSuccess(() -> Component.literal(res.stream().map(Object::toString).collect(Collectors.joining("\n"))), false);
            return res.size();
        }))));
        root.then(Commands.literal("dismiss").then(npc().executes(c -> {
            CompanionEntity n = owned(c);
            for (int i = 0; i < n.inventory.getContainerSize(); i++) {
                if (!n.inventory.getItem(i).isEmpty()) n.spawnAtLocation(n.inventory.removeItemNoUpdate(i));
            }
            for (var slot : net.minecraft.world.entity.EquipmentSlot.values()) {
                if (!n.getItemBySlot(slot).isEmpty()) {
                    n.spawnAtLocation(n.getItemBySlot(slot));
                    n.setItemSlot(slot, net.minecraft.world.item.ItemStack.EMPTY);
                }
            }
            n.say("Goodbye!");
            n.discard();
            c.getSource().sendSuccess(() -> Component.literal(n.name() + " left (items dropped)."), dev.yucktheslime.tradeofimmersion.config.ToiConfig.get().chatOpFeedback);
            return 1;
        })));
        d.register(root);
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> npc() {
        return Commands.argument("npc", StringArgumentType.string()).suggests(NAMES);
    }

    private static CompanionEntity find(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        CompanionEntity n = Companions.find(c.getSource().getServer(), StringArgumentType.getString(c, "npc"),
                c.getSource().getEntity() instanceof ServerPlayer p ? p : null);
        if (n == null) throw NOT_FOUND.create();
        return n;
    }

    private static CompanionEntity owned(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        CompanionEntity n = find(c);
        if (c.getSource().hasPermission(2)) return n;
        if (c.getSource().getEntity() instanceof ServerPlayer p && n.isOwner(p)) return n;
        throw NOT_YOURS.create();
    }

    private static int mode(CommandContext<CommandSourceStack> c, String mode) throws CommandSyntaxException {
        CompanionEntity n = owned(c);
        n.setTask(null);
        n.setMode(mode);
        c.getSource().sendSuccess(() -> Component.literal(n.name() + ": " + mode), false);
        return 1;
    }

    private static int spawn(CommandContext<CommandSourceStack> c, String name, String gender, String skin) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getEntity() instanceof ServerPlayer sp ? sp : null;
        CompanionEntity n = Companions.spawn(c.getSource().getLevel(), c.getSource().getPosition(), p, name, gender, skin);
        n.say("Hello" + (p != null ? " " + p.getGameProfile().getName() : "") + "! I'm " + n.name() + ".");
        c.getSource().sendSuccess(() -> Component.literal("Spawned " + n.name() + " (" + n.getGender() + ")" + (p != null ? ", owned by " + p.getGameProfile().getName() : "")
                + ". Talk in chat near them, shift-right-click for their inventory."), dev.yucktheslime.tradeofimmersion.config.ToiConfig.get().chatOpFeedback);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        var all = Companions.all(c.getSource().getServer());
        String s = all.isEmpty() ? "No companions loaded." : all.stream().map(n -> n.name() + " (" + n.getOwnerName() + ", " + n.getMode()
                + ", " + Math.round(n.getHealth()) + " hp" + (n.isPassenger() ? ", riding " + n.getVehicle().getName().getString() : "")
                + (n.getTask() != null ? ", " + n.getTask().describe() : "") + ") at " + n.blockPosition().toShortString()).collect(Collectors.joining("\n"));
        c.getSource().sendSuccess(() -> Component.literal(s), false);
        return all.size();
    }

    private static int aiStatus(CommandContext<CommandSourceStack> c) {
        c.getSource().sendSuccess(() -> Component.literal(CompanionBrain.status()), false);
        if (CompanionBrain.backend() instanceof KagrenacBackend k) {
            var srv = c.getSource().getServer();
            String token = ToiConfig.get().effectiveKagrenacToken().isBlank() ? "NOT SET (kagrenacToken or env TOI_KAGRENAC_TOKEN)" : "set";
            k.refreshHealth().whenComplete((info, err) -> srv.execute(() -> c.getSource().sendSuccess(() ->
                    Component.literal("Kagrenac " + KagrenacBackend.safeUrl(ToiConfig.get().kagrenacBaseUrl) + (KagrenacBackend.remote() ? " (remote)" : "") + ": " + info + "; token " + token
                            + "; session " + KagrenacBackend.session() + "; voice " + (ToiConfig.get().voiceEnabled ? "on (" + dev.yucktheslime.tradeofimmersion.voice.VoiceSync.status() + ")" : "off")), false)));
        }
        return 1;
    }

    /** Switches the AI preset and saves the config: kagrenac (CHIM-style server), openrouter, ollama or mock. */
    private static int preset(CommandContext<CommandSourceStack> c) {
        String p = StringArgumentType.getString(c, "preset").toLowerCase(java.util.Locale.ROOT);
        ToiConfig cfg = ToiConfig.get();
        switch (p) {
            case "kagrenac" -> cfg.aiBackend = "kagrenac";
            case "openrouter", "ollama" -> {
                cfg.aiBackend = "openai";
                cfg.aiPreset = p;
                cfg.aiBaseUrl = "";
                cfg.aiModel = "";
            }
            case "mock" -> cfg.aiBackend = "mock";
            default -> {
                c.getSource().sendFailure(Component.literal("Presets: kagrenac, openrouter, ollama, mock"));
                return 0;
            }
        }
        ToiConfig.save();
        CompanionBrain.reload();
        c.getSource().sendSuccess(() -> Component.literal("AI preset " + p + " saved. " + CompanionBrain.status()
                + (p.equals("kagrenac") ? "\nRun /npc ai status to check the server." : "")), dev.yucktheslime.tradeofimmersion.config.ToiConfig.get().chatOpFeedback);
        return 1;
    }
}
