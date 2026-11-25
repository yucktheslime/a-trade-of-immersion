package dev.yucktheslime.tradeofimmersion.skills.job;

import dev.yucktheslime.tradeofimmersion.action.ActionExecutor;
import dev.yucktheslime.tradeofimmersion.entity.CompanionEntity;
import dev.yucktheslime.tradeofimmersion.skills.SkillTask;
import dev.yucktheslime.tradeofimmersion.skills.SkillsConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

/**
 * Fetches items: from the ground nearby, or from a chest/barrel nearby (if allowed), then brings them to the
 * companion's player (or keeps them in the bag when the player is gone).
 */
public class FetchTask extends SkillTask {
    enum Phase { FIND, TO_SOURCE, TO_OWNER }

    private final String query;
    private final int want;
    private final int radius;
    private Phase phase = Phase.FIND;
    @Nullable
    private BlockPos container;
    @Nullable
    private ItemEntity ground;
    private int got;
    private String from = "";

    public FetchTask(String query, int count, int radius) {
        super(20 * 60 * 4);
        this.query = query;
        this.want = Math.max(1, count);
        this.radius = radius;
    }

    @Override
    public Vec3 destination(CompanionEntity c) {
        if (phase == Phase.TO_OWNER && c.getOwner() != null) return c.getOwner().position();
        if (container != null) return Vec3.atCenterOf(container);
        if (ground != null) return ground.position();
        return null;
    }

    @Override
    protected boolean step(CompanionEntity c) {
        if (!(c.level() instanceof ServerLevel level)) return true;
        switch (phase) {
            case FIND -> {
                ground = findGround(c);
                if (ground == null && SkillsConfig.get().fetchFromContainers) container = findContainer(c, level);
                if (ground == null && container == null) {
                    event(c, "fetch: found no " + query + " lying around" + (SkillsConfig.get().fetchFromContainers ? " or in chests" : "") + " nearby");
                    return true;
                }
                phase = Phase.TO_SOURCE;
            }
            case TO_SOURCE -> {
                if (ground != null) {
                    if (!ground.isAlive()) {
                        phase = got > 0 ? Phase.TO_OWNER : Phase.FIND;
                        return false;
                    }
                    if (c.distanceToSqr(ground) < 2.0 * 2.0) {
                        ItemStack s = ground.getItem();
                        int take = Math.min(s.getCount(), want - got);
                        ItemStack rest = c.inventory.addItem(s.split(take));
                        s.grow(rest.getCount());
                        got += take - rest.getCount();
                        c.take(ground, take - rest.getCount());
                        if (s.isEmpty()) ground.discard();
                        from = "the ground";
                        ground = null;
                        phase = got >= want ? Phase.TO_OWNER : Phase.FIND;
                        if (got == 0) phase = Phase.TO_OWNER;
                    } else walkFar(c, ground.position(), 1.1);
                } else if (container != null) {
                    if (c.distanceToSqr(Vec3.atCenterOf(container)) < 3.0 * 3.0) {
                        BlockEntity be = level.getBlockEntity(container);
                        if (be instanceof Container inv) {
                            for (int i = 0; i < inv.getContainerSize() && got < want; i++) {
                                ItemStack s = inv.getItem(i);
                                if (!ActionExecutor.matches(s, query)) continue;
                                ItemStack taken = inv.removeItem(i, want - got);
                                ItemStack rest = c.inventory.addItem(taken);
                                got += taken.getCount() - rest.getCount();
                                if (!rest.isEmpty()) inv.setItem(i, rest);
                            }
                            inv.setChanged();
                            c.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                            from = be.getBlockState().getBlock().getName().getString();
                        }
                        container = null;
                        phase = Phase.TO_OWNER;
                    } else walkFar(c, Vec3.atCenterOf(container), 1.1);
                }
            }
            case TO_OWNER -> {
                Player owner = c.getOwner();
                if (got == 0) {
                    event(c, "fetch: couldn't take any " + query);
                    return true;
                }
                if (owner == null) {
                    event(c, "fetched " + got + "x " + query + " from " + from + " (kept in my bag, my player is away)");
                    return true;
                }
                if (c.distanceToSqr(owner) < 3.0 * 3.0) {
                    int given = 0;
                    for (int i = 0; i < c.inventory.getContainerSize() && given < got; i++) {
                        ItemStack s = c.inventory.getItem(i);
                        if (!ActionExecutor.matches(s, query)) continue;
                        ItemStack part = s.split(Math.min(s.getCount(), got - given));
                        int n = part.getCount();
                        if (!owner.getInventory().add(part) || !part.isEmpty()) c.spawnAtLocation(part);
                        given += n;
                    }
                    c.inventory.setChanged();
                    event(c, "fetched " + given + "x " + query + " from " + from + " for " + owner.getGameProfile().getName());
                    return true;
                }
                walkFar(c, owner.position(), 1.2);
            }
        }
        return false;
    }

    @Nullable
    private ItemEntity findGround(CompanionEntity c) {
        ItemEntity best = null;
        double bd = Double.MAX_VALUE;
        for (ItemEntity e : c.level().getEntitiesOfClass(ItemEntity.class, c.getBoundingBox().inflate(radius), e -> e.isAlive() && ActionExecutor.matches(e.getItem(), query))) {
            double d = c.distanceToSqr(e);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }

    @Nullable
    private BlockPos findContainer(CompanionEntity c, ServerLevel level) {
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        BlockPos o = c.blockPosition();
        int r = Math.min(radius, 16);
        for (int cx = (o.getX() - r) >> 4; cx <= (o.getX() + r) >> 4; cx++) {
            for (int cz = (o.getZ() - r) >> 4; cz <= (o.getZ() + r) >> 4; cz++) {
                var chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (var e : chunk.getBlockEntities().entrySet()) {
                    BlockPos p = e.getKey();
                    if (p.distSqr(o) > r * r || !(e.getValue() instanceof Container inv)) continue;
                    if (!(e.getValue() instanceof ChestBlockEntity) && !e.getValue().getType().builtInRegistryHolder().key().location().getPath().contains("barrel")
                            && !e.getValue().getType().builtInRegistryHolder().key().location().getPath().contains("chest")) continue;
                    boolean has = false;
                    for (int i = 0; i < inv.getContainerSize() && !has; i++) has = ActionExecutor.matches(inv.getItem(i), query);
                    if (!has) continue;
                    double d = p.distSqr(o);
                    if (d < bd) {
                        bd = d;
                        best = p;
                    }
                }
            }
        }
        return best;
    }

    @Override
    public String describe() {
        return "fetching " + query;
    }
}
