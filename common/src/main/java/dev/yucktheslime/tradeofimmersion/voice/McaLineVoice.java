package dev.yucktheslime.tradeofimmersion.voice;

import dev.yucktheslime.tradeofimmersion.ai.KagrenacBackend;
import dev.yucktheslime.tradeofimmersion.config.ToiConfig;
import dev.yucktheslime.tradeofimmersion.util.McaVillagerUtil;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerLike;
import net.minecraft.world.entity.Entity;

/**
 * Hooks MCA's {@code sendChatToAllAround} so scripted villager lines can use this mod's PocketTTS voice.
 * Optional and defensive: a change in MCA must never break villager chat.
 */
public final class McaLineVoice {
    private McaLineVoice() {
    }

    public static void maybeVoiceScripted(Object messenger, String text) {
        try {
            ToiConfig cfg = ToiConfig.get();
            if (!ToiVoicePolicy.shouldVoiceScriptedMcaLine(cfg.voiceEnabled, cfg.voiceVillagers, cfg.mcaLinesUseToiVoice)) {
                return;
            }
            if (text == null || text.isBlank()) return;
            Entity speaker = entity(messenger);
            if (!(speaker instanceof VillagerEntityMCA v) || !v.isAlive()) return;
            String hint = "neutral";
            try {
                String gender = McaVillagerUtil.profile(v, null).gender();
                if (gender != null && !gender.isEmpty()) hint = gender;
            } catch (RuntimeException | LinkageError ignored) {
            }
            VoiceSync.spoke(v, KagrenacBackend.villagerId(v.getUUID().toString()), text, "", hint);
        } catch (RuntimeException | LinkageError ignored) {
            // never break MCA chat
        }
    }

    private static Entity entity(Object messenger) {
        if (messenger instanceof Entity e) return e;
        if (messenger instanceof VillagerLike<?> vl) {
            try {
                return vl.asEntity();
            } catch (RuntimeException | LinkageError e) {
                return null;
            }
        }
        return null;
    }
}
