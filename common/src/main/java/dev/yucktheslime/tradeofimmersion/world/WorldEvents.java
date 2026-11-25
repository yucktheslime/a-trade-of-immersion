package dev.yucktheslime.tradeofimmersion.world;

import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.ChatEvent;
import dev.architectury.event.events.common.EntityEvent;
import dev.yucktheslime.tradeofimmersion.ai.CompanionBrain;
import dev.yucktheslime.tradeofimmersion.chatfilter.ChatFilter;
import dev.yucktheslime.tradeofimmersion.chatfilter.ChatFilterHooks;
import dev.yucktheslime.tradeofimmersion.config.ToiConfig;
import dev.yucktheslime.tradeofimmersion.entity.CompanionEntity;
import dev.yucktheslime.tradeofimmersion.memory.NpcMemoryStore;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * World awareness: deaths and attacks near companions become memories (and reactions for the important ones),
 * and player chat is routed by {@link dev.yucktheslime.tradeofimmersion.chatfilter.ChatFilterHooks}
 * so the AI only answers when a player addresses an NPC.
 */
public final class WorldEvents {
    private static final Map<UUID, Long> LAST_HURT_NOTE = new ConcurrentHashMap<>();

    private WorldEvents() {
    }

    public static void init() {
        ChatFilterHooks.init();
        EntityEvent.LIVING_DEATH.register(WorldEvents::onDeath);
        EntityEvent.LIVING_HURT.register(WorldEvents::onHurt);
        ChatEvent.RECEIVED.register((player, component) -> {
            if (player != null) {
                String s = component.getString();
                player.server.execute(() -> onChat(player, s));
            }
            return EventResult.pass();
        });
    }

    private static List<CompanionEntity> companionsNear(Entity e, double r) {
        return e.level().getEntitiesOfClass(CompanionEntity.class, e.getBoundingBox().inflate(r), c -> c.isAlive() && c != e);
    }

    private static String who(Entity e) {
        if (e == null) return "something";
        if (e instanceof Player p) return p.getGameProfile().getName();
        return e.getName().getString();
    }

    private static EventResult onDeath(LivingEntity dead, DamageSource source) {
        if (dead.level().isClientSide) return EventResult.pass();
        double r = ToiConfig.get().eventAwarenessRadius;
        Entity killer = source.getEntity();
        String text = who(dead) + (killer != null ? " was killed by " + who(killer) : " died (" + source.getMsgId() + ")");
        boolean notable = dead instanceof Player || dead instanceof VillagerEntityMCA || dead instanceof CompanionEntity || dead.hasCustomName();
        forwardDeath(dead, source, killer, notable);
        for (CompanionEntity c : companionsNear(dead, r)) {
            boolean mine = killer == c;
            if (!notable && !mine && dead.distanceToSqr(c) > 16 * 16) continue;
            NpcMemoryStore.get().event(c.getUUID(), "death", mine ? "You killed " + who(dead) : text);
            if (c.isOwner(dead)) CompanionBrain.onEvent(c, "combat_ended", who(dead) + ", the player you travel with, just died! (" + text + ")");
            else if (notable && !(dead instanceof CompanionEntity o && o == c)) CompanionBrain.onEvent(c, "death", text + " nearby.");
        }
        return EventResult.pass();
    }

    private static boolean boss(Entity e) {
        return e instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon || e instanceof net.minecraft.world.entity.boss.wither.WitherBoss
                || e instanceof net.minecraft.world.entity.monster.warden.Warden || e instanceof net.minecraft.world.entity.monster.ElderGuardian;
    }

    private static String where(Entity e) {
        return e.blockPosition().toShortString() + " in the " + e.level().dimension().location().getPath().replace('_', ' ');
    }

