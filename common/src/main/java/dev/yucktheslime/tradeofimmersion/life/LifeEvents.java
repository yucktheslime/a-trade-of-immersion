package dev.yucktheslime.tradeofimmersion.life;

import dev.yucktheslime.tradeofimmersion.ai.KagrenacWorld;
import dev.yucktheslime.tradeofimmersion.entity.CompanionEntity;
import dev.yucktheslime.tradeofimmersion.memory.NpcMemoryStore;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Life events (marriages, engagements, pregnancies, births, deaths in the family, gifts, mood changes) become
 * memories for everyone involved (companions and villagers alike, in {@link NpcMemoryStore}), for companions who saw
 * it, a log-only Kagrenac world event, and a line for players nearby.
 */
public final class LifeEvents {
    public static final String TYPE = "life";

    private LifeEvents() {
    }

    public static void remember(UUID who, String type, String text) {
        NpcMemoryStore.get().event(who, type, text);
    }

    /**
     * @param where    where it happened (witnesses and players around it hear about it); may be null
     * @param involved the people it is about (they remember it even when far away)
     */
    public static void record(MinecraftServer server, Entity where, String type, String text, UUID... involved) {
        if (text == null || text.isBlank()) return;
        LifeConfig cfg = LifeConfig.get();
        List<UUID> told = new ArrayList<>();
        for (UUID id : involved) {
            if (id == null || told.contains(id)) continue;
            told.add(id);
            if (server.getPlayerList().getPlayer(id) == null) remember(id, type, text);
        }
        if (where != null) {
            for (CompanionEntity c : where.level().getEntitiesOfClass(CompanionEntity.class, where.getBoundingBox().inflate(cfg.lifeWitnessRadius))) {
                if (told.contains(c.getUUID())) continue;
                told.add(c.getUUID());
                remember(c.getUUID(), type, "You saw: " + text);
            }
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (p.level() == where.level() && p.distanceToSqr(where) < 48 * 48 && !"gift".equals(type) && !"mood".equals(type)) {
                    p.sendSystemMessage(Component.literal(text + ".").withStyle(ChatFormatting.LIGHT_PURPLE));
                }
            }
        }
        if (cfg.lifeEventsToKagrenac) {
            try {
                // Kagrenac's event.php already takes "relationship" and "world_note" log-only events (no server change needed)
                String kt = "romance".equals(type) || "gift".equals(type) || "jealousy".equals(type) ? "relationship" : "world_note";
                KagrenacWorld.event(kt, null, text, Map.of("kind", type));
            } catch (Throwable ignored) {
            }
        }
    }
}
