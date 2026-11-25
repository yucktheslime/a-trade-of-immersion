package dev.yucktheslime.tradeofimmersion.skills.item;

import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import dev.yucktheslime.tradeofimmersion.TradeOfImmersion;
import dev.yucktheslime.tradeofimmersion.entity.CompanionEntity;
import dev.yucktheslime.tradeofimmersion.skills.SkillsConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Network side of item learning. The server asks an owner's client for an item's full tooltip
 * ({@link TooltipRequest} / {@link TooltipReport}); a client tells the server when its player presses a mod key while
 * holding an item ({@link KeyObserved}), and the server matches that to the mod packet that followed.
 */
public final class SkillNet {
    public static final int MAX_LINES = 24, MAX_LINE = 256;

    /** Server -> client: send me this item's tooltip. */
    public record TooltipRequest(int id, ItemStack stack) implements CustomPacketPayload {
        public static final Type<TooltipRequest> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TradeOfImmersion.MOD_ID, "skills_tooltip_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TooltipRequest> CODEC = StreamCodec.of((b, p) -> {
            b.writeVarInt(p.id);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(b, p.stack);
        }, b -> new TooltipRequest(b.readVarInt(), ItemStack.OPTIONAL_STREAM_CODEC.decode(b)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client -> server: the tooltip lines of the requested item (line 0, the name, left out). */
    public record TooltipReport(int id, String itemId, List<String> lines) implements CustomPacketPayload {
        public static final Type<TooltipReport> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TradeOfImmersion.MOD_ID, "skills_tooltip_report"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TooltipReport> CODEC = StreamCodec.of((b, p) -> {
            b.writeVarInt(p.id);
            b.writeUtf(p.itemId, 128);
            int n = Math.min(MAX_LINES, p.lines.size());
            b.writeVarInt(n);
            for (int i = 0; i < n; i++) b.writeUtf(cut(p.lines.get(i)), MAX_LINE * 4);
        }, b -> {
            int id = b.readVarInt();
            String item = b.readUtf(128);
            int n = Math.min(MAX_LINES, b.readVarInt());
            List<String> l = new ArrayList<>();
            for (int i = 0; i < n; i++) l.add(b.readUtf(MAX_LINE * 4));
            return new TooltipReport(id, item, l);
        });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client -> server: a mod key mapping was just pressed while holding {@code itemId}. */
    public record KeyObserved(String mapping, String label, String bound, String itemId) implements CustomPacketPayload {
        public static final Type<KeyObserved> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TradeOfImmersion.MOD_ID, "skills_key_observed"));
        public static final StreamCodec<RegistryFriendlyByteBuf, KeyObserved> CODEC = StreamCodec.of((b, p) -> {
            b.writeUtf(p.mapping, 128);
            b.writeUtf(p.label, 128);
            b.writeUtf(p.bound, 64);
            b.writeUtf(p.itemId, 128);
        }, b -> new KeyObserved(b.readUtf(128), b.readUtf(128), b.readUtf(64), b.readUtf(128)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private record Pending(UUID player, long pressMs, KeyObserved key) {
    }

    private static final List<Pending> PENDING = new CopyOnWriteArrayList<>();
    private static final Map<Integer, String> REQUESTS = new ConcurrentHashMap<>();
    private static int nextId = 1;

    private SkillNet() {
    }

    public static void init() {
        NetworkManager.registerReceiver(NetworkManager.c2s(), TooltipReport.TYPE, TooltipReport.CODEC,
                (p, ctx) -> ctx.queue(() -> { if (ctx.getPlayer() instanceof ServerPlayer sp) onTooltip(sp, p); }));
        NetworkManager.registerReceiver(NetworkManager.c2s(), KeyObserved.TYPE, KeyObserved.CODEC, (p, ctx) -> {
            long now = System.currentTimeMillis();
            ctx.queue(() -> { if (ctx.getPlayer() instanceof ServerPlayer sp) onKey(sp, p, now); });
        });
        if (Platform.getEnvironment() == Env.CLIENT) {
            NetworkManager.registerReceiver(NetworkManager.s2c(), TooltipRequest.TYPE, TooltipRequest.CODEC,
                    (p, ctx) -> ctx.queue(() -> dev.yucktheslime.tradeofimmersion.skills.client.SkillsClient.onTooltipRequest(p)));
        } else {
            NetworkManager.registerS2CPayloadType(TooltipRequest.TYPE, TooltipRequest.CODEC);
        }
        PlayerEvent.PLAYER_QUIT.register(p -> PayloadRecorder.forget(p.getUUID()));
    }

    static String cut(String s) {
        if (s == null) return "";
        return s.length() > MAX_LINE ? s.substring(0, MAX_LINE) : s;
    }

    /** Asks {@code p}'s client for the full tooltip of {@code stack} (when it has the mod). @return true if asked */
    public static boolean requestTooltip(ServerPlayer p, ItemStack stack) {
        if (!SkillsConfig.get().tooltipFromClient || stack.isEmpty()) return false;
        try {
            if (!NetworkManager.canPlayerReceive(p, TooltipRequest.TYPE)) return false;
        } catch (Throwable t) {
            return false;
        }
        int id;
        synchronized (SkillNet.class) {
            id = nextId++;
        }
        if (REQUESTS.size() > 256) REQUESTS.clear();
        REQUESTS.put(id, BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        NetworkManager.sendToPlayer(p, new TooltipRequest(id, stack.copyWithCount(1)));
        return true;
    }

    private static void onTooltip(ServerPlayer p, TooltipReport r) {
        String asked = REQUESTS.remove(r.id());
        if (asked == null || !asked.equals(r.itemId())) return; // only answers to our own questions
        ItemLearner.mergeTooltip(r.itemId(), r.lines(), "client");
    }

    private static void onKey(ServerPlayer p, KeyObserved k, long pressMs) {
        if (!SkillsConfig.get().keybindObservation) return;
        String held = BuiltInRegistries.ITEM.getKey(p.getMainHandItem().getItem()).toString();
        if (p.getMainHandItem().isEmpty() || !held.equals(k.itemId())) return;
        if (PENDING.size() > 64) PENDING.clear();
        PENDING.add(new Pending(p.getUUID(), pressMs, k));
    }

    /** Server tick: match key presses older than the correlation window to the packets that followed. */
    public static void tick(MinecraftServer server) {
        if (PENDING.isEmpty()) return;
        long now = System.currentTimeMillis();
        Iterator<Pending> it = PENDING.iterator();
        List<Pending> done = new ArrayList<>();
        while (it.hasNext()) {
            Pending pd = it.next();
            if (now < pd.pressMs() + KeybindCorrelator.AFTER + 50) continue;
            done.add(pd);
            try {
                correlate(server, pd);
            } catch (Throwable t) {
                TradeOfImmersion.LOGGER.debug("key correlation failed", t);
            }
        }
        PENDING.removeAll(done);
    }

    private static void correlate(MinecraftServer server, Pending pd) {
        List<PayloadRecorder.Seen> seen = PayloadRecorder.recent(pd.player());
        List<KeybindCorrelator.Packet> pk = new ArrayList<>();
        for (int i = 0; i < seen.size(); i++) pk.add(new KeybindCorrelator.Packet(seen.get(i).channel(), seen.get(i).timeMs(), i));
        List<KeybindCorrelator.Packet> hits = KeybindCorrelator.correlate(pk, pd.pressMs());
        if (hits.isEmpty()) return;
        PayloadBridge bridge = PayloadBridge.Holder.get();
        ItemSkillStore.Skill skill = ItemSkillStore.get().of(pd.key().itemId());
        String keyId = pd.key().mapping().toLowerCase(java.util.Locale.ROOT);
        List<ItemSkillStore.KeyPacket> list = skill.keyPackets.computeIfAbsent(keyId, x -> new ArrayList<>());
        int learned = 0;
        for (KeybindCorrelator.Packet h : hits) {
            if (learned >= 2) break;
            PayloadRecorder.Seen s = seen.get(h.index());
            byte[] data = bridge == null ? null : bridge.encode(s.payload(), server.registryAccess());
            if (data == null || data.length > 8192) continue;
            String b64 = Base64.getEncoder().encodeToString(data);
            ItemSkillStore.KeyPacket kp = list.stream().filter(x -> x.channel.equals(s.channel()) && x.data.equals(b64)).findFirst().orElse(null);
            if (kp == null) {
                kp = new ItemSkillStore.KeyPacket();
                kp.channel = s.channel();
                kp.data = b64;
                list.add(kp);
                if (list.size() > 4) list.remove(0);
            }
            kp.keyMapping = pd.key().mapping();
            kp.keyLabel = pd.key().label();
            kp.keyBound = pd.key().bound();
            kp.sightings++;
            learned++;
        }
        if (learned == 0) return;
        ItemLearner.addObservedKeyHint(pd.key().itemId(), pd.key().bound(), pd.key().label());
        // the key works again even if it was marked unusable before
        skill.unusable.removeIf(id -> id.startsWith("keybind") && id.endsWith(":" + pd.key().bound().toLowerCase(java.util.Locale.ROOT)));
        ItemSkillStore.get().markDirty();
        ServerPlayer p = server.getPlayerList().getPlayer(pd.player());
        if (p != null && list.get(list.size() - 1).sightings == 1) {
            boolean anyCompanion = !p.level().getEntitiesOfClass(CompanionEntity.class, p.getBoundingBox().inflate(32), c -> c.isOwner(p)).isEmpty();
            if (anyCompanion) p.displayClientMessage(Component.literal("Your companions learned: " + pd.key().label() + " [" + pd.key().bound() + "] with "
                    + p.getMainHandItem().getHoverName().getString()), true);
        }
    }
}
