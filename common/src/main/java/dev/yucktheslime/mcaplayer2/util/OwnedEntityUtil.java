package dev.yucktheslime.mcaplayer2.util;

import dev.yucktheslime.mcaplayer2.McaPlayer2Bridge;
import dev.yucktheslime.mcaplayer2.config.BridgeConfig;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * Generic detection of tamed companions / owned animals and resolution of their owner to an MCA villager.
 * Works with anything that exposes an owner UUID - vanilla tameables, horses, and modded pets - without
 * referencing any specific mod.
 *
 * <p>Built-in resolvers, in order:
 * <ol>
 *   <li>Vanilla {@link OwnableEntity#getOwnerUUID()} (wolves, cats, parrots, horses, and any modded mob
 *       implementing it).</li>
 *   <li>Reflection: a public no-arg method returning {@link UUID} named one of
 *       {@code getOwnerUUID / getOwnerUuid / getOwnerId / getOwner}, on the entity itself or on an object
 *       returned by one of the accessor methods listed in config {@code ownerAccessorMethods}
 *       (for mods that keep pet data in a separate object).</li>
 *   <li>NBT fallback: saves the entity's data and looks for an owner UUID under common keys
 *       ({@code ownerNbtKeys} in config, default {@code Owner, OwnerUUID, owner, ownerUUID, OwnerUuid, ownerUuid}),
 *       at the top level or one compound deep. Stored as an int-array UUID or a UUID string.</li>
 * </ol>
 */
public final class OwnedEntityUtil {
    private static final List<OwnerUuidResolver> RESOLVERS = new CopyOnWriteArrayList<>();
    private static final List<OwnerUuidResolver> EXTRA_RESOLVERS = new CopyOnWriteArrayList<>();
    private static final String[] OWNER_METHOD_NAMES = {"getOwnerUUID", "getOwnerUuid", "getOwnerId", "getOwner"};
    private static final Map<Class<?>, Optional<Method>> METHOD_CACHE = new ConcurrentHashMap<>();
    private static final Map<UUID, Cached> RESULT_CACHE = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_TICKS = 200;

    static {
        RESOLVERS.add(OwnedEntityUtil::vanillaOwnable);
        RESOLVERS.add(OwnedEntityUtil::reflectiveOwnerMethod);
    }

    private OwnedEntityUtil() {
    }

    /** Add a resolver (tried after the built-in vanilla/reflection resolvers, before the NBT fallback). */
    public static void registerResolver(OwnerUuidResolver resolver) {
        EXTRA_RESOLVERS.add(resolver);
    }

    /** How an owner UUID was matched. */
    public enum OwnerKind { MCA_VILLAGER, MCA_VILLAGER_GUESSED, PLAYER, UNKNOWN }

    /**
     * An owned entity and its resolved owner.
     *
     * @param ownerName villager/player name, or null when {@link OwnerKind#UNKNOWN}
     */
    public record OwnedEntity(LivingEntity entity, UUID ownerUuid, OwnerKind kind, VillagerEntityMCA ownerVillager, String ownerName) {
        public String typeName() {
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
            return id == null ? "creature" : id.getPath().replace('_', ' ');
        }

        public String customName() {
            return entity.hasCustomName() && entity.getCustomName() != null ? entity.getCustomName().getString() : null;
        }

        /** e.g. {@code wolf "Rex"} or {@code cat}. */
        public String label() {
            String n = customName();
            return n == null ? typeName() : typeName() + " \"" + n + "\"";
        }
    }

    /** Owner UUID of {@code entity} if it is a tamed companion / owned animal (cached briefly). */
    public static Optional<UUID> ownerOf(Entity entity) {
        if (entity == null || entity instanceof Player || entity instanceof VillagerEntityMCA) return Optional.empty();
        long now = entity.level().getGameTime();
        Cached c = RESULT_CACHE.get(entity.getUUID());
        if (c != null && now - c.tick < CACHE_TTL_TICKS) return c.owner;
        Optional<UUID> result = Optional.empty();
        for (OwnerUuidResolver r : RESOLVERS) {
            result = safe(r, entity);
            if (result.isPresent()) break;
        }
        if (result.isEmpty()) for (OwnerUuidResolver r : EXTRA_RESOLVERS) {
            result = safe(r, entity);
            if (result.isPresent()) break;
        }
        if (result.isEmpty() && BridgeConfig.get().ownerNbtFallback) result = safe(OwnedEntityUtil::nbtFallback, entity);
        if (RESULT_CACHE.size() > 4096) RESULT_CACHE.clear();
        RESULT_CACHE.put(entity.getUUID(), new Cached(now, result));
        return result;
    }

