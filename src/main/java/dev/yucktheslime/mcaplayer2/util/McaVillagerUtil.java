package dev.yucktheslime.mcaplayer2.util;

import dev.yucktheslime.mcaplayer2.McaPlayer2Bridge;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Memories;
import net.conczin.mca.entity.ai.Traits;
import net.conczin.mca.entity.ai.brain.VillagerBrain;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Helpers for locating MCA villagers and reading their data. All direct calls into MCA Reborn
 * live here so an MCA API change only breaks one file.
 *
 * <p>Every MCA method used below was checked with {@code javap} against
 * {@code mca-fabric-7.7.36-beta.3+1.21.1.jar} (Modrinth version 7.7.36+1.21.1):
 * {@code VillagerEntityMCA#getRelationships().getGender()}, {@code VillagerLike#getAgeState()},
 * {@code VillagerLike#getProfessionId()}, {@code VillagerLike#getTraits().getTraits()},
 * {@code Traits.Trait#getId()}, {@code VillagerBrain#getPersonality().getId()},
 * {@code VillagerBrain#getMood().getName()}, {@code VillagerBrain#getMoodValue()},
 * {@code VillagerBrain#getMemories()} (map player UUID -> {@code Memories#getHearts()}),
 * {@code Messenger#sendChatToAllAround(MutableComponent)}, {@code Messenger#isToYoungToSpeak()},
 * {@code Messenger#isSpeechImpaired()} and {@code VillagerLike#isHostile()}.
 *
 * <p>Ids (not translated names) are used for personality/profession/traits because MCA's
 * translation keys are client assets and don't resolve on a dedicated server.
 */
public final class McaVillagerUtil {
    private McaVillagerUtil() {
    }

    /** Kept from the original scaffold API; now a real {@code instanceof VillagerEntityMCA} check. */
    public static Optional<LivingEntity> findNearestMcaVillager(LivingEntity origin, double radius) {
        if (origin == null || origin.level() == null) return Optional.empty();
        List<VillagerEntityMCA> list = villagersNear(origin, radius);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public static boolean isMcaVillager(Entity entity) {
        return entity instanceof VillagerEntityMCA;
    }

    /** Living MCA villagers within {@code radius} of {@code center}, nearest first (server side only). */
    public static List<VillagerEntityMCA> villagersNear(Entity center, double radius) {
        if (!(center.level() instanceof ServerLevel level)) return List.of();
        AABB box = center.getBoundingBox().inflate(radius);
        List<VillagerEntityMCA> list = new ArrayList<>(level.getEntitiesOfClass(VillagerEntityMCA.class, box,
                v -> v != center && v.isAlive() && v.distanceTo(center) <= radius));
        list.sort(Comparator.comparingDouble(v -> v.distanceToSqr(center)));
        return list;
    }

    /** Can this villager hold a conversation (old enough, not hostile, not speech impaired)? */
    public static boolean canTalk(VillagerEntityMCA v) {
        try {
            return v.isAlive() && !v.isToYoungToSpeak() && !v.isSpeechImpaired() && !v.isHostile();
        } catch (Throwable t) {
            return v.isAlive();
        }
    }

    public static String name(VillagerEntityMCA v) {
        return v.getName().getString();
    }

    /** Find a villager by UUID first, then by (case-insensitive) full name, prefix or first name, nearest first. */
    public static Optional<VillagerEntityMCA> find(MinecraftServer server, Entity near, String query, double searchRadius) {
        if (query == null || query.isBlank()) return Optional.empty();
        String q = query.trim().replace('_', ' ');
        try {
            UUID id = UUID.fromString(q);
            for (ServerLevel level : server.getAllLevels()) {
                Entity e = level.getEntity(id);
                if (e instanceof VillagerEntityMCA v) return Optional.of(v);
            }
            return Optional.empty();
        } catch (IllegalArgumentException notUuid) {
            // fall through to name search
        }
        String lq = q.toLowerCase(Locale.ROOT);
        List<VillagerEntityMCA> nearby = villagersNear(near, searchRadius);
        for (VillagerEntityMCA v : nearby) if (name(v).equalsIgnoreCase(q)) return Optional.of(v);
        for (VillagerEntityMCA v : nearby) if (name(v).toLowerCase(Locale.ROOT).startsWith(lq)) return Optional.of(v);
        for (VillagerEntityMCA v : nearby) {
            String first = name(v).split(" ")[0].toLowerCase(Locale.ROOT);
            if (first.length() >= 2 && lq.contains(first)) return Optional.of(v);
        }
        return Optional.empty();
    }

    /**
     * Reads MCA data for a villager.
     *
     * @param relationshipPlayer usually the companion's owner (MCA tracks hearts per player); may be null.
     */
    public static VillagerProfile profile(VillagerEntityMCA v, Player relationshipPlayer) {
        String gender = safe(() -> v.getRelationships().getGender().name().toLowerCase(Locale.ROOT), "");
        if (gender.equals("unassigned") || gender.equals("neutral")) gender = "";
        String age = safe(() -> v.getAgeState().name().toLowerCase(Locale.ROOT), "adult");
        String profession = safe(() -> prettyId(v.getProfessionId()), "none");
        VillagerBrain<?> brain = v.getVillagerBrain();
        String personality = safe(() -> prettyId(brain.getPersonality().getId()), "unknown");
        String mood = safe(() -> brain.getMood().getName(), "neutral");
        int moodValue = safe(brain::getMoodValue, 0);
        List<String> traits = new ArrayList<>();
        try {
            for (Traits.Trait t : v.getTraits().getTraits()) traits.add(prettyId(t.getId()));
        } catch (Throwable t) {
            McaPlayer2Bridge.LOGGER.debug("Could not read MCA traits", t);
        }
        Integer hearts = null;
        String playerName = null;
        if (relationshipPlayer != null) {
            playerName = relationshipPlayer.getName().getString();
            try {
                Memories m = brain.getMemories().get(relationshipPlayer.getUUID());
                if (m != null) hearts = m.getHearts();
            } catch (Throwable t) {
                McaPlayer2Bridge.LOGGER.debug("Could not read MCA hearts", t);
            }
        }
        return new VillagerProfile(v.getUUID(), name(v), gender, age, profession, personality, mood, moodValue,
                List.copyOf(traits), hearts, playerName);
    }

    /** Speak a line through MCA's own villager chat pipeline (MCA prefix + name, speech sound, players within 20 blocks). */
    public static void say(VillagerEntityMCA v, String line) {
        v.sendChatToAllAround(Component.literal(line));
    }

    public static void lookAt(VillagerEntityMCA v, Entity target) {
        v.getLookControl().setLookAt(target, 30.0F, 30.0F);
    }

    private static String prettyId(ResourceLocation id) {
        return id == null ? "none" : id.getPath().replace('_', ' ');
    }

    private interface Getter<T> {
        T get() throws Throwable;
    }

    private static <T> T safe(Getter<T> g, T fallback) {
        try {
            T t = g.get();
            return t != null ? t : fallback;
        } catch (Throwable t) {
            return fallback;
        }
    }
}
