package dev.yucktheslime.tradeofimmersion.skills;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.yucktheslime.tradeofimmersion.action.ActionExecutor;
import dev.yucktheslime.tradeofimmersion.action.ActionExecutor.Result;
import dev.yucktheslime.tradeofimmersion.action.Targets;
import dev.yucktheslime.tradeofimmersion.entity.CompanionEntity;
import dev.yucktheslime.tradeofimmersion.entity.CompanionTask;
import dev.yucktheslime.tradeofimmersion.skills.item.ItemLearner;
import dev.yucktheslime.tradeofimmersion.skills.item.ItemSkillRunner;
import dev.yucktheslime.tradeofimmersion.skills.job.BlockWork;
import dev.yucktheslime.tradeofimmersion.skills.job.ChopTask;
import dev.yucktheslime.tradeofimmersion.skills.job.DigTask;
import dev.yucktheslime.tradeofimmersion.skills.job.FarmTask;
import dev.yucktheslime.tradeofimmersion.skills.job.FetchTask;
import dev.yucktheslime.tradeofimmersion.skills.job.MineTask;
import dev.yucktheslime.tradeofimmersion.skills.learn.HabitTracker;
import dev.yucktheslime.tradeofimmersion.skills.mount.MountClassifier;
import dev.yucktheslime.tradeofimmersion.skills.mount.MountRules;
import dev.yucktheslime.tradeofimmersion.skills.travel.TravelPlanner;
import dev.yucktheslime.tradeofimmersion.skills.travel.TravelTask;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Phase 3 actions for the AI and {@code /npc do}: jobs (farm, chop, mine, dig, fetch), riding with the mount rules,
 * long trips, and learned item use. {@link ActionExecutor} asks {@link #run} first; null means "not mine, carry on".
 */
public final class SkillActions {
    /** New action names (the executor's allowlist includes these). */
    public static final Set<String> NAMES = Set.of("farm", "chop", "mine", "dig", "fetch", "travel", "use_ability", "learn_item", "stop_job");

    private SkillActions() {
    }

    public static final String PROMPT = """
            - {"action":"farm","radius":8}  harvest ripe crops around you and replant
            - {"action":"chop","count":24}  chop the trees nearby (not log houses), replant saplings
            - {"action":"mine","block":"<stone|coal ore|iron ore|ores|block id>","count":16}  mine exposed blocks you can walk to
            - {"action":"dig","direction":"<north|south|east|west|forward>","mode":"tunnel|stairs_down|stairs_up|bridge","length":10}  dig a tunnel/staircase or bridge a gap with blocks from your bag
            - {"action":"fetch","item":"<item>","count":8}  bring items from the ground or nearby chests to your companion
            - {"action":"travel","target":"<name or 'x y z'>"}  go somewhere far; on long trips you take a horse/boat/vehicle you may use
            - {"action":"learn_item","item":"<item>"}  read its tooltip and learn how it is used (right-click, keys...)
            - {"action":"use_ability","item":"<item>","how":"<auto|right_click|sneak|block|entity|hold|key:V|a word like dash>","target":"<optional>"}  use an item the way you learned
            - {"action":"stop_job"}  stop the job you are doing
            Riding: never ride a dragon that belongs to a player; wild dragons, horses, bikes and cars are fine.
            """;

    @Nullable
    public static Result run(CompanionEntity c, String name, JsonObject a) {
        SkillsConfig cfg = SkillsConfig.get();
        switch (name) {
            case "mount" -> {
                return mount(c, a);
            }
            case "goto" -> {
                return gotoLong(c, a, false);
            }
            case "travel" -> {
                return gotoLong(c, a, true);
            }
            case "stop_job" -> {
                CompanionTask t = c.getTask();
                c.setTask(null);
                return ok(name, t == null ? "wasn't doing anything" : "stopped " + t.describe());
            }
            case "farm", "chop", "mine", "dig", "fetch" -> {
                if (!cfg.jobsEnabled) return fail(name, "jobs are turned off");
                return job(c, name, a, cfg);
            }
            case "learn_item" -> {
                if (!cfg.itemLearning) return fail(name, "item learning is turned off");
                ItemStack stack = findStack(c, first(a, "item", "name", "value"));
                if (stack.isEmpty()) return fail(name, "no '" + first(a, "item", "name") + "' to look at");
                return ok(name, ItemLearner.learn(c, stack));
            }
            case "use_ability" -> {
                String item = first(a, "item", "name");
                if (!item.isBlank() && !ActionExecutor.matches(c.getMainHandItem(), item)) {
                    int slot = ActionExecutor.findItem(c, item);
                    if (slot < 0) return fail(name, "no '" + item + "' in inventory");
                    ItemStack old = c.getMainHandItem();
                    c.setItemSlot(EquipmentSlot.MAINHAND, c.inventory.getItem(slot));
                    c.inventory.setItem(slot, old);
                }
                String t = first(a, "target", "on");
                Vec3 pos = Targets.parsePos(t);
                Entity e = null;
                if (pos == null && !t.isBlank() && !t.equalsIgnoreCase("self") && !t.equalsIgnoreCase("air") && !t.equalsIgnoreCase("block")) {
                    e = Targets.find(c, t, 6, x -> x != c);
                    if (e == null) return fail(name, "no '" + t + "' within reach");
                }
                if (pos == null && t.equalsIgnoreCase("block")) {
                    var hit = c.pick(5.0, 1.0f, false);
                    if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) pos = hit.getLocation();
                }
                ItemSkillRunner.Outcome o = ItemSkillRunner.use(c, first(a, "how", "mode"), e, pos);
                return new Result(name, o.ok(), o.text());
            }
            default -> {
                return null;
            }
        }
    }

    private static Result job(CompanionEntity c, String name, JsonObject a, SkillsConfig cfg) {
        int maxR = Math.max(4, cfg.jobMaxRadius);
        int radius = clamp(num(a, "radius", name.equals("farm") ? 8 : 12), 2, maxR);
        BlockPos center = c.blockPosition();
        String near = first(a, "near", "around", "at");
        if (near.equalsIgnoreCase("owner") || near.equalsIgnoreCase("me")) {
            Player o = c.getOwner();
            if (o != null) center = o.blockPosition();
        } else {
            Vec3 p = Targets.parsePos(near);
            if (p != null) center = BlockPos.containing(p);
        }
        if (c.isPassenger()) c.stopRiding();
        switch (name) {
            case "farm" -> {
                c.setTask(new FarmTask(center, radius));
                return ok(name, "harvesting ripe crops within " + radius + " blocks");
            }
            case "chop" -> {
                if (!cfg.miningEnabled) return fail(name, "breaking blocks is turned off");
                int n = clamp(num(a, "count", 24), 1, cfg.mineMaxBlocks);
                c.setTask(new ChopTask(center, radius, n));
                return ok(name, "chopping up to " + n + " logs");
            }
            case "mine" -> {
                if (!cfg.miningEnabled) return fail(name, "breaking blocks is turned off");
                String what = first(a, "block", "target", "item", "value");
                if (what.isBlank()) what = "stone";
                int n = clamp(num(a, "count", 16), 1, cfg.mineMaxBlocks);
                c.setTask(new MineTask(center, Math.min(radius, 16), BlockWork.matcher(what), what, n));
                return ok(name, "mining up to " + n + " " + what);
            }
            case "dig" -> {
                String v = first(a, "value");
                Direction dir = direction(c, first(a, "direction", "dir").isBlank() ? v : first(a, "direction", "dir"));
                DigTask.Mode mode = DigTask.mode(first(a, "mode").isBlank() ? v : first(a, "mode"));
                if (mode != DigTask.Mode.BRIDGE && !cfg.miningEnabled) return fail(name, "breaking blocks is turned off");
                if (mode == DigTask.Mode.BRIDGE && !cfg.bridgingEnabled) return fail(name, "bridging is turned off");
                int len = clamp(num(a, "length", firstInt(v, 10)), 1, cfg.digMaxLength);
                c.setTask(new DigTask(c.blockPosition(), dir, mode, len));
                return ok(name, mode.name().toLowerCase(Locale.ROOT).replace('_', ' ') + " " + dir.getName() + " for " + len + " blocks");
            }
            default -> {
                String item = first(a, "item", "name", "value");
                if (item.isBlank()) return fail(name, "fetch what?");
                int n = clamp(num(a, "count", 16), 1, 64 * 9);
                c.setTask(new FetchTask(item, n, Math.min(radius, 24)));
                return ok(name, "looking for " + item);
            }
        }
    }

    private static Result mount(CompanionEntity c, JsonObject a) {
        String t = first(a, "target", "entity", "vehicle", "value");
        Entity e = Targets.find(c, t, 16, x -> x != c.getVehicle() && !(x instanceof Player) && !x.isPassengerOfSameVehicle(c)
                && MountClassifier.kind(x) != MountRules.Kind.NOT_RIDEABLE
                && (t.isBlank() || "nearest".equalsIgnoreCase(t) ? MountClassifier.decide(c, x).allowed() : true));
        if (e == null) return fail("mount", "no rideable '" + t + "' nearby that I may ride");
        if (!SkillsConfig.get().mountControl) return null; // old behaviour
        MountRules.Decision d = MountClassifier.decide(c, e);
        if (!d.allowed()) return fail("mount", "won't ride " + e.getName().getString() + ": " + d.reason());
        c.setTask(new CompanionTask.Mount(e));
        return ok("mount", "going to ride " + e.getName().getString() + " (" + d.reason() + (d.steerable() ? "" : "; I can sit on it but not steer") + ")");
    }

    /** goto: unchanged for short trips; long trips (or "travel") use {@link TravelTask}. */
    @Nullable
    private static Result gotoLong(CompanionEntity c, JsonObject a, boolean always) {
        String t = first(a, "target", "to", "where", "value");
        Vec3 pos = Targets.parsePos(t);
        Entity e = null;
        if (pos == null) {
            e = Targets.find(c, t, always ? 128 : 64, x -> true);
            if (e == null) return always ? fail("travel", "can't see '" + t + "'") : null;
            pos = e.position();
        }
        double dist = Math.sqrt(Math.pow(pos.x - c.getX(), 2) + Math.pow(pos.z - c.getZ(), 2));
        SkillsConfig cfg = SkillsConfig.get();
        if (!always) {
            // keep the old Goto for short walks; take over when it's long (pathfinding can't reach far points directly)
            if (dist <= Math.min(cfg.longTravelDistance, 48)) return null;
        }
        String label = e != null ? e.getName().getString() : t;
        c.setTask(new TravelTask(e, pos, label));
        boolean seek = TravelPlanner.plan(dist, cfg.longTravelDistance, cfg.longTravel && cfg.mountControl, c.isPassenger(), false) == TravelPlanner.Mode.SEEK_VEHICLE;
        return ok(always ? "travel" : "goto", "heading to " + label + " (" + Math.round(dist) + " blocks" + (seek ? ", looking for a ride first" : "") + ")");
    }

    static Direction direction(CompanionEntity c, String s) {
        String d = s == null ? "" : s.toLowerCase(Locale.ROOT);
        for (Direction x : Direction.Plane.HORIZONTAL) if (d.contains(x.getName())) return x;
        if (d.contains("toward") && c.getOwner() != null) {
            Vec3 v = c.getOwner().position().subtract(c.position());
            return Direction.getNearest(v.x, 0, v.z);
        }
        return c.getDirection();
    }

    /** Prompt context lines: the player's habits and how to use what the companion carries. */
    public static String context(CompanionEntity c) {
        StringBuilder b = new StringBuilder();
        try {
            if (SkillsConfig.get().habitLearning && c.getOwnerId().isPresent()) {
                String h = HabitTracker.summary(c.getOwnerId().get(), c.level());
                if (!h.isBlank()) b.append("Your companion ").append(c.getOwnerName()).append(" often: ").append(h).append(". Offer to help with those (farm/chop/mine).\n");
            }
            if (SkillsConfig.get().itemLearning) {
                List<String> known = new ArrayList<>();
                List<ItemStack> stacks = new ArrayList<>();
                stacks.add(c.getMainHandItem());
                for (int i = 0; i < c.inventory.getContainerSize(); i++) stacks.add(c.inventory.getItem(i));
                java.util.Set<String> seen = new java.util.HashSet<>();
                for (ItemStack s : stacks) {
                    if (s.isEmpty() || !seen.add(ItemLearner.id(s))) continue;
                    String how = ItemLearner.brief(s);
                    if (!how.isBlank()) known.add(s.getHoverName().getString() + " (" + how + ")");
                    if (known.size() >= 5) break;
                }
                if (!known.isEmpty()) b.append("Items you know how to use (use_ability): ").append(String.join("; ", known)).append(".\n");
            }
            if (c.isPassenger() && c.getVehicle() != null) {
                MountRules.Decision d = MountClassifier.decide(c, c.getVehicle());
                if (!d.steerable()) b.append("You sit on ").append(c.getVehicle().getName().getString()).append(" but can't steer it.\n");
            }
        } catch (Throwable ignored) {
            // context is best effort
        }
        return b.toString();
    }

    // ------------------------------------------------------------------ helpers

    static ItemStack findStack(CompanionEntity c, String item) {
        int slot = ActionExecutor.findItem(c, item);
        if (slot >= 0) return c.inventory.getItem(slot);
        if (item.isBlank() || ActionExecutor.matches(c.getMainHandItem(), item)) return c.getMainHandItem();
        Player o = c.getOwner();
        if (o != null && (item.contains("hand") || ActionExecutor.matches(o.getMainHandItem(), item))) return o.getMainHandItem();
        return ItemStack.EMPTY;
    }

    static int num(JsonObject a, String key, int def) {
        JsonElement e = a.get(key);
        try {
            return e != null && e.isJsonPrimitive() ? (int) Math.round(e.getAsDouble()) : def;
        } catch (Exception ex) {
            return def;
        }
    }

    static int firstInt(String s, int def) {
        if (s == null) return def;
        var m = java.util.regex.Pattern.compile("\\d+").matcher(s);
        return m.find() ? Integer.parseInt(m.group()) : def;
    }

    static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    static String first(JsonObject a, String... keys) {
        for (String k : keys) {
            JsonElement e = a.get(k);
            if (e != null && e.isJsonPrimitive() && !e.getAsString().isBlank()) return e.getAsString().trim();
        }
        return "";
    }

    static Result ok(String a, String t) {
        return new Result(a, true, t);
    }

    static Result fail(String a, String t) {
        return new Result(a, false, t);
    }

    /** Kagrenac protocol: one plain argument per action -> executor JSON. Null when not ours. */
    @Nullable
    public static JsonObject fromKagrenac(String n, String value) {
        if (!NAMES.contains(n)) return null;
        JsonObject m = new JsonObject();
        m.addProperty("action", n);
        String v = value == null ? "" : value.trim();
        switch (n) {
            case "mine" -> m.addProperty("block", v);
            case "fetch", "learn_item" -> m.addProperty("item", v);
            case "travel" -> m.addProperty("target", v);
            case "use_ability" -> {
                // "item" or "item, how"
                int i = v.indexOf(',');
                m.addProperty("item", i > 0 ? v.substring(0, i).trim() : v);
                if (i > 0) m.addProperty("how", v.substring(i + 1).trim());
            }
            default -> m.addProperty("value", v);
        }
        return m;
    }
}
