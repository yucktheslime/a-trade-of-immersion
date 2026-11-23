package dev.yucktheslime.tradeofimmersion.villager;

import dev.yucktheslime.tradeofimmersion.TradeOfImmersion;
import dev.yucktheslime.tradeofimmersion.ai.CompanionBrain;
import dev.yucktheslime.tradeofimmersion.ai.VillagerRequest;
import dev.yucktheslime.tradeofimmersion.config.ToiConfig;
import dev.yucktheslime.tradeofimmersion.entity.CompanionEntity;
import dev.yucktheslime.tradeofimmersion.memory.ConversationMemoryStore;
import dev.yucktheslime.tradeofimmersion.memory.NpcMemoryStore;
import dev.yucktheslime.tradeofimmersion.util.McaVillagerUtil;
import dev.yucktheslime.tradeofimmersion.util.VillagerProfile;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Companion &lt;-&gt; MCA villager conversations. The villager's line comes from the same AI backend, prompted
 * with its MCA profile (personality, mood, profession, hearts with the owner) and the saved conversation log;
 * the companion can answer, up to {@code maxVillagerExchanges} villager lines per conversation.
 */
public final class VillagerTalk {
    private VillagerTalk() {
    }

    public static void companionSpeaks(CompanionEntity c, VillagerEntityMCA v, String message, boolean initial) {
        if (c.getServer() == null || message == null || message.isBlank()) return;
        if (initial && !v.getUUID().equals(c.talkingVillager)) c.villagerExchanges = 0;
        c.talkingVillager = v.getUUID();
        String vName = McaVillagerUtil.name(v);
        if (initial) c.say("(to " + vName + ") " + message);
        McaVillagerUtil.lookAt(v, c);
        ConversationMemoryStore.get().append(v.getUUID(), vName, c.name(), ConversationMemoryStore.Speaker.COMPANION, message, c.level().getGameTime());
        if (!McaVillagerUtil.canTalk(v)) {
            NpcMemoryStore.get().event(c.getUUID(), "talk", vName + " can't talk right now (asleep or busy)");
            return;
        }
        if (!CompanionBrain.trySlot()) {
            NpcMemoryStore.get().event(c.getUUID(), "talk", vName + " didn't answer (AI rate limit)");
            return;
        }
        ToiConfig cfg = ToiConfig.get();
        VillagerProfile prof = McaVillagerUtil.profile(v, c.getOwner());
        String system = "You are " + vName + ", a villager in a Minecraft village (MCA). " + prof.describe()
                + "\nA traveller named " + c.name() + " (who travels with " + c.getOwnerName() + ") is talking to you. "
                + "Answer in character, in at most " + cfg.villagerReplyMaxWords + " words, as plain spoken text on one line. "
                + "Let your personality, mood and how much you like " + c.getOwnerName() + " show. Never mention being an AI.";
        List<String> history = ConversationMemoryStore.get().recentLines(v.getUUID(), c.name(), cfg.historyLinesInPrompt);
        String user = "Earlier conversation:\n" + String.join("\n", history) + "\n\n" + c.name() + " says: " + message + "\nYour reply:";
        Map<String, String> ctx = new LinkedHashMap<>();
        ctx.put("profession", prof.profession());
        ctx.put("personality", prof.personality());
        ctx.put("mood", prof.mood());
        ctx.put("speaker_owner", c.getOwnerName());
        MinecraftServer srv = c.getServer();
        UUID cid = c.getUUID(), vid = v.getUUID();
        CompanionBrain.active().villagerLine(new VillagerRequest(vid.toString(), vName, c.name(), system, user, message, ctx))
                .whenComplete((line, err) -> srv.execute(() -> {
                    CompanionEntity comp = CompanionBrain.find(srv, cid);
                    VillagerEntityMCA vill = findVillager(srv, vid);
                    if (vill == null) return;
                    if (err != null || line == null || line.isBlank()) {
                        if (err != null) dev.yucktheslime.tradeofimmersion.util.QuietLog.warn("villager-line", "Villager line failed: {}", err.toString());
                        return;
                    }
                    String clean = line.replaceAll("^\"|\"$", "").trim();
                    McaVillagerUtil.say(vill, clean);
                    dev.yucktheslime.tradeofimmersion.voice.VoiceSync.spoke(vill, dev.yucktheslime.tradeofimmersion.ai.KagrenacBackend.villagerId(vid.toString()),
                            clean, "", prof.gender().isEmpty() ? "neutral" : prof.gender());
                    dev.yucktheslime.tradeofimmersion.ai.KagrenacWorld.event("villager_spoke", null,
                            vName + " (villager) told " + (comp == null ? "a traveller" : comp.name()) + ": " + clean);
                    ConversationMemoryStore.get().append(vid, vName, comp == null ? "" : comp.name(), ConversationMemoryStore.Speaker.VILLAGER, clean, vill.level().getGameTime());
                    if (comp == null) return;
                    NpcMemoryStore.get().event(cid, "talk", vName + " told you: " + clean);
                    NpcMemoryStore.get().chat(cid, vName, clean);
                    comp.villagerExchanges++;
                    comp.lastVillagerLineTick = comp.level().getGameTime();
                    if (comp.villagerExchanges < cfg.maxVillagerExchanges) CompanionBrain.onVillagerLine(comp, vName, clean);
                    else comp.talkingVillager = null;
                }));
    }

    /** The companion answered a villager line out loud: pass it on unless it is continuing with an explicit talk action. */
    public static void companionReplied(CompanionEntity c, String said, boolean continuingWithAction) {
        if (c.talkingVillager == null || continuingWithAction || c.getServer() == null) return;
        if (c.villagerExchanges >= ToiConfig.get().maxVillagerExchanges) return;
        VillagerEntityMCA v = findVillager(c.getServer(), c.talkingVillager);
        if (v == null || v.distanceToSqr(c) > 8 * 8) return;
        companionSpeaks(c, v, said, false);
    }

    private static VillagerEntityMCA findVillager(MinecraftServer srv, UUID id) {
        for (ServerLevel l : srv.getAllLevels()) {
            Entity e = l.getEntity(id);
            if (e instanceof VillagerEntityMCA v) return v;
        }
        return null;
    }
}