    /** All owned entities within {@code radius} of {@code center}, nearest first, with owners resolved. */
    public static List<OwnedEntity> findOwnedNear(Entity center, double radius, Predicate<LivingEntity> exclude) {
        if (!(center.level() instanceof ServerLevel level)) return List.of();
        MinecraftServer server = level.getServer();
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, center.getBoundingBox().inflate(radius),
                e -> e != center && e.isAlive() && !(e instanceof Player) && !(e instanceof VillagerEntityMCA)
                        && (exclude == null || !exclude.test(e)));
        candidates.sort(Comparator.comparingDouble(e -> e.distanceToSqr(center)));
        List<OwnedEntity> out = new ArrayList<>();
        int max = Math.max(1, BridgeConfig.get().maxOwnedEntitiesInPrompt);
        for (LivingEntity e : candidates) {
            if (out.size() >= max) break;
            Optional<UUID> owner = ownerOf(e);
            owner.ifPresent(uuid -> out.add(resolveOwner(server, e, uuid)));
        }
        return out;
    }

    /**
     * Resolve an owner UUID: exact UUID match against loaded MCA villagers first, then online players,
     * then (only if {@code guessOwnerFromNearestVillager} is on) the nearest MCA villager, else UNKNOWN.
     */
    public static OwnedEntity resolveOwner(MinecraftServer server, LivingEntity entity, UUID ownerUuid) {
        for (ServerLevel l : server.getAllLevels()) {
            Entity owner = l.getEntity(ownerUuid);
            if (owner instanceof VillagerEntityMCA v) {
                return new OwnedEntity(entity, ownerUuid, OwnerKind.MCA_VILLAGER, v, McaVillagerUtil.name(v));
            }
        }
        ServerPlayer p = server.getPlayerList().getPlayer(ownerUuid);
        if (p != null) return new OwnedEntity(entity, ownerUuid, OwnerKind.PLAYER, null, p.getName().getString());
        if (BridgeConfig.get().guessOwnerFromNearestVillager) {
            List<VillagerEntityMCA> near = McaVillagerUtil.villagersNear(entity, 16);
            if (!near.isEmpty()) {
                VillagerEntityMCA v = near.get(0);
                return new OwnedEntity(entity, ownerUuid, OwnerKind.MCA_VILLAGER_GUESSED, v, McaVillagerUtil.name(v));
            }
        }
        return new OwnedEntity(entity, ownerUuid, OwnerKind.UNKNOWN, null, null);
    }

    // ------------------------------------------------------------------ built-in resolvers

    private static Optional<UUID> vanillaOwnable(Entity e) {
        return e instanceof OwnableEntity o ? Optional.ofNullable(o.getOwnerUUID()) : Optional.empty();
    }

    private static Optional<UUID> reflectiveOwnerMethod(Entity e) {
        Optional<UUID> direct = invokeOwnerMethod(e);
        if (direct.isPresent()) return direct;
        for (String accessor : BridgeConfig.get().ownerAccessorMethods) {
            try {
                Method m = e.getClass().getMethod(accessor);
                if (m.getParameterCount() != 0 || Modifier.isStatic(m.getModifiers())) continue;
                Object holder = m.invoke(e);
                if (holder != null) {
                    Optional<UUID> nested = invokeOwnerMethod(holder);
                    if (nested.isPresent()) return nested;
                }
            } catch (NoSuchMethodException ignored) {
                // accessor not present on this entity type
            } catch (Throwable t) {
                McaPlayer2Bridge.LOGGER.debug("Owner accessor {} failed on {}", accessor, e.getClass().getName(), t);
            }
        }
        return Optional.empty();
    }

    private static Optional<UUID> invokeOwnerMethod(Object target) {
        Optional<Method> method = METHOD_CACHE.computeIfAbsent(target.getClass(), cls -> {
            for (String name : OWNER_METHOD_NAMES) {
                try {
                    Method m = cls.getMethod(name);
                    if (m.getReturnType() == UUID.class && !Modifier.isStatic(m.getModifiers())) return Optional.of(m);
                } catch (NoSuchMethodException ignored) {
                    // try next name
                }
            }
            return Optional.empty();
        });
        if (method.isEmpty()) return Optional.empty();
        try {
            return Optional.ofNullable((UUID) method.get().invoke(target));
        } catch (Throwable t) {
            return Optional.empty();
        }
    }

    private static Optional<UUID> nbtFallback(Entity e) {
        CompoundTag tag = e.saveWithoutId(new CompoundTag());
        Optional<UUID> top = uuidFromKeys(tag);
        if (top.isPresent()) return top;
        for (String key : tag.getAllKeys()) {
            if (tag.getTagType(key) == Tag.TAG_COMPOUND) {
                Optional<UUID> nested = uuidFromKeys(tag.getCompound(key));
                if (nested.isPresent()) return nested;
            }
        }
        return Optional.empty();
    }

    private static Optional<UUID> uuidFromKeys(CompoundTag tag) {
        for (String key : BridgeConfig.get().ownerNbtKeys) {
            if (!tag.contains(key)) continue;
            if (tag.hasUUID(key)) return Optional.of(tag.getUUID(key));
            if (tag.getTagType(key) == Tag.TAG_STRING) {
                try {
                    return Optional.of(UUID.fromString(tag.getString(key)));
                } catch (IllegalArgumentException ignored) {
                    // a name, not a UUID
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<UUID> safe(OwnerUuidResolver r, Entity e) {
        try {
            Optional<UUID> o = r.resolveOwner(e);
            return o == null ? Optional.empty() : o;
        } catch (Throwable t) {
            return Optional.empty();
        }
    }

    private record Cached(long tick, Optional<UUID> owner) {
    }
}
