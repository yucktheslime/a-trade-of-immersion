package dev.yucktheslime.tradeofimmersion.skills.mount;

import dev.yucktheslime.tradeofimmersion.entity.CompanionEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Drives one family of rideable things for a companion. Built in: horses/camels/donkeys/mules, pigs and striders,
 * minecarts, generic modded mounts and dragons (through their navigation). Boats stay with the companion's own boat
 * code. Other mods' vehicles (bikes, cars, planes) get an adapter registered with {@link VehicleAdapters#register}.
 */
public interface VehicleAdapter {
    /** Short id for logs and {@code /npcskills mounts}. */
    String id();

    boolean handles(Entity vehicle);

    /** True when this is a vehicle (not a living mount). */
    default boolean vehicle() {
        return false;
    }

    /**
     * Steer {@code vehicle} (ridden by {@code c}) one tick toward {@code dest}.
     *
     * @return false to let the companion's built-in steering handle it
     */
    boolean steer(CompanionEntity c, Entity vehicle, Vec3 dest, double speed);

    default void stop(CompanionEntity c, Entity vehicle) {
    }

    /** True for placeholder adapters that only push the vehicle and still need real per-mod work. */
    default boolean stub() {
        return false;
    }
}
