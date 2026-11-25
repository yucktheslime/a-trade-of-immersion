package dev.yucktheslime.tradeofimmersion.skills;

import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.TickEvent;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import dev.yucktheslime.tradeofimmersion.TradeOfImmersion;
import dev.yucktheslime.tradeofimmersion.entity.CompanionEntity;
import dev.yucktheslime.tradeofimmersion.entity.CompanionTask;
import dev.yucktheslime.tradeofimmersion.entity.Companions;
import dev.yucktheslime.tradeofimmersion.skills.item.ItemSkillStore;
import dev.yucktheslime.tradeofimmersion.skills.item.SkillNet;
import dev.yucktheslime.tradeofimmersion.skills.learn.HabitStore;
import dev.yucktheslime.tradeofimmersion.skills.learn.HabitTracker;
import dev.yucktheslime.tradeofimmersion.skills.travel.VehicleFinder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;

/**
 * Phase 3 (0.4.x): adaptive learning and jobs, mount and vehicle control, long trips, item learning from tooltips
 * and keys. Everything lives in this package; {@link #init} is called once from {@code TradeOfImmersion.init}.
 */
public final class Skills {
    private Skills() {
    }

    public static void init() {
        SkillsConfig.load();
        HabitTracker.init();
        SkillNet.init();
        if (Platform.getEnvironment() == Env.CLIENT) dev.yucktheslime.tradeofimmersion.skills.client.SkillsClient.init();
        CommandRegistrationEvent.EVENT.register((dispatcher, registry, selection) -> SkillsCommand.register(dispatcher));
        LifecycleEvent.SERVER_STARTED.register(server -> {
            HabitStore.get().open(server);
            ItemSkillStore.get().open(server);
        });
        LifecycleEvent.SERVER_LEVEL_SAVE.register(level -> {
            HabitStore.get().save();
            ItemSkillStore.get().save();
        });
        LifecycleEvent.SERVER_STOPPING.register(server -> {
            HabitStore.get().close();
            ItemSkillStore.get().close();
        });
        TickEvent.SERVER_POST.register(Skills::tick);
        TradeOfImmersion.LOGGER.info("[tradeofimmersion] skills: jobs={}, habits={}, mounts={}, long trips={} (>{} blocks), item learning={}",
                SkillsConfig.get().jobsEnabled, SkillsConfig.get().habitLearning, SkillsConfig.get().mountControl,
                SkillsConfig.get().longTravel, SkillsConfig.get().longTravelDistance, SkillsConfig.get().itemLearning);
    }

    private static int ticks;

    private static void tick(MinecraftServer server) {
        SkillNet.tick(server);
        if (++ticks % 40 != 0) return;
        SkillsConfig cfg = SkillsConfig.get();
        if (!cfg.mountControl || !cfg.mountWhenOwnerRides) return;
        for (CompanionEntity c : Companions.all(server)) {
            try {
                followByMount(c);
            } catch (Throwable ignored) {
            }
        }
    }

    /** The player rode off while the companion follows on foot: get on an allowed, steerable ride close by. */
    private static void followByMount(CompanionEntity c) {
        if (!"follow".equals(c.getMode()) || c.getTask() != null || c.isPassenger() || c.getTarget() != null) return;
        Player owner = c.getOwner();
        if (owner == null || !owner.isPassenger() || owner.distanceToSqr(c) < 10 * 10) return;
        VehicleFinder.Pick p = VehicleFinder.best(c, 16, 0, owner.position());
        if (p == null) return;
        c.setTask(new CompanionTask.Mount(p.vehicle()));
        SkillTask.event(c, "my player rode off, so I'm taking " + p.vehicle().getName().getString() + " (" + p.decision().reason() + ")");
    }
}
