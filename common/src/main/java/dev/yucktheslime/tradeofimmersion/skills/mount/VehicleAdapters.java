package dev.yucktheslime.tradeofimmersion.skills.mount;

import dev.yucktheslime.tradeofimmersion.TradeOfImmersion;
import dev.yucktheslime.tradeofimmersion.action.Targets;
import dev.yucktheslime.tradeofimmersion.entity.CompanionEntity;
import dev.yucktheslime.tradeofimmersion.skills.SkillsConfig;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Strider;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/** Registry of {@link VehicleAdapter}s. Adapters registered by other code are asked before the built-in ones. */
public final class VehicleAdapters {
    private static final List<VehicleAdapter> CUSTOM = new CopyOnWriteArrayList<>();
    private static final List<VehicleAdapter> BUILT_IN = List.of(new Boats(), new Minecarts(), new Mobs(),
            // placeholders for vehicle mods: detected by entity id namespace, driven by a generic push for now
            new ModVehicleStub("immersive_vehicles", "mts", "immersive_vehicles"),
            new ModVehicleStub("automobility", "automobility"),
            new ModVehicleStub("ultimate_car_mod", "car"),
            new ModVehicleStub("mrcrayfish_vehicle_mod", "vehicle"),
            new ModVehicleStub("immersive_aircraft", "immersive_aircraft"),
            new ModVehicleStub("small_ships", "smallships"),
            new ModVehicleStub("generic_vehicle"));

    private VehicleAdapters() {
    }

    /** For addons: a real adapter for a vehicle mod. Asked before the built-in ones. */
    public static void register(VehicleAdapter adapter) {
        CUSTOM.add(0, adapter);
        TradeOfImmersion.LOGGER.info("[tradeofimmersion] vehicle adapter {} registered", adapter.id());
    }

    public static VehicleAdapter find(Entity vehicle) {
        for (VehicleAdapter a : CUSTOM) if (safeHandles(a, vehicle)) return a;
        for (VehicleAdapter a : BUILT_IN) if (safeHandles(a, vehicle)) return a;
        return null;
    }

    public static List<VehicleAdapter> all() {
        List<VehicleAdapter> out = new java.util.ArrayList<>(CUSTOM);
        out.addAll(BUILT_IN);
        return out;
    }

