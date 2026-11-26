package dev.yucktheslime.mcabodyshapes.body;

import com.mojang.serialization.Codec;
import dev.yucktheslime.mcabodyshapes.McaBodyShapes;
import java.util.function.Supplier;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class BodyShapeAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, McaBodyShapes.MOD_ID);

    /**
     * Saved with the entity (villager or player), copied on death, and synced to clients
     * that receive the entity so the renderer can scale the right model parts.
     */
    public static final Supplier<AttachmentType<BodyShape>> BODY_SHAPE = ATTACHMENT_TYPES.register(
            "body_shape",
            () -> AttachmentType.builder(() -> BodyShape.VANILLA)
                    .serialize(BodyShape.CODEC)
                    .sync(BodyShape.STREAM_CODEC)
                    .copyOnDeath()
                    .build()
    );

    /**
     * Server-side marker that a shape was finalized (random roll, command, or editor).
     * Used so a later load does not re-roll a player-reset vanilla shape, while still
     * letting 0.3.0 adults that were stuck on untouched defaults get a one-time roll.
     */
    public static final Supplier<AttachmentType<Boolean>> ASSIGNED = ATTACHMENT_TYPES.register(
            "body_shape_assigned",
            () -> AttachmentType.builder(() -> Boolean.FALSE)
                    .serialize(Codec.BOOL)
                    .copyOnDeath()
                    .build()
    );

    private BodyShapeAttachments() {
    }
}
