package dev.yucktheslime.tradeofimmersion.life.pregnancy;

import dev.yucktheslime.tradeofimmersion.TradeOfImmersion;
import dev.yucktheslime.tradeofimmersion.ai.CompanionBrain;
import dev.yucktheslime.tradeofimmersion.entity.CompanionEntity;
import dev.yucktheslime.tradeofimmersion.life.JsonStore;
import dev.yucktheslime.tradeofimmersion.life.LifeConfig;
import dev.yucktheslime.tradeofimmersion.life.LifeEvents;
import dev.yucktheslime.tradeofimmersion.life.LifeMca;
import dev.yucktheslime.tradeofimmersion.life.LifeRegistry;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Pregnancy;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.item.BabyItem;
import net.conczin.mca.item.components.BabyParentsComponent;
import net.conczin.mca.registry.DataComponentsMCA;
import net.conczin.mca.util.WorldUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Pregnancy with phases, hooked into MCA Reborn's own procreation:
 * <ul>
 *   <li>Player + villager ("procreate" in MCA's menu) and player + player ({@code /mca procreate}): MCA creates the
 *   baby item through {@code BabyItem.createItem}; a mixin turns that into the start of a pregnancy for the one MCA
 *   chose as the mother, and the baby item is made at birth.</li>
 *   <li>Villager + villager gestation (MCA's village procreation): MCA's own pregnancy timer is paused and these
 *   phases run instead; at birth the child spawns like MCA does it.</li>
 *   <li>Companion couples (with players, companions or villagers): started by this mod ({@code /npc romance
 *   procreate} or on their own).</li>
 * </ul>
 * Saved in {@code <world>/data/tradeofimmersion/pregnancies.json}; timing uses overworld game time, so it keeps
 * running while the carrier is unloaded or offline (the birth waits until they are back).
 */
public final class Pregnancies {
    public static final class Rec {
        public String carrier, carrierName, carrierKind;
        public String partner, partnerName, partnerKind;
        public int babies = 1;
        /** MCA villager-villager gestation (birth spawns the child entity like MCA). */
        public boolean nativeMca;
        /** Ended (birth or miscarriage) while the carrier was away; handled when they are loaded again. */
        public boolean pending;
        public PregnancyPhases.State state;
    }

    public static final class Data {
        public int version = 1;
        public Map<String, Rec> active = new LinkedHashMap<>();
        /** carrier -> game time of the last pregnancy start (cool-down for NPC couples). */
        public Map<String, Long> lastStart = new HashMap<>();
    }

    public static final JsonStore<Data> STORE = new JsonStore<>("pregnancies.json", Data.class, Data::new);
    /** True while this mod itself makes a baby item (so the BabyItem mixin lets it through). */
    public static final ThreadLocal<Boolean> BYPASS = ThreadLocal.withInitial(() -> false);
    private static final Random RNG = new Random();

    private Pregnancies() {
    }

    public static boolean phasesOn() {
        LifeConfig c = LifeConfig.get();
        return c.on(c.pregnancyPhases);
    }

    public static Rec get(UUID carrier) {
        return STORE.data().active.get(carrier.toString());
    }

    public static boolean pregnant(UUID id) {
        Rec r = get(id);
        return r != null && !r.state.ended;
    }

    // ---------------------------------------------------------------- starting

    /**
     * From the BabyItem mixin: MCA wants to hand out a baby item now. Returns true to cancel it (a pregnancy starts
     * instead). Twins: MCA calls this several times in the same tick with the same seed.
     */
    public static boolean interceptBaby(Entity mother, Entity father, long seed) {
        if (BYPASS.get() || !phasesOn() || mother == null || father == null || mother.level().isClientSide) return false;
        MinecraftServer server = ((ServerLevel) mother.level()).getServer();
        Entity carrier = mother, other = father;
        // MCA picks the mother from villager genes; for two players (or a male "mother") prefer the female one
        if (LifeMca.gender(carrier) != Gender.FEMALE && LifeMca.gender(other) == Gender.FEMALE) {
            carrier = father;
            other = mother;
        }
        long now = LifeMca.now(server);
        Rec r = get(carrier.getUUID());
        if (r != null && !r.state.ended) {
            if (r.state.start == now && r.state.seed == seed && other.getUUID().toString().equals(r.partner)) {
                r.babies = Math.min(8, r.babies + 1); // twins, triplets...
                STORE.dirty();
            } else if (carrier instanceof Player p) {
                p.sendSystemMessage(Component.literal("You are already pregnant.").withStyle(ChatFormatting.YELLOW));
            }
            return true;
        }
        start(carrier, other, seed, false);
        return true;
    }

    /** From the Pregnancy mixin: MCA started villager-villager gestation for {@code mother}. */
    public static void onNativeGestation(VillagerEntityMCA mother) {
        if (!phasesOn() || mother.level().isClientSide || pregnant(mother.getUUID())) return;
        Entity father = mother.getRelationships().getPartner().orElse(null);
        Rec r = start(mother, father, mother.getRandom().nextLong(), true);
        if (r == null) return;
    }

    /** Whether MCA's own gestation timer should be paused for this villager (our phases run instead). */
    public static boolean handlesNative(VillagerEntityMCA mother) {
        return phasesOn() && get(mother.getUUID()) != null;
    }

    public static Rec start(Entity carrier, Entity partner, long seed, boolean nativeMca) {
        MinecraftServer server = ((ServerLevel) carrier.level()).getServer();
        if (pregnant(carrier.getUUID())) return null;
        LifeConfig cfg = LifeConfig.get();
        Rec r = new Rec();
        r.carrier = carrier.getUUID().toString();
        r.carrierName = LifeMca.name(carrier);
        r.carrierKind = LifeMca.kind(carrier).name();
        if (partner != null) {
            r.partner = partner.getUUID().toString();
            r.partnerName = LifeMca.name(partner);
            r.partnerKind = LifeMca.kind(partner).name();
        } else {
            r.partner = "";
            r.partnerName = "";
            r.partnerKind = "";
        }
        r.nativeMca = nativeMca;
        r.state = new PregnancyPhases.State(LifeMca.now(server), cfg.pregnancyParams(), seed);
        STORE.data().active.put(r.carrier, r);
        STORE.data().lastStart.put(r.carrier, r.state.start);
        STORE.dirty();
        // family-tree nodes exist from the start (companions too)
        LifeMca.node(carrier);
        if (partner != null && LifeMca.social(partner)) LifeMca.node(partner);
        if (carrier instanceof VillagerEntityMCA v && !nativeMca) v.getRelationships().getPregnancy().setPregnant(true);
        String text = r.carrierName + (r.partnerName.isEmpty() ? " is" : " and " + r.partnerName + " are") + " expecting a baby";
        tell(server, r, "You're pregnant! The baby is due in about " + days(r.state.total()) + " in-game days.",
                r.carrierName + " is pregnant with your baby! Due in about " + days(r.state.total()) + " in-game days.");
        LifeEvents.record(server, carrier, "pregnancy", text, carrier.getUUID(), partner == null ? null : partner.getUUID());
        if (carrier instanceof CompanionEntity c) CompanionBrain.onEvent(c, "pregnancy", "You just found out you are pregnant" +
                (r.partnerName.isEmpty() ? "" : " with " + r.partnerName + "'s baby") + ". React in character (one line).");
        TradeOfImmersion.LOGGER.info("[tradeofimmersion] Pregnancy: {} (partner {}, {} days, native={})", r.carrierName, r.partnerName,
                days(r.state.total()), nativeMca);
        return r;
    }

    static String days(long ticks) {
        double d = ticks / (double) PregnancyPhases.DAY;
        return d == Math.floor(d) ? String.valueOf((long) d) : String.format(java.util.Locale.ROOT, "%.1f", d);
    }

    // ---------------------------------------------------------------- ticking

    /** Once a second from the server tick. */
    public static void tick(MinecraftServer server) {
        if (STORE.data().active.isEmpty()) {
            if (server.getTickCount() % 100 == 0) cleanupEffects(server);
            return;
        }
        LifeConfig cfg = LifeConfig.get();
        long now = LifeMca.now(server);
        PregnancyPhases.Params params = cfg.pregnancyParams();
        boolean sideTick = server.getTickCount() % 600 == 0;
        for (Rec r : new ArrayList<>(STORE.data().active.values())) {
            Entity carrier = LifeMca.find(server, UUID.fromString(r.carrier));
            if (!r.state.ended) {
                for (PregnancyPhases.Step st : PregnancyPhases.advance(r.state, now, params)) {
                    STORE.dirty();
                    if (st.event() == PregnancyPhases.Event.PHASE_CHANGED && st.phase() > 1) onPhase(server, r, carrier, st.phase());
                }
                if (r.state.ended) r.pending = true;
            }
            if (r.pending) {
                if (carrier == null || !carrier.isAlive()) continue; // waits until the carrier is back
                if ("birth".equals(r.state.outcome)) birth(server, r, carrier);
                else lose(server, r, carrier, "miscarriage");
                continue;
            }
            if (carrier instanceof LivingEntity le && le.isAlive()) applyEffects(le, r, now, sideTick, cfg);
        }
        if (server.getTickCount() % 100 == 0) cleanupEffects(server);
    }

    private static void onPhase(MinecraftServer server, Rec r, Entity carrier, int phase) {
        String name = PregnancyPhases.phaseName(phase, r.state.phases);
        tell(server, r, "You're now in your " + name + ".", r.carrierName + " is now in the " + name + ".");
        LifeEvents.remember(UUID.fromString(r.carrier), "pregnancy", "You are now in your " + name + " of pregnancy.");
        if (carrier instanceof CompanionEntity c) CompanionBrain.onEvent(c, "pregnancy", "You are now in your " + name
                + " of pregnancy and you can feel it. Mention it briefly, in character.");
    }

    private static void applyEffects(LivingEntity e, Rec r, long now, boolean sideTick, LifeConfig cfg) {
        if (!cfg.pregnancyEffects) {
            if (e.hasEffect(LifeRegistry.PREGNANT)) e.removeEffect(LifeRegistry.PREGNANT);
            return;
        }
        if (!e.hasEffect(LifeRegistry.PREGNANT)) {
            e.addEffect(new MobEffectInstance(LifeRegistry.PREGNANT, MobEffectInstance.INFINITE_DURATION, 0, true, false, true));
        }
        if (!sideTick || !cfg.pregnancySideEffects) return;
        int phase = PregnancyPhases.phase(r.state, now);
        double roll = RNG.nextDouble();
        Set<PregnancyPhases.Effect> fx = PregnancyPhases.effects(phase, r.state.phases, roll);
        int slow = PregnancyPhases.slownessLevel(phase, r.state.phases);
        if (slow >= 0) e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 640, slow, true, false, true));
        if (fx.contains(PregnancyPhases.Effect.HUNGER) && e instanceof Player && RNG.nextDouble() < 0.5) {
            e.addEffect(new MobEffectInstance(MobEffects.HUNGER, 300, 0, true, false, true));
        }
        if (fx.contains(PregnancyPhases.Effect.NAUSEA) && e instanceof Player p) {
            e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 160, 0, true, false, true));
            p.displayClientMessage(Component.literal("A wave of morning sickness...").withStyle(ChatFormatting.GRAY), true);
        }
        if (fx.contains(PregnancyPhases.Effect.FATIGUE)) e.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 640, 0, true, false, true));
        if (fx.contains(PregnancyPhases.Effect.MOOD_SWINGS) && cfg.pregnancyMoodSwings && e instanceof VillagerEntityMCA v) {
            v.getVillagerBrain().modifyMoodValue(RNG.nextBoolean() ? 4 : -4);
        }
    }

    /** Players (and loaded NPCs near them) with the Pregnant effect but no pregnancy lose it. */
    private static void cleanupEffects(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.hasEffect(LifeRegistry.PREGNANT) && !pregnant(p.getUUID())) p.removeEffect(LifeRegistry.PREGNANT);
            for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(64),
                    x -> (x instanceof VillagerEntityMCA || x instanceof CompanionEntity) && x.hasEffect(LifeRegistry.PREGNANT))) {
                if (!pregnant(e.getUUID())) e.removeEffect(LifeRegistry.PREGNANT);
            }
        }
    }

    // ---------------------------------------------------------------- endings

    private static void birth(MinecraftServer server, Rec r, Entity carrier) {
        STORE.data().active.remove(r.carrier);
        STORE.dirty();
        clearEffects(carrier);
        Entity partner = r.partner.isEmpty() ? null : LifeMca.find(server, UUID.fromString(r.partner));
        String text;
        if (r.nativeMca && carrier instanceof VillagerEntityMCA mother) {
            Pregnancy preg = mother.getRelationships().getPregnancy();
            preg.setPregnant(false);
            preg.setBabyAge(0);
            VillagerEntityMCA child = partner instanceof VillagerEntityMCA f ? preg.createChild(preg.getGender(), f) : preg.createChild(preg.getGender());
            child.setPos(mother.getX(), mother.getY(), mother.getZ());
            WorldUtils.spawnEntity(mother.level(), child, MobSpawnType.BREEDING);
            text = r.carrierName + " gave birth to " + child.getName().getString();
        } else {
            if (carrier instanceof VillagerEntityMCA v) v.getRelationships().getPregnancy().setPregnant(false);
            List<ItemStack> babies = new ArrayList<>();
            for (int i = 0; i < Math.max(1, r.babies); i++) {
                ItemStack s = makeBaby(carrier, partner, r, r.state.seed);
                if (!s.isEmpty()) babies.add(s);
            }
            Entity to = carrier instanceof Player ? carrier : partner instanceof Player ? partner : carrier;
            for (ItemStack s : babies) give(to, carrier, s);
            text = r.carrierName + " gave birth" + (babies.size() > 1 ? " to " + babies.size() + " babies" : "")
                    + (r.partnerName.isEmpty() ? "" : " (with " + r.partnerName + ")");
        }
        tell(server, r, "Your baby is here!" + (r.babies > 1 ? " (" + r.babies + " of them!)" : ""), r.carrierName + " gave birth to your baby!");
        LifeEvents.record(server, carrier, "birth", text, carrier.getUUID(), r.partner.isEmpty() ? null : UUID.fromString(r.partner));
        if (carrier instanceof VillagerEntityMCA v) v.getVillagerBrain().modifyMoodValue(20);
        if (carrier instanceof CompanionEntity c) CompanionBrain.onEvent(c, "birth", "You just gave birth to your baby. React in character.");
        if (partner instanceof CompanionEntity c && c != carrier) CompanionBrain.onEvent(c, "birth", r.carrierName + " just gave birth to your baby!");
    }

    /** Ends without a birth (miscarriage, death, or a command). */
    public static void lose(MinecraftServer server, Rec r, Entity carrier, String why) {
        STORE.data().active.remove(r.carrier);
        STORE.dirty();
        if (carrier != null) clearEffects(carrier);
        if (carrier instanceof VillagerEntityMCA v) {
            v.getRelationships().getPregnancy().setPregnant(false);
            v.getRelationships().getPregnancy().setBabyAge(0);
            if (!"ended".equals(why)) v.getVillagerBrain().modifyMoodValue(-25);
        }
        if ("ended".equals(why)) return;
        String text = "miscarriage".equals(why) ? r.carrierName + " lost the baby" : r.carrierName + "'s pregnancy ended (" + why + ")";
        tell(server, r, "miscarriage".equals(why) ? "You lost the baby. Take care of yourself." : "Your pregnancy has ended.",
                "miscarriage".equals(why) ? r.carrierName + " lost the baby." : r.carrierName + "'s pregnancy has ended.");
        LifeEvents.record(server, carrier, "loss", text, UUID.fromString(r.carrier), r.partner.isEmpty() ? null : UUID.fromString(r.partner));
        if (carrier instanceof CompanionEntity c && c.isAlive()) CompanionBrain.onEvent(c, "loss", "You have lost your baby. You are grieving.");
    }

    public static void onDeath(LivingEntity dead) {
        Rec r = get(dead.getUUID());
        if (r == null || dead.level().isClientSide) return;
        MinecraftServer server = ((ServerLevel) dead.level()).getServer();
        if (dead instanceof Player && !LifeConfig.get().playerPregnancyEndsOnDeath) return;
        lose(server, r, dead, "death");
    }

    private static void clearEffects(Entity e) {
        if (e instanceof LivingEntity le) {
            le.removeEffect(LifeRegistry.PREGNANT);
        }
    }

    private static void give(Entity to, Entity fallback, ItemStack s) {
        if (to instanceof Player p && p.getInventory().add(s)) return;
        if (to instanceof VillagerEntityMCA v && v.getInventory().addItem(s).isEmpty()) return;
        if (to instanceof CompanionEntity c && c.inventory.addItem(s).isEmpty()) return;
        Entity at = to != null ? to : fallback;
        at.level().addFreshEntity(new ItemEntity(at.level(), at.getX(), at.getY() + 0.5, at.getZ(), s));
    }

    /** MCA's baby item, with genes from both parents (companions through their MCA villager record). */
    static ItemStack makeBaby(Entity carrier, Entity partner, Rec r, long seed) {
        Entity mother = carrier, father = partner != null ? partner : carrier;
        try {
            Entity m = mother instanceof CompanionEntity ? (Entity) LifeMca.asVillager(mother) : mother;
            Entity f = father instanceof CompanionEntity ? (Entity) LifeMca.asVillager(father) : father;
            if (m == null) m = mother;
            if (f == null) f = m;
            BYPASS.set(true);
            ItemStack s;
            try {
                s = BabyItem.createItem(m, f, seed);
            } finally {
                BYPASS.set(false);
            }
            UUID fatherId = r.partner.isEmpty() ? carrier.getUUID() : UUID.fromString(r.partner);
            s.set(DataComponentsMCA.BABY_PARENTS, new BabyParentsComponent(carrier.getUUID(), fatherId, r.carrierName,
                    r.partnerName.isEmpty() ? r.carrierName : r.partnerName));
            return s;
        } catch (Throwable t) {
            TradeOfImmersion.LOGGER.error("[tradeofimmersion] Could not make the baby item for {}", r.carrierName, t);
            return ItemStack.EMPTY;
        }
    }

    /** Message for the carrier (when a player) and for the partner/owners who are players. */
    private static void tell(MinecraftServer server, Rec r, String toCarrier, String toOthers) {
        ServerPlayer c = server.getPlayerList().getPlayer(UUID.fromString(r.carrier));
        if (c != null) c.sendSystemMessage(Component.literal(toCarrier).withStyle(ChatFormatting.LIGHT_PURPLE));
        List<UUID> others = new ArrayList<>();
        if (!r.partner.isEmpty()) others.add(UUID.fromString(r.partner));
        Entity ce = LifeMca.find(server, UUID.fromString(r.carrier));
        if (ce instanceof CompanionEntity comp) comp.getOwnerId().ifPresent(others::add);
        if (!r.partner.isEmpty() && LifeMca.find(server, UUID.fromString(r.partner)) instanceof CompanionEntity pc) pc.getOwnerId().ifPresent(others::add);
        for (UUID o : others.stream().distinct().toList()) {
            ServerPlayer p = server.getPlayerList().getPlayer(o);
            if (p != null && p != c) p.sendSystemMessage(Component.literal(toOthers).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    // ---------------------------------------------------------------- read-outs

    /** 0..1 belly size for every loaded carrier, for the client sync. */
    public static Map<UUID, Float> bellies(MinecraftServer server) {
        Map<UUID, Float> m = new HashMap<>();
        if (!LifeConfig.get().on(LifeConfig.get().bellyVisual)) return m;
        long now = LifeMca.now(server);
        for (Rec r : STORE.data().active.values()) {
            if (r.state.ended) continue;
            float b = (float) PregnancyPhases.belly(PregnancyPhases.progress(r.state, now));
            if (b > 0.01f) m.put(UUID.fromString(r.carrier), Math.round(b * 50) / 50f);
        }
        return m;
    }

    /** One line for prompts and commands, or "" when not pregnant. */
    public static String describe(MinecraftServer server, UUID id, boolean secondPerson) {
        Rec r = get(id);
        if (r == null || r.state.ended) return "";
        long now = LifeMca.now(server);
        int phase = PregnancyPhases.phase(r.state, now);
        String who = secondPerson ? "You are" : r.carrierName + " is";
        StringBuilder sb = new StringBuilder(who).append(" pregnant");
        if (!r.partnerName.isEmpty()) sb.append(" (with ").append(r.partnerName).append("'s baby)");
        sb.append(", ").append(PregnancyPhases.phaseName(phase, r.state.phases)).append(", about ")
                .append(days(PregnancyPhases.ticksLeft(r.state, now))).append(" days to go");
        LifeConfig cfg = LifeConfig.get();
        if (cfg.pregnancyEffects && cfg.pregnancySideEffects) {
            Set<PregnancyPhases.Effect> fx = PregnancyPhases.effects(phase, r.state.phases, 0.5);
            List<String> feel = new ArrayList<>();
            if (fx.contains(PregnancyPhases.Effect.HUNGER)) feel.add("always hungry");
            if (fx.contains(PregnancyPhases.Effect.CRAVINGS)) feel.add("craving odd foods");
            if (phase == 1) feel.add("queasy in the mornings");
            if (fx.contains(PregnancyPhases.Effect.SLOWNESS)) feel.add("heavy and slow");
            if (fx.contains(PregnancyPhases.Effect.BACK_PAIN)) feel.add("an aching back");
            if (!feel.isEmpty()) sb.append("; ").append(secondPerson ? "you feel " : "feeling ").append(String.join(", ", feel));
        }
        if (cfg.pregnancyMoodSwings) sb.append("; mood swings - right now ").append(PregnancyPhases.moodSwing(r.state.seed, now));
        return sb.append('.').toString();
    }
}
