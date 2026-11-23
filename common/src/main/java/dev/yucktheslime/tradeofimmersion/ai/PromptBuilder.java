package dev.yucktheslime.tradeofimmersion.ai;

import dev.yucktheslime.tradeofimmersion.action.Targets;
import dev.yucktheslime.tradeofimmersion.config.ToiConfig;
import dev.yucktheslime.tradeofimmersion.entity.CompanionEntity;
import dev.yucktheslime.tradeofimmersion.entity.CompanionTask;
import dev.yucktheslime.tradeofimmersion.item.ItemInspector;
import dev.yucktheslime.tradeofimmersion.memory.NpcMemoryStore;
import dev.yucktheslime.tradeofimmersion.util.McaVillagerUtil;
import dev.yucktheslime.tradeofimmersion.util.OwnedEntityUtil;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Builds the system prompt and the world-context user prompt for a companion turn. Server thread only. */
public final class PromptBuilder {
    private PromptBuilder() {
    }

    static final String ACTIONS = """
            Actions you can take (at most 4 per reply, each at most once; leave "actions" empty when just talking):
            - {"action":"follow"}  follow your companion player
            - {"action":"stay"}  wait where you are
            - {"action":"goto","target":"<name, creature type or 'x y z'>"}
            - {"action":"mount","target":"<horse|boat|minecart|pig|camel|any rideable name/type, or 'nearest'>"}  ride it; you steer it while following
            - {"action":"dismount"}
            - {"action":"attack","target":"<name or type, 'hostile' for the nearest monster, 'none' to stop>"}
            - {"action":"equip","item":"<item name or id from your inventory>","slot":"mainhand|offhand|auto"}  armor goes on automatically
            - {"action":"use_item","item":"<item>","target":"<optional: creature name/type or 'x y z' block>"}  item "food" eats the best food; also tools, modded items...
            - {"action":"give_item","item":"<item>","count":1,"to":"<player name, default your companion>"}
            - {"action":"pick_up"}  collect items lying nearby
            - {"action":"inspect_item","item":"<item name, or 'hand' for what your companion holds>"}  learn what an item is/does
            - {"action":"talk_to_villager","name":"<villager name>","message":"<what you say to them>"}
            """;

    public static String system(CompanionEntity c) {
        String persona = c.getPersona().isBlank()
                ? "Friendly, curious and brave; loyal to your companion, a little playful."
                : c.getPersona();
        return "You are " + c.name() + ", a living person in a Minecraft world (with MCA villages), travelling with "
                + c.getOwnerName() + ". You are " + c.getGender() + ". Personality: " + persona + "\n"
                + "Stay in character as a real inhabitant of this world: talk naturally and briefly (one or two sentences), "
                + "react to what happens around you, remember the past events listed, and never say you are an AI or a language model.\n"
                + "You can act in the world. Reply with ONLY one JSON object, no other text:\n"
                + "{\"say\":\"what you say out loud (may be empty)\",\"actions\":[...],\"learn\":[{\"item\":\"<item id>\",\"notes\":\"<one sentence on what it is for>\"}]}\n"
                + ACTIONS
                + "Only use items you actually carry, and targets that appear in the context. Use \"learn\" after inspecting an item to remember what it does. "
                + "If an action failed, say so honestly and maybe try something else.";
    }

