package dev.yucktheslime.mcabodyshapes.network;

import dev.yucktheslime.mcabodyshapes.McaBodyShapes;
import dev.yucktheslime.mcabodyshapes.body.PregnancyWrites;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Client-to-server write of MCA pregnancy progress from the Shape tab slider.
 */
public record SetPregnancyPayload(UUID target, float progress) implements CustomPacketPayload {
    public static final Type<SetPregnancyPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(McaBodyShapes.MOD_ID, "set_pregnancy")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, SetPregnancyPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, SetPregnancyPayload::target,
            ByteBufCodecs.FLOAT, SetPregnancyPayload::progress,
            SetPregnancyPayload::new
    );

    @Override
    public Type<SetPregnancyPayload> type() {
        return TYPE;
    }

    public static void handle(SetPregnancyPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        Entity entity = player.serverLevel().getEntity(payload.target());
        if (!(entity instanceof LivingEntity living)) {
            return;
        }
        if (!SetBodyShapePayload.canEdit(player, living)) {
            return;
        }
        PregnancyWrites.apply(living, Mth.clamp(payload.progress(), 0.0f, 1.0f));
    }
}
