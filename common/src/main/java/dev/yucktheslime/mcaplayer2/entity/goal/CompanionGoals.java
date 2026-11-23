package dev.yucktheslime.mcaplayer2.entity.goal;

import dev.yucktheslime.mcaplayer2.config.BridgeConfig;
import dev.yucktheslime.mcaplayer2.entity.CompanionEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/** Goal setup: flee when weak, melee, run the current task, follow owner, defend owner, fight nearby hostiles. */
public final class CompanionGoals {
    private CompanionGoals() {
    }

    public static void register(CompanionEntity c, GoalSelector goals, GoalSelector targets) {
        goals.addGoal(0, new FloatGoal(c));
        goals.addGoal(1, new FleeWhenWeakGoal(c));
        goals.addGoal(2, new MeleeAttackGoal(c, 1.25, true) {
            @Override
            public boolean canUse() {
                return !c.isPassenger() && super.canUse();
            }
        });
        goals.addGoal(3, new TaskGoal(c));
        goals.addGoal(4, new FollowOwnerGoal(c));
        goals.addGoal(5, new OpenDoorGoal(c, true));
        goals.addGoal(7, new WaterAvoidingRandomStrollGoal(c, 0.8) {
            @Override
            public boolean canUse() {
                return "wander".equals(c.getMode()) && c.getTask() == null && super.canUse();
            }
        });
        goals.addGoal(8, new LookAtPlayerGoal(c, Player.class, 8.0f));
        goals.addGoal(9, new RandomLookAroundGoal(c));

        targets.addGoal(0, new CommandedTargetGoal(c));
        targets.addGoal(1, new OwnerDefenseGoal(c, true));
        targets.addGoal(2, new OwnerDefenseGoal(c, false));
        targets.addGoal(3, new HurtByTargetGoal(c) {
            @Override
            protected boolean canAttack(LivingEntity target, TargetingConditions cond) {
                return !c.isOwner(target) && super.canAttack(target, cond);
            }
        });
        targets.addGoal(4, new NearestAttackableTargetGoal<>(c, LivingEntity.class, 10, true, false,
                e -> e instanceof Enemy && !(e instanceof Creeper) && BridgeConfig.get().defendOwner && c.distanceToSqr(e) < 12 * 12));
    }

    /** Runs the companion's current {@link dev.yucktheslime.mcaplayer2.entity.CompanionTask} (it ticks in the entity; this goal just owns MOVE). */
    static class TaskGoal extends Goal {
        private final CompanionEntity c;

        TaskGoal(CompanionEntity c) {
            this.c = c;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return c.getTask() != null;
        }
    }

    static class FollowOwnerGoal extends Goal {
        private final CompanionEntity c;
        private Player owner;
        private int recalc;

        FollowOwnerGoal(CompanionEntity c) {
            this.c = c;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!"follow".equals(c.getMode()) || c.getTask() != null || c.isPassenger()) return false;
            owner = c.getOwner();
            return owner != null && !owner.isSpectator() && c.distanceToSqr(owner) > 4 * 4;
        }

        @Override
        public boolean canContinueToUse() {
            return owner != null && owner.isAlive() && c.getTask() == null && !c.isPassenger() && "follow".equals(c.getMode())
                    && c.distanceToSqr(owner) > 2.5 * 2.5;
        }

        @Override
        public void tick() {
            c.getLookControl().setLookAt(owner, 10f, c.getMaxHeadXRot());
            double tp = BridgeConfig.get().followTeleportDistance;
            if (tp > 0 && c.distanceToSqr(owner) > tp * tp && owner.onGround() && !owner.isPassenger()) {
                c.moveTo(owner.getX(), owner.getY(), owner.getZ(), c.getYRot(), c.getXRot());
                c.getNavigation().stop();
                return;
            }
            if (--recalc <= 0) {
                recalc = 10;
                c.getNavigation().moveTo(owner, c.distanceToSqr(owner) > 12 * 12 ? 1.3 : 1.0);
            }
        }

        @Override
        public void stop() {
            c.getNavigation().stop();
            owner = null;
        }
    }

    static class FleeWhenWeakGoal extends Goal {
        private final CompanionEntity c;
        private Vec3 away;

        FleeWhenWeakGoal(CompanionEntity c) {
            this.c = c;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        private LivingEntity threat() {
            LivingEntity t = c.getLastHurtByMob();
            if (t == null) t = c.getTarget();
            return t != null && t.isAlive() && c.distanceToSqr(t) < 12 * 12 ? t : null;
        }

        @Override
        public boolean canUse() {
            if (c.isPassenger() || c.getHealth() > c.getMaxHealth() * BridgeConfig.get().fleeHealthFraction) return false;
            LivingEntity t = threat();
            if (t == null) return false;
            away = DefaultRandomPos.getPosAway(c, 16, 7, t.position());
            return away != null;
        }

        @Override
        public void start() {
            c.setTarget(null);
            c.getNavigation().moveTo(away.x, away.y, away.z, 1.4);
            c.eatSomething();
        }

        @Override
        public boolean canContinueToUse() {
            return !c.getNavigation().isDone() && threat() != null;
        }
    }

    static class CommandedTargetGoal extends TargetGoal {
        private final CompanionEntity c;

        CommandedTargetGoal(CompanionEntity c) {
            super(c, false, false);
            this.c = c;
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            return c.getCommandedTarget() != null;
        }

        @Override
        public void start() {
            c.setTarget(c.getCommandedTarget());
            super.start();
        }

        @Override
        public boolean canContinueToUse() {
            return c.getCommandedTarget() != null;
        }
    }

    /** {@code attacker=true}: target whoever hurt the owner; false: whatever the owner attacked. */
    static class OwnerDefenseGoal extends TargetGoal {
        private final CompanionEntity c;
        private final boolean attacker;
        private LivingEntity candidate;
        private int lastStamp;

        OwnerDefenseGoal(CompanionEntity c, boolean attacker) {
            super(c, false);
            this.c = c;
            this.attacker = attacker;
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            if (!BridgeConfig.get().defendOwner) return false;
            Player owner = c.getOwner();
            if (owner == null) return false;
            candidate = attacker ? owner.getLastHurtByMob() : owner.getLastHurtMob();
            int stamp = attacker ? owner.getLastHurtByMobTimestamp() : owner.getLastHurtMobTimestamp();
            return candidate != null && stamp != lastStamp && candidate != c && !c.isAlliedTo(candidate)
                    && canAttack(candidate, TargetingConditions.DEFAULT);
        }

        @Override
        public void start() {
            c.setTarget(candidate);
            Player owner = c.getOwner();
            if (owner != null) lastStamp = attacker ? owner.getLastHurtByMobTimestamp() : owner.getLastHurtMobTimestamp();
            super.start();
        }
    }
}
