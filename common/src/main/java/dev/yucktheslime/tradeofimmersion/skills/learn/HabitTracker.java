package dev.yucktheslime.tradeofimmersion.skills.learn;

import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.BlockEvent;
import dev.architectury.event.events.common.InteractionEvent;
import dev.yucktheslime.tradeofimmersion.entity.CompanionEntity;
import dev.yucktheslime.tradeofimmersion.memory.NpcMemoryStore;
import dev.yucktheslime.tradeofimmersion.skills.SkillTask;
import dev.yucktheslime.tradeofimmersion.skills.SkillsConfig;
import dev.yucktheslime.tradeofimmersion.skills.job.BlockWork;
import dev.yucktheslime.tradeofimmersion.skills.job.ChopTask;
import dev.yucktheslime.tradeofimmersion.skills.job.FarmTask;
import dev.yucktheslime.tradeofimmersion.skills.job.MineTask;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Watches what real players do (harvest, plant, chop, mine, dig, build) and feeds their {@link HabitModel}. When a
 * player does something they often do, their idle following companions nearby join in (farm, chop or mine the same
 * thing around there), at most once per cooldown.
 */
public final class HabitTracker {
    private static final Map<UUID, Long> LAST_HELP = new HashMap<>();

    private HabitTracker() {
    }

    public static void init() {
        BlockEvent.BREAK.register((level, pos, state, player, xp) -> {
            try {
                onBreak(level, pos, state, player);
            } catch (Throwable ignored) {
                // never block a break
            }
            return EventResult.pass();
        });
        BlockEvent.PLACE.register((level, pos, state, entity) -> {
            try {
                if (entity instanceof ServerPlayer p && real(p)) observe(p, isPlanting(state) ? "farm" : "build", BlockWork.id(state), pos);
            } catch (Throwable ignored) {
            }
            return EventResult.pass();
        });
        InteractionEvent.RIGHT_CLICK_BLOCK.register((player, hand, pos, face) -> {
            try {
                if (player instanceof ServerPlayer p && real(p)) {
                    ItemStack held = p.getItemInHand(hand);
                    if (held.getItem() instanceof HoeItem && p.level().getBlockState(pos).is(BlockTags.DIRT)) observe(p, "farm", "till", pos);
                }
            } catch (Throwable ignored) {
            }
            return EventResult.pass();
        });
    }

    /** Fake players (ours and other mods') don't teach habits. */
    static boolean real(ServerPlayer p) {
        return p.connection != null && p.getClass() == ServerPlayer.class;
    }

    static boolean isPlanting(BlockState st) {
        return st.is(BlockTags.CROPS) || st.is(BlockTags.SAPLINGS);
    }

    private static void onBreak(Level level, BlockPos pos, BlockState st, Player player) {
        if (!(player instanceof ServerPlayer p) || !real(p) || p.isCreative()) return;
        String activity;
        if (FarmTask.ripe(st)) activity = "farm";
        else if (st.is(BlockTags.LOGS)) activity = "chop";
        else if (st.is(BlockTags.MINEABLE_WITH_PICKAXE)) activity = "mine";
        else if (st.is(BlockTags.MINEABLE_WITH_SHOVEL)) activity = "dig";
        else return;
        observe(p, activity, BlockWork.id(st), pos);
    }

    static void observe(ServerPlayer p, String activity, String subject, BlockPos pos) {
        SkillsConfig cfg = SkillsConfig.get();
        if (!cfg.habitLearning || !(p.level() instanceof ServerLevel level)) return;
        long tick = level.getGameTime();
        double hl = Math.max(0.1, cfg.habitHalfLifeDays) * 24000.0;
        HabitModel m = HabitStore.get().of(p.getUUID());
        boolean wasHabit = m.isHabit(activity, tick, hl, cfg.habitThreshold);
        m.observe(activity, subject, tick, hl, pos.getX(), pos.getY(), pos.getZ(), level.dimension().location().toString());
        if (!wasHabit && m.isHabit(activity, tick, hl, cfg.habitThreshold)) {
            for (CompanionEntity c : companionsOf(p, 48)) {
                NpcMemoryStore.get().event(c.getUUID(), "habit", p.getGameProfile().getName() + " often " + verb(activity) + " (" + pretty(subject) + ")");
            }
        }
        if (cfg.habitAutoHelp && cfg.jobsEnabled && m.isHabit(activity, tick, hl, cfg.habitThreshold)) maybeHelp(p, activity, subject, pos);
    }

    static Iterable<CompanionEntity> companionsOf(ServerPlayer p, double r) {
        return p.level().getEntitiesOfClass(CompanionEntity.class, p.getBoundingBox().inflate(r), c -> c.isAlive() && c.isOwner(p));
    }

    private static void maybeHelp(ServerPlayer p, String activity, String subject, BlockPos pos) {
        if (!activity.equals("farm") && !activity.equals("chop") && !activity.equals("mine")) return;
        long now = System.currentTimeMillis();
        for (CompanionEntity c : companionsOf(p, 24)) {
            if (c.getTask() != null || c.isPassenger() || c.getTarget() != null || !"follow".equals(c.getMode())) continue;
            Long last = LAST_HELP.get(c.getUUID());
            if (last != null && now - last < SkillsConfig.get().habitHelpCooldownSeconds * 1000L) continue;
            LAST_HELP.put(c.getUUID(), now);
            switch (activity) {
                case "farm" -> {
                    c.setTask(new FarmTask(pos, 8));
                    c.say("I'll help with the harvest.");
                }
                case "chop" -> {
                    c.setTask(new ChopTask(pos, 10, 24));
                    c.say("I'll chop some too.");
                }
                default -> {
                    c.setTask(new MineTask(pos, 8, BlockWork.matcher(subject), pretty(subject), 16));
                    c.say("I'll dig some " + pretty(subject) + " too.");
                }
            }
            SkillTask.event(c, "started helping " + p.getGameProfile().getName() + " (" + verb(activity) + ") because they do it often");
            return; // one helper per action is enough
        }
    }

    public static String verb(String activity) {
        return switch (activity) {
            case "farm" -> "farms";
            case "chop" -> "chops trees";
            case "mine" -> "mines";
            case "dig" -> "digs";
            case "build" -> "builds";
            default -> activity;
        };
    }

    public static String pretty(String id) {
        if (id == null || id.isBlank()) return "";
        String p = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        return p.replace('_', ' ');
    }

    /** One line for a companion's prompt: its player's habits. */
    public static String summary(UUID player, Level level) {
        HabitModel m = HabitStore.get().peek(player);
        if (m == null) return "";
        SkillsConfig cfg = SkillsConfig.get();
        double hl = Math.max(0.1, cfg.habitHalfLifeDays) * 24000.0;
        var top = m.top(level.getGameTime(), hl, cfg.habitThreshold / 3.0, 4);
        if (top.isEmpty()) return "";
        StringBuilder b = new StringBuilder();
        for (var t : top) {
            if (b.length() > 0) b.append("; ");
            b.append(verb(t.activity()));
            if (!t.subject().isBlank()) b.append(" (mostly ").append(pretty(t.subject())).append(")");
            if (t.score() >= cfg.habitThreshold) b.append(" a lot");
        }
        return b.toString();
    }

    static String itemId(ItemStack s) {
        return BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
    }
}