    private static boolean safeHandles(VehicleAdapter a, Entity e) {
        try {
            return a.handles(e);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Called from {@code CompanionEntity.steerVehicleTo}. @return true when an adapter steered (the entity's own code
     * then does nothing).
     */
    public static boolean steer(CompanionEntity c, Entity vehicle, Vec3 dest, double speed) {
        if (!SkillsConfig.get().mountControl) return false;
        VehicleAdapter a = find(vehicle);
        if (a == null) return false;
        try {
            return a.steer(c, vehicle, dest, speed);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * A point on the way to {@code dest} that pathfinding can reach (paths are limited to about the follow range).
     * Far destinations get a waypoint {@code step} blocks along the straight line, on the ground surface.
     */
    public static Vec3 waypoint(Entity mover, Vec3 dest, double step) {
        Vec3 from = mover.position();
        double dx = dest.x - from.x, dz = dest.z - from.z;
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d <= step) return dest;
        double x = from.x + dx / d * step, z = from.z + dz / d * step;
        int y = mover.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
        if (y <= mover.level().getMinBuildHeight()) y = Mth.floor(from.y);
        return new Vec3(x, y, z);
    }

    public static double pathStep(Mob m) {
        double range = m.getAttributes().hasAttribute(Attributes.FOLLOW_RANGE) ? m.getAttributeValue(Attributes.FOLLOW_RANGE) : 16;
        return Mth.clamp(range * 0.75, 10, 40);
    }

    // ------------------------------------------------------------------ built-in adapters

    /** Boats keep the companion's own boat steering (CompanionEntity.steerVehicleTo). */
    static final class Boats implements VehicleAdapter {
        public String id() {
            return "boat";
        }

        public boolean handles(Entity v) {
            return v instanceof Boat;
        }

        public boolean vehicle() {
            return true;
        }

        public boolean steer(CompanionEntity c, Entity v, Vec3 dest, double speed) {
            return false;
        }
    }

    /** Minecarts only go along rails: push them the way that gets closer, brake near the end. */
    static final class Minecarts implements VehicleAdapter {
        public String id() {
            return "minecart";
        }

        public boolean handles(Entity v) {
            return v instanceof AbstractMinecart;
        }

        public boolean vehicle() {
            return true;
        }

        public boolean steer(CompanionEntity c, Entity v, Vec3 dest, double speed) {
            Vec3 d = new Vec3(dest.x - v.getX(), 0, dest.z - v.getZ());
            double dist = d.length();
            Vec3 m = v.getDeltaMovement();
            if (dist < 3) {
                v.setDeltaMovement(m.scale(0.6));
                return true;
            }
            double h = Math.sqrt(m.x * m.x + m.z * m.z);
            if (h < 0.25) {
                Vec3 dir = d.normalize().scale(0.05);
                v.setDeltaMovement(m.add(dir.x, 0, dir.z)); // moveAlongTrack projects this onto the rail
            }
            c.setYRot(v.getYRot());
            return true;
        }

        public void stop(CompanionEntity c, Entity v) {
            v.setDeltaMovement(v.getDeltaMovement().scale(0.5));
        }
    }

    /** Living mounts (horses, camels, pigs, striders, dragons, modded mobs): drive their own pathfinding by waypoints. */
    static final class Mobs implements VehicleAdapter {
        public String id() {
            return "mob";
        }

        public boolean handles(Entity v) {
            return v instanceof Mob && !(v instanceof CompanionEntity);
        }

        public boolean steer(CompanionEntity c, Entity v, Vec3 dest, double speed) {
            Mob m = (Mob) v;
            if ((v instanceof Pig || v instanceof Strider) && SkillsConfig.get().requireSteerItem) {
                boolean stick = MountClassifier.has(c, v instanceof Pig ? net.minecraft.world.item.Items.CARROT_ON_A_STICK : net.minecraft.world.item.Items.WARPED_FUNGUS_ON_A_STICK);
                if (!stick) return true; // can't steer: just sit
            }
            if (c.tickCount % 10 == 0 || m.getNavigation().isDone()) {
                Vec3 wp = waypoint(m, dest, pathStep(m));
                boolean ok = m.getNavigation().moveTo(wp.x, wp.y, wp.z, speed);
                if (!ok) {
                    // no path (water, cliffs, flying mounts with ground navigation): nudge it straight there
                    Vec3 dir = new Vec3(wp.x - m.getX(), 0, wp.z - m.getZ()).normalize();
                    m.getMoveControl().setWantedPosition(m.getX() + dir.x * 4, wp.y, m.getZ() + dir.z * 4, speed);
                }
            }
            c.setYRot(v.getYRot());
            return true;
        }

        public void stop(CompanionEntity c, Entity v) {
            ((Mob) v).getNavigation().stop();
        }
    }

    /**
     * Placeholder for a vehicle mod (bikes, cars, planes, ships). Detected by entity id namespace; driven by pushing it
     * toward the destination, which only works for vehicles that keep their motion. A real adapter (the mod's own input
     * fields) replaces it via {@link #register}.
     */
    static final class ModVehicleStub implements VehicleAdapter {
        private final String id;
        private final String[] namespaces;

        ModVehicleStub(String id, String... namespaces) {
            this.id = id;
            this.namespaces = namespaces;
        }

        public String id() {
            return id + " (stub)";
        }

        public boolean stub() {
            return true;
        }

        public boolean vehicle() {
            return true;
        }

        public boolean handles(Entity v) {
            if (v instanceof Mob || v instanceof Boat || v instanceof AbstractMinecart) return false;
            String tid = Targets.typeId(v).toLowerCase(Locale.ROOT);
            String ns = tid.substring(0, tid.indexOf(':'));
            if (namespaces.length == 0) {
                // generic: any non-living entity that looks like a vehicle
                if (v instanceof net.minecraft.world.entity.LivingEntity) return false;
                String p = tid.substring(tid.indexOf(':') + 1);
                return p.contains("car") || p.contains("bike") || p.contains("vehicle") || p.contains("plane") || p.contains("truck")
                        || p.contains("motor") || p.contains("scooter") || p.contains("kart") || p.contains("ship") || p.contains("boat");
            }
            for (String n : namespaces) if (ns.equals(n)) return true;
            return false;
        }

        public boolean steer(CompanionEntity c, Entity v, Vec3 dest, double speed) {
            Vec3 d = new Vec3(dest.x - v.getX(), 0, dest.z - v.getZ());
            double dist = d.length();
            if (dist < 3) return true;
            float desired = (float) (Mth.atan2(d.z, d.x) * (180.0 / Math.PI)) - 90.0f;
            float diff = Mth.wrapDegrees(desired - v.getYRot());
            v.setYRot(v.getYRot() + Mth.clamp(diff, -6f, 6f));
            if (Math.abs(diff) < 60) {
                Vec3 dir = d.normalize().scale(Math.min(0.35, 0.1 * speed + 0.15));
                Vec3 m = v.getDeltaMovement();
                v.setDeltaMovement(dir.x, m.y, dir.z);
            }
            c.setYRot(v.getYRot());
            return true;
        }
    }
}
