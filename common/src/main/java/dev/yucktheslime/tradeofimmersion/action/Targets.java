package dev.yucktheslime.tradeofimmersion.action;

import dev.yucktheslime.tradeofimmersion.entity.CompanionEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Saddleable;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/** Resolves the free-text targets the AI names ("owner", "Steve", "horse", "nearest boat", "zombie", "10 64 -3"). */
public final class Targets {
    private Targets() {
    }

    @Nullable
    public static Vec3 parsePos(String s) {
        if (s == null) return null;
        String[] p = s.trim().replace(",", " ").split("\\s+");
        if (p.length != 3) return null;
        try {
            return new Vec3(Double.parseDouble(p[0]) + 0.5, Double.parseDouble(p[1]), Double.parseDouble(p[2]) + 0.5);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String typeId(Entity e) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString();
    }

    /** Anything a companion could plausibly sit on: boats, minecarts, horses/camels/llamas, saddleable mobs, modded seats/mounts. */
    public static boolean looksRideable(Entity e) {
        if (e instanceof Boat || e instanceof AbstractMinecart || e instanceof AbstractHorse || e instanceof Saddleable) return true;
        String id = typeId(e).toLowerCase(Locale.ROOT);
        return id.contains("seat") || id.contains("chair") || id.contains("sit") || id.contains("mount") || id.contains("dragon")
                || id.contains("boat") || id.contains("cart") || id.contains("bench") || id.contains("couch") || id.contains("sofa");
    }

    @Nullable
    public static Entity find(CompanionEntity c, String query, double radius, Predicate<Entity> filter) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        q = q.replaceFirst("^(the|a|an|nearest|closest|that)\\s+", "");
        if (q.equals("owner") || q.equals("me")) {
            Player o = c.getOwner();
            return o != null && filter.test(o) ? o : null;
        }
        List<Entity> all = c.level().getEntities(c, c.getBoundingBox().inflate(radius), e -> e.isAlive() && filter.test(e) && !(e instanceof ItemEntity));
        all.sort(Comparator.comparingDouble(c::distanceToSqr));
        if (q.isEmpty() || q.equals("nearest") || q.equals("any") || q.equals("it")) return all.isEmpty() ? null : all.get(0);
        for (Entity e : all) if (e.getName().getString().equalsIgnoreCase(q)) return e;
        for (Entity e : all) if (e.getName().getString().toLowerCase(Locale.ROOT).startsWith(q)) return e;
        for (Entity e : all) {
            String id = typeId(e).toLowerCase(Locale.ROOT);
            String path = id.substring(id.indexOf(':') + 1);
            if (path.equals(q) || id.equals(q) || path.contains(q.replace(' ', '_')) || e.getType().getDescription().getString().equalsIgnoreCase(q)) return e;
        }
        return null;
    }

    public static boolean attackable(CompanionEntity c, Entity e) {
        return e instanceof LivingEntity l && l != c && !c.isOwner(l) && !(l instanceof CompanionEntity o && o.getOwnerId().equals(c.getOwnerId()));
    }
}
