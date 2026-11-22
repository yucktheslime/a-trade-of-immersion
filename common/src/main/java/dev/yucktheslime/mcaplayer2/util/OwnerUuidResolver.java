package dev.yucktheslime.mcaplayer2.util;

import net.minecraft.world.entity.Entity;

import java.util.Optional;
import java.util.UUID;

/**
 * Pluggable strategy that tells whether an entity is a tamed companion / owned animal and who owns it.
 *
 * <p>Register extra resolvers with {@link OwnedEntityUtil#registerResolver(OwnerUuidResolver)}; e.g. another
 * mod (or an optional adapter in this mod) can add support for pet systems that don't use vanilla
 * {@code OwnableEntity}. Resolvers are tried in registration order (built-ins first, the expensive NBT
 * fallback last); the first non-empty result wins.
 */
@FunctionalInterface
public interface OwnerUuidResolver {
    /**
     * @return the owner's UUID if {@code entity} is owned/tamed, else empty. Must not throw for unrelated
     * entities and should be cheap; the result is cached by {@link OwnedEntityUtil} for a short time.
     */
    Optional<UUID> resolveOwner(Entity entity);
}
