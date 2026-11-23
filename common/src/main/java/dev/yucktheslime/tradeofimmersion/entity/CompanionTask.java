package dev.yucktheslime.tradeofimmersion.entity;

import dev.yucktheslime.tradeofimmersion.memory.NpcMemoryStore;
import dev.yucktheslime.tradeofimmersion.villager.VillagerTalk;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;
import java.util.Comparator;
import java.util.List;

/** One multi-tick job a companion works on (walk somewhere, mount something, collect items, go talk to a villager). */
public abstract class CompanionTask {
    protected int ticks;
    protected final int timeout;

    protected CompanionTask(int timeoutTicks) {
        this.timeout = timeoutTicks;
    }

    /** @return true when finished. */
    public final boolean tick(CompanionEntity c) {
        if (++ticks > timeout) {
            onTimeout(c);
            return true;
        }
        return step(c);
    }

    protected abstract boolean step(CompanionEntity c);

    protected void onTimeout(CompanionEntity c) {
        taskEvent(c, describe() + " timed out");
    }

    public void stop(CompanionEntity c) {
        c.getNavigation().stop();
        c.stopVehicle();
    }

    /** Where the companion is heading (used to steer a vehicle while riding), or null. */
    @Nullable
    public Vec3 destination(CompanionEntity c) {
        return null;
    }

    public abstract String describe();

    protected static void walkTo(CompanionEntity c, Vec3 pos, double speed) {
        if (c.isPassenger()) return; // rideTick steers the vehicle using destination()
        if (c.tickCount % 10 == 0 || c.getNavigation().isDone()) c.getNavigation().moveTo(pos.x, pos.y, pos.z, speed);
    }

    // ------------------------------------------------------------------ goto

    public static class Goto extends CompanionTask {
        @Nullable
        private final Entity target;
        private final Vec3 pos;
        private final String label;

        public Goto(@Nullable Entity target, Vec3 pos, String label) {
            super(20 * 90);
            this.target = target;
            this.pos = pos;
            this.label = label;
        }

        @Override
        public Vec3 destination(CompanionEntity c) {
            return target != null && target.isAlive() ? target.position() : pos;
        }

        @Override
        protected boolean step(CompanionEntity c) {
            Vec3 dest = destination(c);
            double arrive = c.isPassenger() ? 4.0 : 2.5;
            if (c.position().distanceToSqr(dest) < arrive * arrive) {
                taskEvent(c, "arrived at " + label);
                return true;
            }
            walkTo(c, dest, 1.1);
            return false;
        }

        @Override
        public String describe() {
            return "going to " + label;
        }
    }

    // ------------------------------------------------------------------ mount

    public static class Mount extends CompanionTask {
        private final Entity vehicle;

        public Mount(Entity vehicle) {
            super(20 * 40);
            this.vehicle = vehicle;
        }

        @Override
        protected boolean step(CompanionEntity c) {
            if (!vehicle.isAlive()) return true;
            if (c.isPassenger()) c.stopRiding();
            if (c.distanceToSqr(vehicle) < 2.5 * 2.5) {
                boolean ok = c.startRiding(vehicle);
                if (!ok && vehicle.isAlive()) {
                    // some vehicles (boats in some states, modded seats) refuse mob riders; force it when a seat is free
                    int seats = vehicle instanceof net.minecraft.world.entity.vehicle.Boat b ? (b instanceof net.minecraft.world.entity.vehicle.ChestBoat ? 1 : 2) : 1;
                    if (vehicle.getPassengers().size() < seats) ok = c.startRiding(vehicle, true);
                }
                taskEvent(c, ok ? "mounted " + vehicle.getName().getString()
                        : "could not mount " + vehicle.getName().getString() + " (no free seat or it refuses riders)");
                return true;
            }
            walkTo(c, vehicle.position(), 1.1);
            return false;
        }

        @Override
        public void stop(CompanionEntity c) {
            c.getNavigation().stop();
        }

        @Override
        public String describe() {
            return "mounting " + vehicle.getName().getString();
        }
    }

    // ------------------------------------------------------------------ pick up

    public static class PickUp extends CompanionTask {
        private final double radius;
        private int collected;

        public PickUp(double radius) {
            super(20 * 30);
            this.radius = radius;
        }

        @Override
        protected boolean step(CompanionEntity c) {
            List<ItemEntity> items = c.level().getEntitiesOfClass(ItemEntity.class, c.getBoundingBox().inflate(radius),
                    e -> e.isAlive() && !e.getItem().isEmpty() && c.inventory.canAddItem(e.getItem()));
            if (items.isEmpty()) {
                taskEvent(c, "picked up " + collected + " item stack(s)");
                return true;
            }
            ItemEntity nearest = items.stream().min(Comparator.comparingDouble(c::distanceToSqr)).get();
            if (c.distanceToSqr(nearest) < 2.0 * 2.0) {
                ItemStack stack = nearest.getItem();
                int before = stack.getCount();
                ItemStack rest = c.inventory.addItem(stack.copy());
                c.take(nearest, before - rest.getCount());
                if (rest.isEmpty()) nearest.discard();
                else nearest.setItem(rest);
                collected++;
            } else {
                walkTo(c, nearest.position(), 1.1);
            }
            return false;
        }

        @Override
        protected void onTimeout(CompanionEntity c) {
            taskEvent(c, "picked up " + collected + " item stack(s) (stopped)");
        }

        @Override
        public String describe() {
            return "picking up items";
        }
    }

    // ------------------------------------------------------------------ talk to villager

    public static class TalkToVillager extends CompanionTask {
        private final VillagerEntityMCA villager;
        private final String message;

        public TalkToVillager(VillagerEntityMCA villager, String message) {
            super(20 * 60);
            this.villager = villager;
            this.message = message;
        }

        @Override
        public Vec3 destination(CompanionEntity c) {
            return villager.position();
        }

        @Override
        protected boolean step(CompanionEntity c) {
            if (!villager.isAlive()) return true;
            if (c.distanceToSqr(villager) < 3.5 * 3.5) {
                c.getNavigation().stop();
                c.getLookControl().setLookAt(villager);
                VillagerTalk.companionSpeaks(c, villager, message, true);
                return true;
            }
            walkTo(c, villager.position(), 1.0);
            return false;
        }

        @Override
        public String describe() {
            return "walking to " + villager.getName().getString() + " to talk";
        }
    }

    /** A task outcome: remembered locally and, with Kagrenac, logged as an action_result event. */
    static void taskEvent(CompanionEntity c, String text) {
        NpcMemoryStore.get().event(c.getUUID(), "task", text);
        dev.yucktheslime.tradeofimmersion.ai.KagrenacWorld.event("action_result", c, c.name() + ": " + text);
    }
}
