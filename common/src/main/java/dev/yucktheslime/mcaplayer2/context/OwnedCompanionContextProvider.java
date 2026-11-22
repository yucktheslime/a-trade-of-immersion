package dev.yucktheslime.mcaplayer2.context;

import com.player2.playerengine.player2api.AgentConversationData;
import dev.yucktheslime.mcaplayer2.config.BridgeConfig;
import dev.yucktheslime.mcaplayer2.player2.CompanionAdapter;
import dev.yucktheslime.mcaplayer2.util.OwnedEntityUtil;
import dev.yucktheslime.mcaplayer2.util.OwnedEntityUtil.OwnedEntity;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Tells the companion about tamed companions / owned animals around it and who they belong to, e.g.
 * {@code This wolf "Rex" is a tamed companion belonging to Alice.} Lines for the villager being talked to
 * come first. Also used to tell a villager about its own tamed companions in its reply prompt.
 */
public final class OwnedCompanionContextProvider implements PromptContextProvider {
    public static final OwnedCompanionContextProvider INSTANCE = new OwnedCompanionContextProvider();

    private OwnedCompanionContextProvider() {
    }

    @Override
    public List<String> contextLines(AgentConversationData companion, VillagerEntityMCA villager) {
        BridgeConfig cfg = BridgeConfig.get();
        if (!cfg.detectOwnedCompanions) return List.of();
        LivingEntity self = CompanionAdapter.safeEntity(companion);
        if (self == null) return List.of();
        List<OwnedEntity> owned = OwnedEntityUtil.findOwnedNear(self, cfg.ownedCompanionScanRadius, e -> e == self);
        List<String> first = new ArrayList<>();
        List<String> rest = new ArrayList<>();
        for (OwnedEntity o : owned) {
            String line = describe(o, companion);
            if (line == null) continue;
            boolean belongsToTarget = villager != null && o.ownerVillager() == villager;
            (belongsToTarget ? first : rest).add(line + (belongsToTarget ? " (the villager you are talking to)" : ""));
        }
        first.addAll(rest);
        return first;
    }

    /** Lines for a villager's own prompt: tamed companions near it that it owns. */
    public List<String> villagerOwnLines(VillagerEntityMCA villager) {
        BridgeConfig cfg = BridgeConfig.get();
        if (!cfg.detectOwnedCompanions) return List.of();
        List<String> out = new ArrayList<>();
        for (OwnedEntity o : OwnedEntityUtil.findOwnedNear(villager, cfg.ownedCompanionScanRadius, null)) {
            if (o.ownerVillager() == villager && o.kind() == OwnedEntityUtil.OwnerKind.MCA_VILLAGER) {
                out.add("Your tamed companion, a " + o.label() + ", is nearby.");
            }
        }
        return out;
    }

    static String describe(OwnedEntity o, AgentConversationData companion) {
        String what = "This " + o.label();
        return switch (o.kind()) {
            case MCA_VILLAGER -> what + " is a tamed companion belonging to the villager " + o.ownerName() + ".";
            case MCA_VILLAGER_GUESSED -> what + " is an owned animal; it probably belongs to the villager " + o.ownerName()
                    + " (nearest villager, not confirmed).";
            case PLAYER -> {
                var owner = CompanionAdapter.owner(companion);
                boolean ours = owner != null && owner.getUUID().equals(o.ownerUuid());
                yield what + " is a tamed companion belonging to " + (ours ? "your owner " : "the player ") + o.ownerName() + ".";
            }
            case UNKNOWN -> BridgeConfig.get().mentionUnknownOwners
                    ? what + " is an owned animal whose owner is not around." : null;
        };
    }
}