    /**
     * One log-only Kagrenac event per death worth knowing about, wherever it happened (no companion has to be near):
     * players, companions, MCA villagers, named mobs and bosses, and kills by companions (and, with
     * kagrenacLogMobKills, players' kills of hostile mobs). Sent in the background; never shown in chat.
     */
    private static void forwardDeath(LivingEntity dead, DamageSource source, Entity killer, boolean notable) {
        boolean byCompanion = killer instanceof CompanionEntity;
        boolean mobKill = killer instanceof Player && dead instanceof net.minecraft.world.entity.monster.Enemy && ToiConfig.get().kagrenacLogMobKills;
        if (!notable && !boss(dead) && !byCompanion && !mobKill) return;
        String what;
        try {
            what = source.getLocalizedDeathMessage(dead).getString(); // the vanilla death line, e.g. "Steve was slain by Zombie"
        } catch (Exception e) {
            what = who(dead) + (killer != null ? " was killed by " + who(killer) : " died");
        }
        String kind = dead instanceof Player ? "Player death: "
                : dead instanceof CompanionEntity c ? "Companion death (" + c.getOwnerName() + "'s companion): "
                : dead instanceof VillagerEntityMCA ? "Villager death: "
                : boss(dead) ? "Boss killed: " : "Kill: ";
        Map<String, String> ctx = new java.util.LinkedHashMap<>();
        ctx.put("location", where(dead));
        if (killer != null) ctx.put("killer", who(killer));
        dev.yucktheslime.tradeofimmersion.ai.KagrenacWorld.event("entity_died", null, kind + what + " at " + where(dead), ctx);
    }

    private static EventResult onHurt(LivingEntity victim, DamageSource source, float amount) {
        if (victim.level().isClientSide || amount <= 0) return EventResult.pass();
        Entity attacker = source.getEntity();
        if (attacker == null) return EventResult.pass();
        boolean playerOrVillager = victim instanceof Player || victim instanceof VillagerEntityMCA || attacker instanceof Player;
        // fights worth forwarding even with no companion near: player vs player/villager/companion, and attacks on villagers/companions
        boolean social = victim instanceof Player || victim instanceof VillagerEntityMCA || victim instanceof CompanionEntity;
        boolean forward = social && (attacker instanceof Player || attacker instanceof CompanionEntity || victim instanceof VillagerEntityMCA
                || victim instanceof CompanionEntity || boss(attacker));
        if (!playerOrVillager && !forward) return EventResult.pass();
        long now = victim.level().getGameTime();
        String key = attacker.getStringUUID() + victim.getStringUUID();
        Long last = LAST_HURT_NOTE.get(UUID.nameUUIDFromBytes(key.getBytes()));
        if (last != null && now - last < 200) return EventResult.pass();
        LAST_HURT_NOTE.put(UUID.nameUUIDFromBytes(key.getBytes()), now);
        if (LAST_HURT_NOTE.size() > 2000) LAST_HURT_NOTE.clear();
        if (!forward && playerOrVillager && !companionsNear(victim, ToiConfig.get().eventAwarenessRadius).isEmpty()) forward = true;
        if (forward && !(victim instanceof CompanionEntity)) { // CompanionEntity.hurt logs its own damage
            Map<String, String> ctx = new java.util.LinkedHashMap<>();
            ctx.put("location", where(victim));
            dev.yucktheslime.tradeofimmersion.ai.KagrenacWorld.event(victim instanceof Player ? "player_hurt" : "npc_hurt", null,
                    who(attacker) + " attacked " + who(victim) + " (" + Math.round(amount) + " damage, health "
                            + Math.max(0, Math.round(victim.getHealth() - amount)) + "/" + Math.round(victim.getMaxHealth()) + ") at " + where(victim), ctx);
        }
        if (!playerOrVillager) return EventResult.pass();
        for (CompanionEntity c : companionsNear(victim, ToiConfig.get().eventAwarenessRadius)) {
            if (victim == c) continue; // CompanionEntity.hurt records its own damage
            String text = who(attacker) + " attacked " + (c.isOwner(victim) ? "your companion " : "") + who(victim);
            NpcMemoryStore.get().event(c.getUUID(), "attack", text);
            if (c.isOwner(victim) && !(attacker instanceof Player) && c.getTarget() == null) {
                // the defend-owner target goal handles the fight; just a heads-up line occasionally
                CompanionBrain.onEvent(c, "combat_started", text + ".");
            }
        }
        return EventResult.pass();
    }

    /**
     * Only real dialogue reaches companions. Architectury's chat event already excludes system messages
     * (command feedback, advancements, death messages, other mods' broadcasts); this also drops commands,
     * pasted error codes and similar noise a player might type or relay.
     */
    public static boolean isDialogue(String text) {
        return ChatFilter.isDialogue(text);
    }

    private static void onChat(ServerPlayer player, String raw) {
        ChatFilterHooks.handlePublicChat(player, raw);
    }
}
