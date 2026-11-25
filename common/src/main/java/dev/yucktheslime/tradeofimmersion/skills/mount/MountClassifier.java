package dev.yucktheslime.tradeofimmersion.skills.mount;

import dev.yucktheslime.tradeofimmersion.action.Targets;
import dev.yucktheslime.tradeofimmersion.entity.CompanionEntity;
import dev.yucktheslime.tradeofimmersion.skills.SkillsConfig;
import dev.yucktheslime.tradeofimmersion.skills.mount.MountRules.Decision;
import dev.yucktheslime.tradeofimmersion.skills.mount.MountRules.Facts;
import dev.yucktheslime.tradeofimmersion.skills.mount.MountRules.Kind;
import dev.yucktheslime.tradeofimmersion.util.OwnedEntityUtil;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Saddleable;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Strider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.ChestBoat;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Reads the facts {@link MountRules} needs from a real entity. */
public final class MountClassifier {
    private MountClassifier() {
    }

    public static Kind kind(Entity e) {
        if (e == null || e instanceof Player || e instanceof CompanionEntity || e instanceof VillagerEntityMCA || e instanceof EnderDragon) return Kind.NOT_RIDEABLE;
        if (e instanceof LivingEntity && isDragon(e)) return Kind.DRAGON;
        if (e instanceof Llama) return Kind.OTHER_MOB;
        if (e instanceof AbstractHorse) return Kind.HORSE_LIKE;
        if (e instanceof Pig || e instanceof Strider) return Kind.STICK_STEERED;
        if (e instanceof Boat) return Kind.BOAT;
        if (e instanceof AbstractMinecart cart) return cart.getMinecartType() == AbstractMinecart.Type.RIDEABLE ? Kind.MINECART : Kind.NOT_RIDEABLE;
        VehicleAdapter a = VehicleAdapters.find(e);
        if (a != null && a.vehicle()) return Kind.VEHICLE;
        if (!(e instanceof LivingEntity)) return Targets.looksRideable(e) ? Kind.VEHICLE : Kind.NOT_RIDEABLE;
        if (e instanceof Saddleable || Targets.looksRideable(e)) return Kind.OTHER_MOB;
        return Kind.NOT_RIDEABLE;
    }

    public static boolean isDragon(Entity e) {
        List<String> names = new ArrayList<>();
        for (Class<?> k = e.getClass(); k != null && k != Entity.class; k = k.getSuperclass()) names.add(k.getSimpleName());
        return MountRules.looksLikeDragon(Targets.typeId(e), names);
    }

    public static Facts facts(CompanionEntity c, Entity e) {
        Kind kind = kind(e);
        UUID owner = kind == Kind.NOT_RIDEABLE ? null : OwnedEntityUtil.ownerOf(e).orElse(null);
        boolean saddled = switch (kind) {
            case DRAGON, BOAT, MINECART, VEHICLE -> true;
            default -> e instanceof Saddleable s && s.isSaddled();
        };
        boolean playerAboard = e.getPassengers().stream().anyMatch(p -> p instanceof Player);
        int seats = e instanceof Boat ? (e instanceof ChestBoat ? 1 : 2) : e instanceof Camel ? 2 : kind == Kind.VEHICLE ? 4 : 1;
        long taken = e.getPassengers().stream().filter(p -> p != c).count();
        boolean seatFree = taken < seats;
        boolean steerItem = e instanceof Pig ? has(c, Items.CARROT_ON_A_STICK) : e instanceof Strider ? has(c, Items.WARPED_FUNGUS_ON_A_STICK) : false;
        boolean hostile = e instanceof Enemy;
        return new Facts(kind, owner, saddled, playerAboard, seatFree, steerItem, hostile);
    }

    public static Decision decide(CompanionEntity c, Entity e) {
        UUID companionOwner = c.getOwnerId().orElse(null);
        return MountRules.decide(facts(c, e), companionOwner, SkillsConfig.get().mountSettings());
    }

    public static boolean has(CompanionEntity c, Item item) {
        if (c.getMainHandItem().is(item) || c.getOffhandItem().is(item)) return true;
        for (int i = 0; i < c.inventory.getContainerSize(); i++) {
            ItemStack s = c.inventory.getItem(i);
            if (s.is(item)) return true;
        }
        return false;
    }
}