    public static TurnRequest build(CompanionEntity c, String speaker, String trigger, String eventType) {
        ToiConfig cfg = ToiConfig.get();
        Map<String, String> ctx = TurnRequest.ctx();
        StringBuilder u = new StringBuilder();

        // ---- self
        u.append("== You ==\n");
        String health = Math.round(c.getHealth()) + "/" + Math.round(c.getMaxHealth());
        Entity vehicle = c.getVehicle();
        CompanionTask task = c.getTask();
        String doing = task != null ? task.describe() : c.getTarget() != null ? "fighting " + c.getTarget().getName().getString() : c.getMode();
        String where = c.level().getBiome(c.blockPosition()).unwrapKey().map(k -> k.location().getPath().replace('_', ' ')).orElse("somewhere");
        long day = c.level().getDayTime() % 24000;
        String time = day < 1000 ? "dawn" : day < 6000 ? "morning" : day < 12000 ? "afternoon" : day < 13500 ? "dusk" : "night";
        u.append("Health ").append(health).append(". Doing: ").append(doing)
                .append(vehicle != null ? ". Riding " + vehicle.getName().getString() : "")
                .append(". Position ").append(c.blockPosition().toShortString()).append(" in ").append(where)
                .append(" (").append(c.level().dimension().location().getPath()).append("), ").append(time)
                .append(c.level().isThundering() ? ", thunderstorm" : c.level().isRaining() ? ", raining" : "").append(".\n");
        List<String> gear = new ArrayList<>();
        for (EquipmentSlot s : EquipmentSlot.values()) {
            if (s.getType() == EquipmentSlot.Type.ANIMAL_ARMOR) continue;
            ItemStack st = c.getItemBySlot(s);
            if (!st.isEmpty()) gear.add(s.getName() + ": " + ItemInspector.brief(st));
        }
        u.append("Equipment: ").append(gear.isEmpty() ? "nothing" : String.join("; ", gear)).append('\n');
        List<String> inv = new ArrayList<>();
        for (int i = 0; i < c.inventory.getContainerSize(); i++) {
            ItemStack st = c.inventory.getItem(i);
            if (!st.isEmpty()) inv.add(ItemInspector.brief(st));
        }
        u.append("Inventory: ").append(inv.isEmpty() ? "empty" : String.join("; ", inv.subList(0, Math.min(inv.size(), 24)))).append('\n');
        ctx.put("health", health);
        ctx.put("doing", doing + (vehicle != null ? ", riding " + vehicle.getName().getString() : ""));
        ctx.put("location", where);
        ctx.put("time_of_day", time);
        ctx.put("holding", c.getMainHandItem().isEmpty() ? "nothing" : c.getMainHandItem().getHoverName().getString());
        ctx.put("inventory", inv.isEmpty() ? "empty" : inv.stream().limit(8).collect(Collectors.joining(", ")));

        // ---- surroundings
        u.append("== Around you ==\n");
        List<Entity> near = c.level().getEntities(c, c.getBoundingBox().inflate(24), e -> e.isAlive() && !(e instanceof ItemEntity));
        near.sort(Comparator.comparingDouble(c::distanceToSqr));
        List<String> people = new ArrayList<>(), creatures = new ArrayList<>(), villagers = new ArrayList<>(), things = new ArrayList<>();
        for (Entity e : near) {
            int d = (int) Math.round(c.distanceTo(e));
            if (e instanceof Player p) {
                people.add(p.getGameProfile().getName() + (c.isOwner(p) ? " (your companion)" : "") + " " + d + "m, health " + Math.round(p.getHealth())
                        + (p.getMainHandItem().isEmpty() ? "" : ", holding " + p.getMainHandItem().getHoverName().getString()));
            } else if (e instanceof VillagerEntityMCA v) {
                if (villagers.size() < 6) villagers.add(McaVillagerUtil.profile(v, c.getOwner()).describe() + " (" + d + "m)");
            } else if (e instanceof CompanionEntity o) {
                people.add(o.name() + " (companion of " + o.getOwnerName() + ") " + d + "m");
            } else if (e instanceof LivingEntity le) {
                if (creatures.size() < 12) {
                    creatures.add(le.getName().getString() + " [" + Targets.typeId(le) + "] " + d + "m"
                            + (le instanceof Enemy ? ", hostile" : "") + (Targets.looksRideable(le) ? ", rideable" : "")
                            + (le.getVehicle() == c ? "" : "") + (le == c.getTarget() ? ", your target" : ""));
                }
            } else if (things.size() < 8 && Targets.looksRideable(e)) {
                things.add(e.getName().getString() + " [" + Targets.typeId(e) + "] " + d + "m, rideable");
            }
        }
        if (!people.isEmpty()) u.append("People: ").append(String.join("; ", people)).append('\n');
        if (!creatures.isEmpty()) u.append("Creatures: ").append(String.join("; ", creatures)).append('\n');
        if (!things.isEmpty()) u.append("Things: ").append(String.join("; ", things)).append('\n');
        if (!villagers.isEmpty()) u.append("Villagers: ").append(String.join(" | ", villagers)).append('\n');
        try {
            if (cfg.detectOwnedCompanions) {
                var owned = OwnedEntityUtil.findOwnedNear(c, cfg.ownedCompanionScanRadius, x -> x == c || x instanceof CompanionEntity);
                if (!owned.isEmpty()) {
                    u.append("Tamed/owned creatures: ").append(owned.stream().limit(cfg.maxOwnedEntitiesInPrompt)
                            .map(o -> o.entity().getName().getString() + " (" + o.typeName() + ", owned by " + o.ownerName() + ")")
                            .collect(Collectors.joining("; "))).append('\n');
                }
            }
        } catch (Throwable ignored) {
        }
        ctx.put("nearby", String.join(", ", creatures.stream().limit(5).toList()) + (people.isEmpty() ? "" : "; people: " + String.join(", ", people.stream().limit(3).toList())));

        // ---- memory
        var events = NpcMemoryStore.get().recentEvents(c.getUUID(), cfg.memoryEventsInPrompt);
        if (!events.isEmpty()) {
            u.append("== Things you remember (oldest first) ==\n");
            long now = System.currentTimeMillis();
            for (var e : events) u.append("- ").append(ago(now - e.time())).append(": ").append(e.text()).append('\n');
            ctx.put("recent_event", events.get(events.size() - 1).text());
        }
        var chat = NpcMemoryStore.get().recentChat(c.getUUID(), cfg.chatLinesInPrompt);
        if (!chat.isEmpty()) {
            u.append("== Recent conversation ==\n");
            for (var l : chat) u.append(l.who()).append(": ").append(l.text()).append('\n');
        }
        u.append("== Now ==\n");
        if (eventType == null) u.append(speaker).append(" says to you: ").append(trigger);
        else if (speaker != null && speaker.startsWith("villager:")) u.append(trigger);
        else u.append("Something happened: ").append(trigger);
        u.append("\nReply with the JSON object.");
        return new TurnRequest(c.getUUID().toString(), c.name(), c.getOwnerName(), system(c), u.toString(), trigger,
                speaker == null ? "" : speaker, eventType, ctx);
    }

    static String ago(long ms) {
        long s = ms / 1000;
        if (s < 60) return "just now";
        if (s < 3600) return (s / 60) + " min ago";
        if (s < 86400) return (s / 3600) + " h ago";
        return (s / 86400) + " days ago";
    }
}
