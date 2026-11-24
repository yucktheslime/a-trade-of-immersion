package dev.yucktheslime.tradeofimmersion.action;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.yucktheslime.tradeofimmersion.entity.CompanionEntity;
import dev.yucktheslime.tradeofimmersion.entity.CompanionTask;
import dev.yucktheslime.tradeofimmersion.item.ItemInspector;
import dev.yucktheslime.tradeofimmersion.memory.ItemKnowledgeStore;
import dev.yucktheslime.tradeofimmersion.memory.NpcMemoryStore;
import dev.yucktheslime.tradeofimmersion.util.McaVillagerUtil;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Validates and runs the actions an AI reply asks for. Only names in {@link #ALLOWED} run; at most
 * {@link #MAX_ACTIONS} per reply; each name once. Returns one result line per action (fed back to the AI).
 */
public final class ActionExecutor {
    public static final Set<String> ALLOWED = Set.of("follow", "stay", "goto", "mount", "dismount", "attack", "equip", "use_item",
            "give_item", "pick_up", "inspect_item", "talk_to_villager");
    public static final int MAX_ACTIONS = 4;
    private static volatile Set<String> extraAllowed = Set.of();

    private ActionExecutor() {
    }

    /** Extra action names advertised by enabled Kagrenac server plugins. */
    public static void setExtraAllowed(Collection<String> names) {
        Set<String> clean = new LinkedHashSet<>();
        if (names != null) {
            for (String n : names) {
                if (n != null && n.matches("[A-Za-z0-9_]{1,40}")) clean.add(n.toLowerCase(Locale.ROOT));
            }
        }
        extraAllowed = Set.copyOf(clean);
    }

    public record Result(String action, boolean ok, String text) {
        @Override
        public String toString() {
            return action + ": " + (ok ? "" : "FAILED - ") + text;
        }
    }

    public static List<Result> run(CompanionEntity c, List<JsonObject> actions) {
        List<Result> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (JsonObject a : actions) {
            if (out.size() >= MAX_ACTIONS) break;
            String name = str(a, "action");
            if (name.isEmpty()) name = str(a, "name");
            name = name.toLowerCase(Locale.ROOT).trim();
            if (name.equals("eat")) name = "use_item";
            if (name.equals("hold_position") || name.equals("wait")) name = "stay";
            if (name.equals("follow_player")) name = "follow";
            JsonObject args = a.has("args") && a.get("args").isJsonObject() ? a.getAsJsonObject("args") : a;
            if (!ALLOWED.contains(name) && !extraAllowed.contains(name)) {
                out.add(new Result(name, false, "not an allowed action"));
                continue;
            }
            if (!seen.add(name)) continue;
            Result r;
            try {
                r = runOne(c, name, args);
            } catch (Exception e) {
                r = new Result(name, false, "error " + e.getClass().getSimpleName());
            }
            out.add(r);
            NpcMemoryStore.get().event(c.getUUID(), "action", r.toString());
        }
        return out;
    }

    private static Result runOne(CompanionEntity c, String name, JsonObject a) {
        switch (name) {
            case "follow" -> {
                c.setTask(null);
                c.setCommandedTarget(null);
                c.setMode("follow");
                return ok(name, "following " + c.getOwnerName());
            }
            case "stay" -> {
                c.setTask(null);
                c.setMode("stay");
                return ok(name, "staying here");
            }
            case "goto" -> {
                String t = first(a, "target", "to", "where");
                Vec3 pos = Targets.parsePos(t);
                if (pos != null) {
                    c.setTask(new CompanionTask.Goto(null, pos, t));
                    return ok(name, "walking to " + t);
                }
                Entity e = Targets.find(c, t, 64, x -> true);
                if (e == null) return fail(name, "can't see '" + t + "'");
                c.setTask(new CompanionTask.Goto(e, e.position(), e.getName().getString()));
                return ok(name, "going to " + e.getName().getString());
            }
            case "mount" -> {
                String t = first(a, "target", "entity", "vehicle");
                Entity e = Targets.find(c, t, 16, x -> x != c.getVehicle() && !(x instanceof Player) && !x.isPassengerOfSameVehicle(c)
                        && (t.isBlank() || "nearest".equalsIgnoreCase(t) ? Targets.looksRideable(x) : true));
                if (e == null) return fail(name, "no rideable '" + t + "' nearby");
                c.setTask(new CompanionTask.Mount(e));
                return ok(name, "going to ride " + e.getName().getString());
            }
            case "dismount" -> {
                if (!c.isPassenger()) return fail(name, "not riding anything");
                Entity v = c.getVehicle();
                c.stopVehicle();
                c.stopRiding();
                return ok(name, "got off " + (v == null ? "" : v.getName().getString()));
            }
            case "attack" -> {
                String t = first(a, "target", "entity", "who");
                if (t.equalsIgnoreCase("none") || t.equalsIgnoreCase("stop")) {
                    c.setCommandedTarget(null);
                    c.setTarget(null);
                    return ok(name, "stopped fighting");
                }
                boolean anyHostile = t.isBlank() || t.toLowerCase(Locale.ROOT).contains("hostile") || t.toLowerCase(Locale.ROOT).contains("monster") || t.equalsIgnoreCase("enemy");
                Entity e = anyHostile ? Targets.find(c, "", 24, x -> x instanceof net.minecraft.world.entity.monster.Enemy && Targets.attackable(c, x))
                        : Targets.find(c, t, 24, x -> Targets.attackable(c, x));
                if (!(e instanceof LivingEntity le)) return fail(name, "no '" + t + "' to attack nearby");
                c.setTask(null);
                c.setCommandedTarget(le);
                return ok(name, "attacking " + le.getName().getString());
            }
            case "equip" -> {
                String item = first(a, "item", "name");
                int slot = findItem(c, item);
                if (slot < 0) return fail(name, "no '" + item + "' in inventory");
                ItemStack stack = c.inventory.getItem(slot);
                String where = str(a, "slot").toLowerCase(Locale.ROOT);
                EquipmentSlot target = where.contains("off") ? EquipmentSlot.OFFHAND
                        : where.contains("main") || where.contains("hand") ? EquipmentSlot.MAINHAND : c.getEquipmentSlotForItem(stack);
                ItemStack old = c.getItemBySlot(target);
                c.setItemSlot(target, stack);
                c.inventory.setItem(slot, old);
                return ok(name, "equipped " + stack.getHoverName().getString() + " in " + target.getName());
            }
            case "use_item" -> {
                String item = first(a, "item", "name");
                if (item.equalsIgnoreCase("food") || item.equalsIgnoreCase("something")) {
                    return c.eatSomething() ? ok(name, "ate some food") : fail(name, "no food in inventory");
                }
                if (!item.isBlank() && !matches(c.getMainHandItem(), item)) {
                    int slot = findItem(c, item);
                    if (slot < 0) return fail(name, "no '" + item + "' in inventory");
                    ItemStack old = c.getMainHandItem();
                    c.setItemSlot(EquipmentSlot.MAINHAND, c.inventory.getItem(slot));
                    c.inventory.setItem(slot, old);
                }
                if (c.getMainHandItem().isEmpty()) {
                    if (item.isBlank() && c.eatSomething()) return ok(name, "ate something");
                    return fail(name, "nothing in hand");
                }
                String t = first(a, "target", "on");
                Vec3 pos = Targets.parsePos(t);
                Entity e = null;
                if (pos == null && !t.isBlank() && !t.equalsIgnoreCase("self") && !t.equalsIgnoreCase("front") && !t.equalsIgnoreCase("air")) {
                    e = Targets.find(c, t, 6, x -> x != c);
                    if (e == null) return fail(name, "no '" + t + "' within reach");
                }
                return ok(name, ItemUse.use(c, e, pos));
            }
            case "give_item" -> {
                String item = first(a, "item", "name");
                int count = a.has("count") ? Math.max(1, a.get("count").getAsInt()) : 64;
                String to = first(a, "to", "target", "player");
                Entity e = to.isBlank() ? c.getOwner() : Targets.find(c, to, 16, x -> x instanceof Player);
                if (!(e instanceof Player p)) return fail(name, "can't see '" + (to.isBlank() ? "owner" : to) + "'");
                int slot = findItem(c, item);
                ItemStack stack;
                if (slot >= 0) stack = c.inventory.removeItem(slot, count);
                else if (matches(c.getMainHandItem(), item)) stack = c.getMainHandItem().split(count);
                else return fail(name, "no '" + item + "' to give");
                String what = stack.getCount() + "x " + stack.getHoverName().getString();
                if (c.distanceToSqr(p) < 4 * 4 && p.getInventory().add(stack) && stack.isEmpty()) return ok(name, "gave " + what + " to " + p.getGameProfile().getName());
                ItemEntity drop = new ItemEntity(c.level(), c.getX(), c.getEyeY() - 0.3, c.getZ(), stack);
                Vec3 dir = p.position().subtract(c.position()).normalize().scale(0.35);
                drop.setDeltaMovement(dir.x, 0.25, dir.z);
                drop.setPickUpDelay(10);
                c.level().addFreshEntity(drop);
                c.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                return ok(name, "tossed " + what + " to " + p.getGameProfile().getName());
            }
            case "pick_up" -> {
                c.setTask(new CompanionTask.PickUp(a.has("radius") ? Math.min(16, a.get("radius").getAsDouble()) : 10));
                return ok(name, "collecting nearby items");
            }
            case "inspect_item" -> {
                String item = first(a, "item", "name");
                ItemStack stack = ItemStack.EMPTY;
                int slot = findItem(c, item);
                if (slot >= 0) stack = c.inventory.getItem(slot);
                else if (matches(c.getMainHandItem(), item) || item.isBlank() && !c.getMainHandItem().isEmpty()) stack = c.getMainHandItem();
                else if (c.getOwner() != null && (item.isBlank() || item.contains("hand") || matches(c.getOwner().getMainHandItem(), item))) stack = c.getOwner().getMainHandItem();
                if (stack.isEmpty()) return fail(name, "no '" + item + "' to look at");
                ItemKnowledgeStore.Knowledge k = ItemInspector.inspect(stack, c.level());
                return ok(name, k.facts);
            }
            case "talk_to_villager" -> {
                String who = first(a, "name", "target", "villager");
                String msg = first(a, "message", "text", "say");
                if (msg.isBlank()) msg = "Hello!";
                Optional<VillagerEntityMCA> v = c.getServer() == null ? Optional.empty() : McaVillagerUtil.find(c.getServer(), c, who, 48);
                if (v.isEmpty()) return fail(name, "no villager called '" + who + "' nearby");
                c.setTask(new CompanionTask.TalkToVillager(v.get(), msg));
                return ok(name, "going to talk to " + v.get().getName().getString());
            }
            default -> {
                if (extraAllowed.contains(name)) {
                    c.swing(InteractionHand.MAIN_HAND);
                    String val = first(a, "value", "name");
                    return ok(name, val.isBlank() ? ("did " + name) : (name + ": " + val));
                }
                return fail(name, "unknown");
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    public static int findItem(CompanionEntity c, String query) {
        if (query == null || query.isBlank()) return -1;
        for (int i = 0; i < c.inventory.getContainerSize(); i++) {
            if (matches(c.inventory.getItem(i), query)) return i;
        }
        return -1;
    }

    public static boolean matches(ItemStack s, String query) {
        if (s.isEmpty() || query == null || query.isBlank()) return false;
        String q = query.toLowerCase(Locale.ROOT).trim();
        String name = s.getHoverName().getString().toLowerCase(Locale.ROOT);
        String id = ItemInspector.id(s).toLowerCase(Locale.ROOT);
        return name.equals(q) || id.equals(q) || name.contains(q) || id.contains(q.replace(' ', '_'));
    }

    private static String str(JsonObject a, String key) {
        JsonElement e = a.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
    }

    private static String first(JsonObject a, String... keys) {
        for (String k : keys) {
            String v = str(a, k);
            if (!v.isBlank()) return v.trim();
        }
        return "";
    }

    private static Result ok(String a, String t) {
        return new Result(a, true, t);
    }

    private static Result fail(String a, String t) {
        return new Result(a, false, t);
    }
}
