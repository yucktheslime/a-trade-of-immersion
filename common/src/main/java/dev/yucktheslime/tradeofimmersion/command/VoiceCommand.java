package dev.yucktheslime.tradeofimmersion.command;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.yucktheslime.tradeofimmersion.config.ToiConfig;
import dev.yucktheslime.tradeofimmersion.entity.CompanionEntity;
import dev.yucktheslime.tradeofimmersion.entity.Companions;
import dev.yucktheslime.tradeofimmersion.voice.VoiceProfiles;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * {@code /npc voice ...}: per-NPC voice profiles on the Kagrenac server, for companions and MCA villagers.
 * <ul>
 *   <li>{@code /npc voice list}: PocketTTS speakers and the NPCs' voices</li>
 *   <li>{@code /npc voice show [npc]}</li>
 *   <li>{@code /npc voice set <speaker> [npc]}, {@code /npc voice auto [npc]}</li>
 *   <li>{@code /npc voice pitch <0.5-2.0> [npc]}, {@code /npc voice language <code|default> [npc]}</li>
 * </ul>
 * Without {@code npc} the NPC you are looking at is used. {@code npc} is a name (nearby companion or villager, or any
 * NPC the server knows) or an id (c-/v-uuid). Companions: their owner or an operator; villagers: operators, or
 * everyone in single player or with villagerVoiceForEveryone.
 */
public final class VoiceCommand {
    private static final SuggestionProvider<CommandSourceStack> SPEAKERS = (ctx, b) -> {
        if (VoiceProfiles.knownSpeakers.isEmpty()) VoiceProfiles.list(); // fills the cache for the next keypress
        return SharedSuggestionProvider.suggest(VoiceProfiles.knownSpeakers, b);
    };
    private static final List<String> LANGUAGES = List.of("default", "en", "es", "fr", "de", "it", "pt", "pl", "tr", "ru", "nl", "cs", "ar", "zh-cn", "ja", "hu", "ko", "hi");

