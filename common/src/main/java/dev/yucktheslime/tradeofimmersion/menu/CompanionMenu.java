package dev.yucktheslime.tradeofimmersion.menu;

import com.google.gson.JsonObject;
import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import dev.yucktheslime.tradeofimmersion.TradeOfImmersion;
import dev.yucktheslime.tradeofimmersion.ai.CompanionBrain;
import dev.yucktheslime.tradeofimmersion.compat.McaEditorBridge;
import dev.yucktheslime.tradeofimmersion.entity.CompanionEntity;
import dev.yucktheslime.tradeofimmersion.voice.VoiceProfiles;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;

import java.util.ArrayList;
import java.util.List;

/**
 * The companion's right-click menu (MCA-style): Talk, Follow/Stay/Wander, Inventory, Give held item, Edit look
 * (MCA's villager editor), Voice (pick a PocketTTS speaker and pitch). MCA's own interaction screen only works on
 * MCA villager entities (it reads their MCA brain, memories and family tree), so companions get this menu with the
 * same kind of buttons, and MCA's editor/needle/comb screens are opened on them for real.
 */
public final class CompanionMenu {
    public record Open(int entityId, boolean owner, boolean canEdit, String mode) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TradeOfImmersion.MOD_ID, "companion_menu"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = StreamCodec.of((b, p) -> {
            b.writeVarInt(p.entityId);
            b.writeBoolean(p.owner);
            b.writeBoolean(p.canEdit);
            b.writeUtf(p.mode, 32);
        }, b -> new Open(b.readVarInt(), b.readBoolean(), b.readBoolean(), b.readUtf(32)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Action(int entityId, String action, String arg) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TradeOfImmersion.MOD_ID, "companion_menu_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC = StreamCodec.of((b, p) -> {
            b.writeVarInt(p.entityId);
            b.writeUtf(p.action, 32);
            b.writeUtf(p.arg, 256);
        }, b -> new Action(b.readVarInt(), b.readUtf(32), b.readUtf(256)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Voice picker data: the NPC's voice and the speakers PocketTTS has (or a message when none). */
    public record Voices(int entityId, String current, String source, float pitch, List<String> speakers, String message) implements CustomPacketPayload {
        public static final Type<Voices> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TradeOfImmersion.MOD_ID, "companion_voices"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Voices> CODEC = StreamCodec.of((b, p) -> {
            b.writeVarInt(p.entityId);
            b.writeUtf(p.current, 64);
            b.writeUtf(p.source, 16);
            b.writeFloat(p.pitch);
            b.writeVarInt(Math.min(500, p.speakers.size()));
            for (int i = 0; i < Math.min(500, p.speakers.size()); i++) b.writeUtf(p.speakers.get(i), 64);
            b.writeUtf(p.message, 300);
        }, b -> {
            int id = b.readVarInt();
            String cur = b.readUtf(64), src = b.readUtf(16);
            float pitch = b.readFloat();
            int n = b.readVarInt();
            List<String> s = new ArrayList<>();
            for (int i = 0; i < n && i < 500; i++) s.add(b.readUtf(64));
            return new Voices(id, cur, src, pitch, s, b.readUtf(300));
        });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private CompanionMenu() {
    }

    public static void init() {
        NetworkManager.registerReceiver(NetworkManager.c2s(), Action.TYPE, Action.CODEC,
                (p, ctx) -> ctx.queue(() -> { if (ctx.getPlayer() instanceof ServerPlayer sp) handle(sp, p); }));
        if (Platform.getEnvironment() == Env.CLIENT) {
            NetworkManager.registerReceiver(NetworkManager.s2c(), Open.TYPE, Open.CODEC,
                    (p, ctx) -> ctx.queue(() -> dev.yucktheslime.tradeofimmersion.client.menu.CompanionMenuClient.open(p)));
            NetworkManager.registerReceiver(NetworkManager.s2c(), Voices.TYPE, Voices.CODEC,
                    (p, ctx) -> ctx.queue(() -> dev.yucktheslime.tradeofimmersion.client.menu.CompanionMenuClient.voices(p)));
        } else {
            NetworkManager.registerS2CPayloadType(Open.TYPE, Open.CODEC);
            NetworkManager.registerS2CPayloadType(Voices.TYPE, Voices.CODEC);
        }
    }

    public static void open(ServerPlayer p, CompanionEntity c) {
        boolean owner = c.isOwner(p) || p.hasPermissions(2);
        NetworkManager.sendToPlayer(p, new Open(c.getId(), owner, McaEditorBridge.mayEdit(p, c), c.getMode()));
    }

    private static void handle(ServerPlayer p, Action a) {
        if (!(p.level().getEntity(a.entityId()) instanceof CompanionEntity c) || !c.isAlive() || c.distanceToSqr(p) > 12 * 12) return;
        boolean owner = c.isOwner(p) || p.hasPermissions(2);
        String who = p.getGameProfile().getName();
        switch (a.action()) {
            case "talk" -> {
                String text = a.arg().replaceAll("\\s+", " ").trim();
                if (!text.isEmpty()) {
                    p.sendSystemMessage(Component.literal("<" + who + " to " + c.name() + "> " + text).withStyle(s -> s.withColor(0xAAAAAA)));
                    CompanionBrain.onChat(c, who, text);
                }
            }
            case "follow", "stay", "wander" -> {
                if (!owner) return;
                c.setTask(null);
                c.setMode(a.action());
                c.say(switch (a.action()) {
                    case "follow" -> "Following you.";
                    case "stay" -> "I'll wait here.";
                    default -> "I'll look around here.";
                });
            }
            case "inventory" -> {
                if (owner) c.openInventory(p);
            }
            case "gift" -> {
                if (owner) c.giveFrom(p, InteractionHand.MAIN_HAND);
            }
            case "edit", "needle", "comb" -> McaEditorBridge.open(p, c, a.action());
            case "voice" -> sendVoices(p, c, "");
            case "voice_set", "voice_pitch" -> {
                if (!owner) {
                    p.displayClientMessage(Component.literal("Only " + c.getOwnerName() + " can change " + c.name() + "'s voice."), true);
                    return;
                }
                Double pitch = null;
                String voice = null;
                if (a.action().equals("voice_pitch")) {
                    try {
                        pitch = Math.max(0.5, Math.min(2.0, Double.parseDouble(a.arg())));
                    } catch (NumberFormatException e) {
                        return;
                    }
                } else {
                    voice = a.arg();
                }
                MinecraftServer srv = p.server;
                VoiceProfiles.set(VoiceProfiles.npcId(c), c.name(), voice, null, pitch).whenComplete((o, err) -> srv.execute(() -> {
                    String msg = err != null ? "Voice: " + (err.getCause() != null ? err.getCause() : err).getMessage()
                            : VoiceProfiles.ok(o) ? "" : "Voice: " + VoiceProfiles.error(o);
                    sendVoices(p, c, msg);
                }));
            }
            default -> {
            }
        }
    }

    private static void sendVoices(ServerPlayer p, CompanionEntity c, String note) {
        MinecraftServer srv = p.server;
        String npcId = VoiceProfiles.npcId(c);
        VoiceProfiles.list().thenCombine(VoiceProfiles.get(npcId).exceptionally(t -> new JsonObject()), (list, one) -> {
            JsonObject npc = one.has("npc") ? one.getAsJsonObject("npc") : new JsonObject();
            List<String> speakers = list.has("speakers") ? VoiceProfiles.strings(list.getAsJsonArray("speakers")) : List.of();
            String msg = note;
            if (msg.isEmpty() && !VoiceProfiles.ok(list)) msg = VoiceProfiles.error(list);
            if (msg.isEmpty() && speakers.isEmpty()) msg = "PocketTTS did not answer: start it in the DwemerDistro launcher, and turn voice on (voiceEnabled).";
            float pitch = 1f;
            try {
                if (!VoiceProfiles.str(npc, "pitch").isEmpty()) pitch = Float.parseFloat(VoiceProfiles.str(npc, "pitch"));
            } catch (NumberFormatException ignored) {
            }
            return new Voices(c.getId(), VoiceProfiles.str(npc, "voice"), VoiceProfiles.str(npc, "source"), pitch, speakers, msg);
        }).whenComplete((v, err) -> srv.execute(() -> NetworkManager.sendToPlayer(p, v != null ? v
                : new Voices(c.getId(), "", "", 1f, List.of(), "Voice: " + (err.getCause() != null ? err.getCause() : err).getMessage()))));
    }
}
