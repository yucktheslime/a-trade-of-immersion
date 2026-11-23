package dev.yucktheslime.tradeofimmersion.ai;

import dev.yucktheslime.tradeofimmersion.TradeOfImmersion;
import dev.yucktheslime.tradeofimmersion.config.ToiConfig;
import dev.yucktheslime.tradeofimmersion.entity.CompanionEntity;
import dev.yucktheslime.tradeofimmersion.util.McaVillagerUtil;
import dev.yucktheslime.tradeofimmersion.util.VillagerProfile;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What the game tells Kagrenac besides turns: log-only world events (event.php), companion and MCA villager
 * bios (npc_bio.php, shown on the dashboard's NPC bios page), and MCA family changes as relationship events.
 * Everything here is a no-op unless the active backend is Kagrenac.
 */
public final class KagrenacWorld {
    private static final Deque<Long> EVENT_TIMES = new ArrayDeque<>();
    private static final int MAX_EVENTS_PER_MINUTE = 40;
    /** npc id -> hash of the last bio pushed; npc id -> last family line seen. */
    private static final Map<String, Integer> PUSHED = new HashMap<>();
    private static final Map<String, String> FAMILY = new HashMap<>();
    private static final int BIO_INTERVAL_TICKS = 20 * 30;
    private static final double BIO_RADIUS = 64;

    private KagrenacWorld() {
    }

    public static KagrenacBackend kagrenac() {
        return CompanionBrain.backend() instanceof KagrenacBackend k ? k : null;
    }

    public static void reset() {
        synchronized (PUSHED) {
            PUSHED.clear();
            FAMILY.clear();
        }
    }

    // ------------------------------------------------------------------ events

    /**
     * A log-only event. {@code about} names the NPC it concerns, or null for "everyone nearby" (then every NPC of
     * this world sees it in its recent events). Rate limited; never makes a model call.
     */
    public static void event(String type, CompanionEntity about, String text) {
        event(type, about, text, null);
    }

    /** Same, with extra context (location, killer...). Queued and sent in the background (KagrenacOutbox). */
    public static void event(String type, CompanionEntity about, String text, Map<String, String> extra) {
        KagrenacBackend k = kagrenac();
        if (k == null || !ToiConfig.get().kagrenacLogEvents || text == null || text.isBlank()) return;
        synchronized (EVENT_TIMES) {
            long now = System.currentTimeMillis();
            while (!EVENT_TIMES.isEmpty() && now - EVENT_TIMES.peekFirst() > 60_000) EVENT_TIMES.pollFirst();
            if (EVENT_TIMES.size() >= MAX_EVENTS_PER_MINUTE) {
                dev.yucktheslime.tradeofimmersion.util.QuietLog.warn("kagrenac-event-rate", "More than {} world events in a minute; extra ones are not sent to Kagrenac", MAX_EVENTS_PER_MINUTE);
                return;
            }
            EVENT_TIMES.addLast(now);
        }
        Map<String, String> ctx = new java.util.LinkedHashMap<>();
        if (extra != null) ctx.putAll(extra);
        if (about != null) {
            ctx.put("location", about.blockPosition().toShortString() + " (" + about.level().dimension().location().getPath() + ")");
            k.logEvent(type, KagrenacBackend.companionId(about.getUUID().toString()), about.name(), text, ctx);
        } else {
            k.logEvent(type, null, null, text, ctx);
        }
    }

    // ------------------------------------------------------------------ bios

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % BIO_INTERVAL_TICKS != 7) return;
        KagrenacBackend k = kagrenac();
        if (k == null || !ToiConfig.get().kagrenacSyncBios) return;
        if (server.getTickCount() % (BIO_INTERVAL_TICKS * 10) == 7) k.refreshHealth(); // notice a server that came up later
        if (!k.has("bio_sync") || k.outbox.offline()) return; // unreachable: the event retries find out when it is back
        try {
            syncBios(server, k);
        } catch (Exception e) {
            TradeOfImmersion.LOGGER.debug("Bio sync failed", e);
        }
    }

    /** Pushes changed bios of companions and MCA villagers near players. Returns how many were sent. */
    public static int syncBios(MinecraftServer server, KagrenacBackend k) {
        List<KagrenacBackend.Bio> changed = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            List<ServerPlayer> players = level.players();
            if (players.isEmpty()) continue;
            for (Entity e : level.getAllEntities()) {
                if (changed.size() >= 32) break;
                if (!(e instanceof CompanionEntity) && !(e instanceof VillagerEntityMCA)) continue;
                if (!e.isAlive() || players.stream().noneMatch(p -> p.distanceToSqr(e) < BIO_RADIUS * BIO_RADIUS)) continue;
                KagrenacBackend.Bio bio = e instanceof CompanionEntity c ? companionBio(c) : villagerBio((VillagerEntityMCA) e);
                synchronized (PUSHED) {
                    Integer before = PUSHED.get(bio.id());
                    int hash = bio.hashCode();
                    if (before != null && before == hash) continue;
                    PUSHED.put(bio.id(), hash);
                }
                changed.add(bio);
            }
        }
        if (!changed.isEmpty()) {
            k.syncBios(changed).whenComplete((v, err) -> {
                if (err != null) {
                    dev.yucktheslime.tradeofimmersion.util.QuietLog.warn("kagrenac-bios", "Kagrenac bio sync failed: {}", (err.getCause() != null ? err.getCause() : err).getMessage());
                    synchronized (PUSHED) {
                        changed.forEach(b -> PUSHED.remove(b.id())); // try again next time
                    }
                } else {
                    TradeOfImmersion.LOGGER.debug("Kagrenac: pushed {} NPC bio(s) to the dashboard", changed.size());
                }
            });
        }
        return changed.size();
    }

    public static KagrenacBackend.Bio companionBio(CompanionEntity c) {
        String persona = c.getPersona().isBlank() ? "Friendly, curious and brave; loyal to " + c.getOwnerName() + ", a little playful." : c.getPersona();
        String bio = c.name() + " is a " + c.getGender() + " traveller in a Minecraft world with MCA villages, the companion of "
                + c.getOwnerName() + ". Personality: " + persona + " " + c.name()
                + " can follow, wait, walk somewhere, fight, ride boats and horses, equip, use, give and pick up items, inspect items to learn"
                + " what they do, and talk with villagers. A living person of this world, never an AI.";
        return new KagrenacBackend.Bio(KagrenacBackend.companionId(c.getUUID().toString()), c.name(), "Companion of " + c.getOwnerName(), bio);
    }

    public static KagrenacBackend.Bio villagerBio(VillagerEntityMCA v) {
        VillagerProfile p = McaVillagerUtil.profile(v, null);
        String family = McaVillagerUtil.family(v);
        String id = KagrenacBackend.villagerId(v.getUUID().toString());
        noteFamily(id, p.name(), family);
        StringBuilder b = new StringBuilder();
        b.append(p.name()).append(" is a").append("aeiou".indexOf(p.ageState().isEmpty() ? 'x' : p.ageState().charAt(0)) >= 0 ? "n " : " ")
                .append(p.ageState()).append(p.gender().isEmpty() ? "" : " " + p.gender()).append(" villager");
        if (!p.profession().isEmpty() && !p.profession().equals("none")) b.append(" working as a ").append(p.profession());
        b.append(" in a Minecraft village (MCA). Personality: ").append(p.personality()).append('.');
        if (!p.traits().isEmpty()) b.append(" Traits: ").append(String.join(", ", p.traits())).append('.');
        if (!family.isEmpty()) b.append(" Family: ").append(family).append('.');
        String role = p.profession().isEmpty() || p.profession().equals("none") ? "Villager" : cap(p.profession());
        return new KagrenacBackend.Bio(id, p.name(), role, b.toString());
    }

    /** A changed MCA family line (new partner, marriage, child) becomes a relationship event. */
    private static void noteFamily(String id, String name, String family) {
        String before;
        synchronized (PUSHED) {
            before = FAMILY.put(id, family);
        }
        if (before != null && !before.equals(family) && !family.isEmpty()) {
            event("relationship", null, name + "'s family changed: " + family + (before.isEmpty() ? "" : " (before: " + before + ")"));
        }
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }
}