    private VoiceCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> voice = Commands.literal("voice");
        voice.then(Commands.literal("list").executes(VoiceCommand::list));
        voice.then(withTarget(Commands.literal("show"), (c, t) -> run(c, VoiceProfiles.get(t.key), o -> VoiceProfiles.describe(o.getAsJsonObject("npc")))));
        voice.then(Commands.literal("set").then(withTarget(Commands.argument("speaker", StringArgumentType.word()).suggests(SPEAKERS), (c, t) -> {
            if (!allowed(c, t)) return 0;
            return run(c, VoiceProfiles.set(t.key, t.name, StringArgumentType.getString(c, "speaker"), null, null),
                    o -> "Voice saved. " + VoiceProfiles.describe(o.getAsJsonObject("npc")));
        })));
        voice.then(withTarget(Commands.literal("auto"), (c, t) -> {
            if (!allowed(c, t)) return 0;
            return run(c, VoiceProfiles.set(t.key, t.name, "", null, null), o -> "Back to an automatic voice. " + VoiceProfiles.describe(o.getAsJsonObject("npc")));
        }));
        voice.then(Commands.literal("pitch").then(withTarget(Commands.argument("pitch", DoubleArgumentType.doubleArg(0.5, 2.0)), (c, t) -> {
            if (!allowed(c, t)) return 0;
            return run(c, VoiceProfiles.set(t.key, t.name, null, null, DoubleArgumentType.getDouble(c, "pitch")),
                    o -> "Pitch saved. " + VoiceProfiles.describe(o.getAsJsonObject("npc")));
        })));
        voice.then(Commands.literal("language").then(withTarget(Commands.argument("language", StringArgumentType.word())
                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(LANGUAGES, b)), (c, t) -> {
            if (!allowed(c, t)) return 0;
            String lang = StringArgumentType.getString(c, "language").toLowerCase(Locale.ROOT);
            return run(c, VoiceProfiles.set(t.key, t.name, null, lang.equals("default") ? "" : lang, null),
                    o -> "Language saved. " + VoiceProfiles.describe(o.getAsJsonObject("npc")));
        })));
        return voice;
    }

    /** Who the command is about: {@code key} is sent to the server (id, or a name for it to look up). */
    record Target(String key, String name, Entity entity) {
    }

    interface Action {
        int run(CommandContext<CommandSourceStack> c, Target t);
    }

    private static <T extends com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, T>> T withTarget(T node, Action action) {
        node.executes(c -> {
            Target t = target(c, null);
            return t == null ? 0 : action.run(c, t);
        });
        node.then(Commands.argument("npc", StringArgumentType.greedyString()).suggests(NAMES).executes(c -> {
            Target t = target(c, StringArgumentType.getString(c, "npc").trim());
            return t == null ? 0 : action.run(c, t);
        }));
        return node;
    }

    private static final SuggestionProvider<CommandSourceStack> NAMES = (ctx, b) -> {
        List<String> names = new ArrayList<>();
        for (CompanionEntity c : Companions.all(ctx.getSource().getServer())) names.add(c.name());
        if (ctx.getSource().getEntity() instanceof ServerPlayer p) {
            for (VillagerEntityMCA v : p.level().getEntitiesOfClass(VillagerEntityMCA.class, p.getBoundingBox().inflate(32))) names.add(v.getName().getString());
        }
        return SharedSuggestionProvider.suggest(names, b);
    };

    private static Target target(CommandContext<CommandSourceStack> c, String npc) {
        CommandSourceStack src = c.getSource();
        ServerPlayer p = src.getEntity() instanceof ServerPlayer sp ? sp : null;
        if (npc == null || npc.isEmpty()) {
            Entity e = p == null ? null : lookedAt(p, 8);
            if (e == null) {
                src.sendFailure(Component.literal("Look at a companion or villager, or name one: /npc voice ... <name>"));
                return null;
            }
            return new Target(VoiceProfiles.npcId(e), e.getName().getString(), e);
        }
        CompanionEntity comp = Companions.find(src.getServer(), npc, p);
        if (comp != null) return new Target(VoiceProfiles.npcId(comp), comp.name(), comp);
        if (p != null) {
            VillagerEntityMCA best = null;
            for (VillagerEntityMCA v : p.level().getEntitiesOfClass(VillagerEntityMCA.class, p.getBoundingBox().inflate(64),
                    v -> v.getName().getString().equalsIgnoreCase(npc))) {
                if (best == null || v.distanceToSqr(p) < best.distanceToSqr(p)) best = v;
            }
            if (best != null) return new Target(VoiceProfiles.npcId(best), best.getName().getString(), best);
        }
        return new Target(npc, null, null); // let the server find it by name or id
    }

    /** The companion or MCA villager the player's crosshair is on, within {@code range} blocks. */
    public static Entity lookedAt(ServerPlayer p, double range) {
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getViewVector(1f).scale(range));
        Entity best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, new AABB(eye, end).inflate(1),
                e -> e instanceof CompanionEntity || e instanceof VillagerEntityMCA)) {
            var hit = e.getBoundingBox().inflate(0.3).clip(eye, end);
            if (hit.isPresent() && eye.distanceToSqr(hit.get()) < bestD) {
                bestD = eye.distanceToSqr(hit.get());
                best = e;
            }
        }
        return best;
    }

    static boolean mayChange(ServerPlayer p, Entity e, MinecraftServer server) {
        if (p == null || p.hasPermissions(2) || server.isSingleplayer()) return true;
        if (e instanceof CompanionEntity c) return c.isOwner(p);
        return ToiConfig.get().villagerVoiceForEveryone;
    }

    private static boolean allowed(CommandContext<CommandSourceStack> c, Target t) {
        ServerPlayer p = c.getSource().getEntity() instanceof ServerPlayer sp ? sp : null;
        boolean ok = t.entity != null ? mayChange(p, t.entity, c.getSource().getServer())
                : p == null || p.hasPermissions(2) || c.getSource().getServer().isSingleplayer();
        if (!ok) c.getSource().sendFailure(Component.literal(t.entity instanceof CompanionEntity
                ? "Only the companion's owner (or an operator) can change its voice." : "Only operators can change that NPC's voice."));
        return ok;
    }

    private static int run(CommandContext<CommandSourceStack> c, CompletableFuture<JsonObject> f, Function<JsonObject, String> ok) {
        CommandSourceStack src = c.getSource();
        MinecraftServer server = src.getServer();
        f.whenComplete((o, err) -> server.execute(() -> {
            if (err != null) {
                Throwable t = err.getCause() != null ? err.getCause() : err;
                src.sendFailure(Component.literal("Voice: " + (t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName())));
            } else if (!VoiceProfiles.ok(o)) {
                String msg = VoiceProfiles.error(o);
                if (o.has("speakers")) msg += " Speakers: " + String.join(", ", VoiceProfiles.strings(o.getAsJsonArray("speakers")));
                src.sendFailure(Component.literal("Voice: " + msg));
            } else {
                String text = ok.apply(o);
                src.sendSuccess(() -> Component.literal(text), false);
            }
        }));
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        return run(c, VoiceProfiles.list(), o -> {
            StringBuilder sb = new StringBuilder();
            List<String> speakers = VoiceProfiles.strings(o.getAsJsonArray("speakers"));
            sb.append(o.has("tts_found") && o.get("tts_found").getAsBoolean()
                    ? "PocketTTS speakers (" + speakers.size() + "): " + String.join(", ", speakers)
                    : "PocketTTS did not answer (start it in the DwemerDistro launcher).");
            int n = 0;
            for (JsonElement e : o.getAsJsonArray("npcs")) {
                if (n++ == 15) {
                    sb.append("\n... more on the dashboard's NPC voices page");
                    break;
                }
                sb.append("\n- ").append(VoiceProfiles.describe(e.getAsJsonObject()));
            }
            if (n == 0) sb.append("\nNo NPC has spoken yet; each gets its own voice on its first line.");
            sb.append("\nChange one: /npc voice set <speaker> [name]  (or look at the NPC)");
            return sb.toString();
        });
    }
}
