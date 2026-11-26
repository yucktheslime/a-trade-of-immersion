package dev.yucktheslime.mcabodyshapes.network;

import dev.yucktheslime.mcabodyshapes.McaBodyShapes;
import dev.yucktheslime.mcabodyshapes.body.BodyShape;
import dev.yucktheslime.mcabodyshapes.body.BodyShapeAge;
import dev.yucktheslime.mcabodyshapes.body.BodyShapeWrites;
import net.conczin.mca.entity.VillagerLike;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Client-to-server write of a body shape from the villager editor.
 * The server validates the target and clamps values before saving.
 */
public record SetBodyShapePayload(UUID target, BodyShape shape) implements CustomPacketPayload {
    public static final Type<SetBodyShapePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(McaBodyShapes.MOD_ID, "set_body_shape")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, SetBodyShapePayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, SetBodyShapePayload::target,
            BodyShape.STREAM_CODEC, SetBodyShapePayload::shape,
            SetBodyShapePayload::new
    );

    /** A little longer than vanilla interaction range, to allow for lag. */
    private static final double MAX_EDIT_DISTANCE_SQR = 16.0 * 16.0;

    @Override
    public Type<SetBodyShapePayload> type() {
        return TYPE;
    }

    public static void handle(SetBodyShapePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        Entity entity = player.serverLevel().getEntity(payload.target());
        if (!(entity instanceof LivingEntity living)) {
            return;
        }
        if (!canEdit(player, living)) {
            return;
        }
        BodyShape shape = payload.shape();
        BodyShapeWrites.apply(living, shape);
    }

    /**
     * Mirrors who can use MCA's villager editor: the editor item works on a nearby
     * {@link VillagerLike} with no extra permission level, and the player can also
     * edit their own MCA-styled model. Kids and teens are rejected.
     */
    public static boolean canEdit(ServerPlayer player, LivingEntity target) {
        if (!BodyShapeAge.isShapeableAdult(target)) {
            return false;
        }
        if (target.getUUID().equals(player.getUUID())) {
            return true;
        }
        if (!(target instanceof VillagerLike<?>)) {
            return false;
        }
        return player.distanceToSqr(target) <= MAX_EDIT_DISTANCE_SQR;
    }
}
