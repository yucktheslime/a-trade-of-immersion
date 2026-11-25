package dev.yucktheslime.tradeofimmersion.life.hooks;

import dev.yucktheslime.tradeofimmersion.entity.CompanionEntity;
import dev.yucktheslime.tradeofimmersion.life.Life;
import dev.yucktheslime.tradeofimmersion.life.LifeConfig;
import dev.yucktheslime.tradeofimmersion.life.LifeEvents;
import dev.yucktheslime.tradeofimmersion.life.LifeMca;
import dev.yucktheslime.tradeofimmersion.life.marriage.Romance;
import dev.yucktheslime.tradeofimmersion.life.pregnancy.Pregnancies;
import dev.yucktheslime.tradeofimmersion.life.relation.RelationshipMath;
import dev.yucktheslime.tradeofimmersion.life.relation.Stage;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.conczin.mca.entity.ai.relationship.RelationshipState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** What the Phase 2 mixins and events call into. Every handler is defensive: a failure never breaks the game action. */
public final class LifeHooks {
    private static final Pattern FLIRT = Pattern.compile("(?i)\\b(i love you|love you|kiss|beautiful|handsome|gorgeous|cute|pretty|"
            + "sexy|hot stuff|date me|go out with me|marry me|sweetheart|darling|babe|honey|my love|crush on|adore you|flirt)\\b|<3");
    private static final Map<String, Long> HURT_NOTE = new ConcurrentHashMap<>();

    private LifeHooks() {
    }

    static boolean on() {
        return LifeConfig.get().enabled && Life.server() != null;
    }

    // ---------------------------------------------------------------- companions

    /** CompanionBrain.onChat: someone talks to a companion. */
    public static void onChatToCompanion(CompanionEntity c, String speaker, String text) {
        if (!on() || c.level().isClientSide || speaker == null || text == null) return;
        try {
            MinecraftServer server = Life.server();
            ServerPlayer p = server.getPlayerList().getPlayerByName(speaker);
            if (p == null) return;
            boolean flirt = FLIRT.matcher(text).find();
            Romance.interact(c, p, flirt ? RelationshipMath.Kind.FLIRT : RelationshipMath.Kind.CHAT, 1);
            if (flirt) {
                Optional<UUID> mine = Romance.partner(server, p.getUUID());
                if (mine.isEmpty() || !mine.get().equals(c.getUUID())) Romance.rivalry(p, c, 1.0, "flirting with");
                // a companion with a partner of its own being flirted with makes that partner jealous of it too
                Optional<UUID> theirs = Romance.partner(server, c.getUUID());
                if (theirs.isPresent() && !theirs.get().equals(p.getUUID())) Romance.rivalry(c, p, 0.5, "being flirted with by");
            }
        } catch (Throwable t) {
            Life.warnOnce("chat", t);
        }
    }

    /** CompanionEntity.giveFrom moved {@code moved} items from the player into the companion. */
    public static void onGiftToCompanion(CompanionEntity c, Player player, ItemStack given, int moved) {
        if (!on() || moved <= 0 || given.isEmpty()) return;
        try {
            double w = giftWeight(given);
            Romance.interact(c, player, RelationshipMath.Kind.GIFT, w);
            Optional<UUID> mine = Romance.partner(Life.server(), player.getUUID());
            if (mine.isEmpty() || !mine.get().equals(c.getUUID())) Romance.rivalry(player, c, 0.7, "giving a gift to");
        } catch (Throwable t) {
            Life.warnOnce("gift", t);
        }
    }

    static double giftWeight(ItemStack s) {
        String id = BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
        double w = s.getRarity() == Rarity.EPIC ? 3 : s.getRarity() == Rarity.RARE ? 2 : s.getRarity() == Rarity.UNCOMMON ? 1.5 : 1;
        if (id.contains("flower") || id.contains("rose") || id.contains("tulip") || id.contains("poppy") || id.contains("bouquet")
                || id.contains("cake") || id.contains("diamond") || id.contains("emerald") || id.contains("chocolate")) w += 0.5;
        if (id.contains("rotten") || id.contains("poison") || id.contains("spider_eye") || id.equals("minecraft:dirt")) w = -0.5;
        return w;
    }

    /** A companion speaks to an MCA villager (VillagerTalk): both get to know each other. */
    public static void onCompanionTalksToVillager(CompanionEntity c, VillagerEntityMCA v) {
        if (!on()) return;
        try {
            Romance.interact(v, c, RelationshipMath.Kind.CHAT, 1);
            Romance.interact(c, v, RelationshipMath.Kind.CHAT, 1);
        } catch (Throwable t) {
            Life.warnOnce("villager-talk", t);
        }
    }

