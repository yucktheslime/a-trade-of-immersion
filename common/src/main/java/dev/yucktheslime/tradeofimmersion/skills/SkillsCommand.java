package dev.yucktheslime.tradeofimmersion.skills;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.yucktheslime.tradeofimmersion.action.Targets;
import dev.yucktheslime.tradeofimmersion.entity.CompanionEntity;
import dev.yucktheslime.tradeofimmersion.entity.Companions;
import dev.yucktheslime.tradeofimmersion.skills.item.ItemLearner;
import dev.yucktheslime.tradeofimmersion.skills.item.ItemSkillStore;
import dev.yucktheslime.tradeofimmersion.skills.learn.HabitStore;
import dev.yucktheslime.tradeofimmersion.skills.learn.HabitTracker;
import dev.yucktheslime.tradeofimmersion.skills.mount.MountClassifier;
import dev.yucktheslime.tradeofimmersion.skills.mount.MountRules;
import dev.yucktheslime.tradeofimmersion.skills.mount.VehicleAdapter;
import dev.yucktheslime.tradeofimmersion.skills.mount.VehicleAdapters;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Map;

/**
 * {@code /npcskills}: what companions learned and why they would or wouldn't ride something.
 * <ul>
 *   <li>{@code habits [player]} - the player's habits (others' need permission 2)</li>
 *   <li>{@code items} / {@code item <id>} / {@code forget <id>} - learned item uses (forget needs permission 2)</li>
 *   <li>{@code mounts <npc>} - every rideable thing near the companion and the mount-rule decision</li>
 *   <li>{@code reload} - re-read tradeofimmersion-skills.json (permission 2)</li>
 * </ul>
 */
public final class SkillsCommand {
    private SkillsCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("npcskills")
                .then(Commands.literal("habits")
                        .executes(c -> habits(c.getSource(), c.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player()).requires(s -> s.hasPermission(2))
                                .executes(c -> habits(c.getSource(), EntityArgument.getPlayer(c, "player")))))
                .then(Commands.literal("items").executes(c -> items(c.getSource())))
                .then(Commands.literal("item").then(Commands.argument("id", StringArgumentType.greedyString())
                        .executes(c -> item(c.getSource(), StringArgumentType.getString(c, "id")))))
                .then(Commands.literal("forget").requires(s -> s.hasPermission(2)).then(Commands.argument("id", StringArgumentType.greedyString())
                        .executes(c -> {
                            ItemSkillStore.get().forget(StringArgumentType.getString(c, "id"));
                            c.getSource().sendSuccess(() -> Component.literal("Forgotten."), false);
                            return 1;
                        })))
                .then(Commands.literal("mounts").then(Commands.argument("npc", StringArgumentType.string())
                        .executes(c -> mounts(c.getSource(), StringArgumentType.getString(c, "npc")))))
                .then(Commands.literal("reload").requires(s -> s.hasPermission(2)).executes(c -> {
                    SkillsConfig.load();
                    c.getSource().sendSuccess(() -> Component.literal("Reloaded " + SkillsConfig.path().getFileName()), false);
                    return 1;
                })));
    }

    private static int habits(CommandSourceStack src, ServerPlayer p) {
        String s = HabitTracker.summary(p.getUUID(), p.level());
        var m = HabitStore.get().peek(p.getUUID());
        StringBuilder b = new StringBuilder(p.getGameProfile().getName()).append(": ").append(s.isBlank() ? "no habits yet" : s);
        if (m != null) {
            double hl = Math.max(0.1, SkillsConfig.get().habitHalfLifeDays) * 24000.0;
            for (var t : m.top(p.level().getGameTime(), hl, 0.01, 8)) {
                b.append("\n  ").append(t.activity()).append(" ").append(String.format("%.1f", t.score())).append(t.subject().isBlank() ? "" : " (" + t.subject() + ")");
            }
        }
        src.sendSuccess(() -> Component.literal(b.toString()), false);
        return 1;
    }

    private static int items(CommandSourceStack src) {
        Map<String, ItemSkillStore.Skill> all = ItemSkillStore.get().all();
        StringBuilder b = new StringBuilder(all.size() + " item(s) learned");
        int n = 0;
        for (var e : all.entrySet()) {
            if (n++ >= 20) {
                b.append("\n  ...");
                break;
            }
            int keys = e.getValue().keyPackets.values().stream().mapToInt(java.util.List::size).sum();
            b.append("\n  ").append(e.getKey()).append(": ").append(e.getValue().hints.size()).append(" way(s)")
                    .append(keys > 0 ? ", " + keys + " key packet(s)" : "")
                    .append(e.getValue().unusable.isEmpty() ? "" : ", " + e.getValue().unusable.size() + " unusable");
        }
        src.sendSuccess(() -> Component.literal(b.toString()), false);
        return 1;
    }

    private static int item(CommandSourceStack src, String id) {
        ItemSkillStore.Skill s = ItemSkillStore.get().get(id);
        if (s == null) {
            src.sendFailure(Component.literal("Nothing learned about " + id));
            return 0;
        }
        StringBuilder b = new StringBuilder(ItemLearner.summary(id, s));
        b.append("\n  tooltip (").append(s.tooltipSource).append("): ").append(String.join(" | ", s.tooltip));
        for (var e : s.keyPackets.entrySet()) for (var p : e.getValue()) b.append("\n  key ").append(p.keyLabel).append(" [").append(p.keyBound).append("] -> ").append(p.channel).append(" x").append(p.sightings);
        for (var e : s.unusableWhy.entrySet()) b.append("\n  unusable ").append(e.getKey()).append(": ").append(e.getValue());
        src.sendSuccess(() -> Component.literal(b.toString()), false);
        return 1;
    }

    private static int mounts(CommandSourceStack src, String npc) {
        CompanionEntity c = null;
        for (CompanionEntity x : Companions.all(src.getServer())) if (x.name().equalsIgnoreCase(npc)) c = x;
        if (c == null) {
            src.sendFailure(Component.literal("No loaded companion called " + npc));
            return 0;
        }
        if (!src.hasPermission(2) && !(src.getEntity() instanceof ServerPlayer sp && c.isOwner(sp))) {
            src.sendFailure(Component.literal("That companion isn't yours."));
            return 0;
        }
        StringBuilder b = new StringBuilder("Rideable things near " + c.name() + ":");
        int n = 0;
        for (Entity e : c.level().getEntities(c, c.getBoundingBox().inflate(24), x -> MountClassifier.kind(x) != MountRules.Kind.NOT_RIDEABLE)) {
            MountRules.Decision d = MountClassifier.decide(c, e);
            VehicleAdapter a = VehicleAdapters.find(e);
            b.append("\n  ").append(e.getName().getString()).append(" [").append(Targets.typeId(e)).append(", ").append(MountClassifier.kind(e))
                    .append(a == null ? "" : ", " + a.id()).append("]: ").append(d.allowed() ? (d.steerable() ? "yes" : "sit only") : "no").append(" - ").append(d.reason());
            if (++n >= 15) break;
        }
        if (n == 0) b.append(" none");
        src.sendSuccess(() -> Component.literal(b.toString()), false);
        return 1;
    }
}
