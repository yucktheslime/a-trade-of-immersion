package dev.yucktheslime.tradeofimmersion.item;

import dev.yucktheslime.tradeofimmersion.entity.CompanionEntity;
import dev.yucktheslime.tradeofimmersion.entity.Companions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Spawns a companion owned by whoever uses it. Rename the egg in an anvil to choose the name. */
public class CompanionSpawnEggItem extends Item {
    public CompanionSpawnEggItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!(ctx.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        BlockPos pos = ctx.getClickedPos().relative(ctx.getClickedFace());
        ItemStack stack = ctx.getItemInHand();
        String name = stack.has(DataComponents.CUSTOM_NAME) ? stack.getHoverName().getString() : null;
        CompanionEntity c = Companions.spawn(level, Vec3.atBottomCenterOf(pos), ctx.getPlayer(), name, null, null);
        c.say("Hello" + (ctx.getPlayer() != null ? " " + ctx.getPlayer().getGameProfile().getName() : "") + "! I'm " + c.name() + ".");
        if (ctx.getPlayer() == null || !ctx.getPlayer().getAbilities().instabuild) stack.shrink(1);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.tradeofimmersion.companion_spawn_egg.desc").withStyle(s -> s.withColor(0xAAAAAA)));
    }
}