    /**
     * CompanionEntity.mobInteract (before its own handling): MCA's bouquet, engagement ring, wedding ring and
     * divorce papers on a companion. Returns null to let the companion handle the click.
     */
    public static InteractionResult onCompanionInteract(CompanionEntity c, Player player, InteractionHand hand) {
        LifeConfig cfg = LifeConfig.get();
        if (!on() || c.level().isClientSide || hand != InteractionHand.MAIN_HAND || !cfg.on(cfg.ringsOnCompanions) || !cfg.companionMarriage) return null;
        if (!(player instanceof ServerPlayer sp)) return null;
        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty()) return null;
        String id = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
        Stage to = switch (id) {
            case "mca:bouquet" -> Stage.DATING;
            case "mca:engagement_ring", "mca:engagement_ring_rg" -> Stage.ENGAGED;
            case "mca:wedding_ring", "mca:wedding_ring_rg" -> Stage.MARRIED;
            case "mca:divorce_papers" -> Stage.NONE;
            default -> null;
        };
        if (to == null) return null;
        try {
            MinecraftServer server = sp.server;
            if (to == Stage.NONE) {
                Stage cur = Romance.stage(server, sp.getUUID(), c.getUUID());
                if (!cur.romantic()) {
                    sp.sendSystemMessage(Component.literal("You and " + c.name() + " aren't together.").withStyle(ChatFormatting.YELLOW));
                    return InteractionResult.CONSUME;
                }
                Romance.end(sp, c.getUUID(), true);
                c.say(cur == Stage.MARRIED ? "So this is how it ends..." : "Fine. Goodbye, then.");
                if (!sp.getAbilities().instabuild) held.shrink(1);
                return InteractionResult.CONSUME;
            }
            if (to == Stage.DATING && !cfg.courtshipStages) {
                return null; // without courtship stages a bouquet is just a gift
            }
            String msg = Romance.ask(sp, c, to);
            boolean ok = Romance.stage(server, sp.getUUID(), c.getUUID()) == to;
            if (ok && !sp.getAbilities().instabuild) held.shrink(1);
            sp.sendSystemMessage(Component.literal(msg).withStyle(ok ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.YELLOW));
            return InteractionResult.CONSUME;
        } catch (Throwable t) {
            Life.warnOnce("rings", t);
            return null;
        }
    }

    // ---------------------------------------------------------------- MCA villagers

    /** BreedableRelationship.giveGift: a player gave an MCA villager something (hearts before/after). */
    public static void onVillagerGift(ServerPlayer player, VillagerEntityMCA v, String item, int before, int after) {
        if (!on()) return;
        LifeConfig cfg = LifeConfig.get();
        if (!cfg.on(cfg.lifeHooks)) return;
        try {
            int d = after - before;
            String name = LifeMca.name(v);
            String text = player.getGameProfile().getName() + " gave " + name + " " + item
                    + (d > 0 ? " (" + name + " liked it)" : d < 0 ? " (" + name + " didn't like it)" : "");
            LifeEvents.record(player.server, v, "gift", text, v.getUUID());
            LifeEvents.remember(v.getUUID(), "gift", player.getGameProfile().getName() + " gave you " + item + (d > 0 ? "; you liked it" : d < 0 ? "; you didn't like it" : ""));
            Optional<UUID> mine = Romance.partner(player.server, player.getUUID());
            if (mine.isPresent() && !mine.get().equals(v.getUUID())) Romance.rivalry(player, v, 0.7, "giving a gift to");
        } catch (Throwable t) {
            Life.warnOnce("villager-gift", t);
        }
    }

    // ---------------------------------------------------------------- world events

    public static void onDeath(LivingEntity dead) {
        if (!on() || dead.level().isClientSide) return;
        try {
            Pregnancies.onDeath(dead);
            if (dead instanceof CompanionEntity c) {
                MinecraftServer server = ((ServerLevel) c.level()).getServer();
                FamilyTreeNode n = LifeMca.node(c);
                UUID partner = n.partner();
                n.setDeceased(true);
                if (FamilyTreeNode.isValid(partner) && n.getRelationshipState() != RelationshipState.SINGLE) {
                    boolean married = n.getRelationshipState().isMarried();
                    LifeMca.node(server, partner).ifPresent(pn -> pn.updatePartner(null, married ? RelationshipState.WIDOW : RelationshipState.SINGLE));
                    LifeEvents.record(server, c, "death", c.name() + " died, leaving " + LifeMca.nameOf(server, partner)
                            + (married ? " widowed" : " alone"), partner);
                    Entity pe = LifeMca.find(server, partner);
                    if (pe instanceof VillagerEntityMCA v) v.getVillagerBrain().modifyMoodValue(-30);
                    if (pe instanceof CompanionEntity pc) dev.yucktheslime.tradeofimmersion.life.Life.react(pc, "death",
                            "Your " + (married ? "spouse " : "partner ") + c.name() + " has died.");
                }
            }
        } catch (Throwable t) {
            Life.warnOnce("death", t);
        }
    }

    public static void onHurt(LivingEntity victim, Entity attacker) {
        if (!on() || attacker == null || victim == attacker || victim instanceof Player) return;
        if (!(victim instanceof CompanionEntity || victim instanceof VillagerEntityMCA) || !LifeMca.social(attacker)) return;
        try {
            String key = attacker.getUUID() + ">" + victim.getUUID();
            long now = victim.level().getGameTime();
            Long last = HURT_NOTE.get(key);
            if (last != null && now - last < 200) return;
            HURT_NOTE.put(key, now);
            if (HURT_NOTE.size() > 2000) HURT_NOTE.clear();
            Romance.interact(victim, attacker, RelationshipMath.Kind.HURT, 1);
        } catch (Throwable t) {
            Life.warnOnce("hurt", t);
        }
    }
}
