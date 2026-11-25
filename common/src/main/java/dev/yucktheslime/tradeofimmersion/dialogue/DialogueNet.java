package dev.yucktheslime.tradeofimmersion.dialogue;

import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import dev.yucktheslime.tradeofimmersion.TradeOfImmersion;
import dev.yucktheslime.tradeofimmersion.chatfilter.ChatFilterHooks;
import dev.yucktheslime.tradeofimmersion.config.ToiConfig;
import dev.yucktheslime.tradeofimmersion.entity.CompanionEntity;
import dev.yucktheslime.tradeofimmersion.memory.NpcMemoryStore;
import dev.yucktheslime.tradeofimmersion.stt.SttRelay;
import dev.yucktheslime.tradeofimmersion.util.McaVillagerUtil;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Network side of the NPC dialogue box (0.3.8). A client with the mod and {@code dialogueBox} on says so with
 * {@link Hello} when it joins; companion lines then reach that player as {@link Line} payloads (shown in the box)
 * instead of chat. Everyone else keeps getting chat. The box's talk field and push-to-talk send {@link Talk}.
 * MCA villager lines (ours and MCA's own) already travel in MCA's VillagerMessage; the client mixin
 * {@code McaVillagerMessageMixin} moves those into the box on the client, so the server needs nothing for them.
 */
public final class DialogueNet {
    public static final int KIND_COMPANION = 0, KIND_VILLAGER = 1, KIND_PLAYER = 2, KIND_INFO = 3;
    public static final int MAX_TALK = 256;
    private static final Set<UUID> BOX_PLAYERS = ConcurrentHashMap.newKeySet();

    /** Client -> server: this client shows NPC lines in its dialogue box (and can record speech). */
    public record Hello(boolean dialogueBox, int version) implements CustomPacketPayload {
        public static final Type<Hello> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TradeOfImmersion.MOD_ID, "dialogue_hello"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Hello> CODEC = StreamCodec.of((b, p) -> {
            b.writeBoolean(p.dialogueBox);
            b.writeVarInt(p.version);
        }, b -> new Hello(b.readBoolean(), b.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server -> client: one line for the dialogue box. */
    public record Line(int entityId, String speaker, String text, int kind, String target) implements CustomPacketPayload {
        public static final Type<Line> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TradeOfImmersion.MOD_ID, "dialogue_line"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Line> CODEC = StreamCodec.of((b, p) -> {
            b.writeVarInt(p.entityId);
            b.writeUtf(p.speaker, 64);
            b.writeUtf(p.text, 1200);
            b.writeVarInt(p.kind);
            b.writeUtf(p.target, 64);
        }, b -> new Line(b.readVarInt(), b.readUtf(64), b.readUtf(1200), b.readVarInt(), b.readUtf(64)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client -> server: the player says {@code text} to the NPC with this entity id (typed, or spoken when {@code spoken}). */
    public record Talk(int entityId, String text, boolean spoken) implements CustomPacketPayload {
        public static final Type<Talk> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TradeOfImmersion.MOD_ID, "dialogue_talk"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Talk> CODEC = StreamCodec.of((b, p) -> {
            b.writeVarInt(p.entityId);
            b.writeUtf(p.text, MAX_TALK * 4);
            b.writeBoolean(p.spoken);
        }, b -> new Talk(b.readVarInt(), b.readUtf(MAX_TALK * 4), b.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private DialogueNet() {
    }

    public static void init() {
        NetworkManager.registerReceiver(NetworkManager.c2s(), Hello.TYPE, Hello.CODEC,
                (p, ctx) -> ctx.queue(() -> { if (ctx.getPlayer() instanceof ServerPlayer sp) hello(sp, p); }));
        NetworkManager.registerReceiver(NetworkManager.c2s(), Talk.TYPE, Talk.CODEC,
                (p, ctx) -> ctx.queue(() -> { if (ctx.getPlayer() instanceof ServerPlayer sp) talk(sp, p.entityId(), p.text(), p.spoken()); }));
        if (Platform.getEnvironment() == Env.CLIENT) {
            NetworkManager.registerReceiver(NetworkManager.s2c(), Line.TYPE, Line.CODEC,
                    (p, ctx) -> ctx.queue(() -> dev.yucktheslime.tradeofimmersion.client.dialogue.DialogueClient.onLine(p)));
        } else {
            NetworkManager.registerS2CPayloadType(Line.TYPE, Line.CODEC);
        }
        SttRelay.init();
        PlayerEvent.PLAYER_QUIT.register(p -> {
            BOX_PLAYERS.remove(p.getUUID());
            SttRelay.forget(p.getUUID());
        });
    }

    private static void hello(ServerPlayer p, Hello h) {
        if (h.dialogueBox()) BOX_PLAYERS.add(p.getUUID());
        else BOX_PLAYERS.remove(p.getUUID());
        TradeOfImmersion.LOGGER.debug("{} dialogue box {}", p.getGameProfile().getName(), h.dialogueBox() ? "on" : "off");
    }

    public static boolean hasBox(ServerPlayer p) {
        return BOX_PLAYERS.contains(p.getUUID());
    }

    /** One NPC line to one player: in the dialogue box when the player's client has it, else as the chat line. */
    public static void deliver(ServerPlayer p, Entity speaker, String name, String text, int kind, String target, Component chat) {
        if (hasBox(p)) NetworkManager.sendToPlayer(p, new Line(speaker == null ? -1 : speaker.getId(), cut(name, 64), cut(text, 1200), kind, cut(target, 64)));
        else if (chat != null) p.sendSystemMessage(chat);
    }

    private static String cut(String s, int n) {
        if (s == null) return "";
        return s.length() > n ? s.substring(0, n) : s;
    }

    /** A line from the dialogue box (typed or transcribed) to an NPC. Server thread. */
    public static void talk(ServerPlayer p, int entityId, String raw, boolean spoken) {
        ToiConfig cfg = ToiConfig.get();
        String text = raw == null ? "" : raw.replaceAll("\\s+", " ").trim();
        if (text.length() > MAX_TALK) text = text.substring(0, MAX_TALK);
        if (text.isEmpty() || !cfg.enabled) return;
        if (text.startsWith("/")) {
            p.displayClientMessage(Component.literal("Commands go in the normal chat (T)."), true);
            return;
        }
        Entity e = p.level().getEntity(entityId);
        if (e == null || !e.isAlive() || e.distanceToSqr(p) > 24 * 24) {
            info(p, "Nobody to talk to: look at an NPC, or get closer.");
            return;
        }
        String who = p.getGameProfile().getName();
        String target = e instanceof VillagerEntityMCA v ? McaVillagerUtil.name(v) : e.getName().getString();
        echo(p, e, who, target, text);
        if (!(e instanceof CompanionEntity) && !(e instanceof VillagerEntityMCA)) {
            info(p, target + " can't talk.");
            return;
        }
        ChatFilterHooks.handleDialogueTalk(p, e, text);
        // other companions close by overhear it, like normal chat
        for (CompanionEntity c : p.level().getEntitiesOfClass(CompanionEntity.class, p.getBoundingBox().inflate(cfg.chatHearingRadius), c -> c != e && c.isAlive())) {
            NpcMemoryStore.get().event(c.getUUID(), "heard", who + " said to " + target + ": " + text);
        }
    }

    /** The player's line, shown to players near them (box or grey chat), like they had said it out loud. */
    private static void echo(ServerPlayer from, Entity to, String who, String target, String text) {
        if (!(from.level() instanceof ServerLevel sl)) return;
        double r = Math.max(1, ToiConfig.get().npcChatRadius);
        Component chat = Component.literal("<" + who + " to " + target + "> " + text).withStyle(s -> s.withColor(0xAAAAAA));
        for (ServerPlayer p : sl.players()) {
            if (p == from || p.distanceToSqr(from) < r * r) deliver(p, from, who, text, KIND_PLAYER, target, chat);
        }
    }

    /** A short note (not an NPC line): in the box when the player has it, else above the hotbar. */
    public static void info(ServerPlayer p, String text) {
        if (hasBox(p)) NetworkManager.sendToPlayer(p, new Line(-1, "", cut(text, 1200), KIND_INFO, ""));
        else p.displayClientMessage(Component.literal(text), true);
    }
}
