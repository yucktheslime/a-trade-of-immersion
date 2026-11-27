package dev.yucktheslime.mcabodyshapes.client;

import com.mojang.brigadier.context.CommandContext;
import dev.yucktheslime.mcabodyshapes.body.BodyShape;
import dev.yucktheslime.mcabodyshapes.body.BustModels;
import dev.yucktheslime.mcabodyshapes.compat.McaPhysicsCompat;
import dev.yucktheslime.mcabodyshapes.config.BodyShapeClientConfig;
import dev.yucktheslime.tradeofimmersion.body.physics.PhysicsRules;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * Client-only {@code /mcabody physics} status readout: config, mcabp state, and for the entity you look at
 * (or yourself) its bust model, which jiggles run, and mcabp's own live view of it. Physics run on each
 * player's client, so this has to be a client command.
 */
public final class BodyPhysicsCommands {
    private BodyPhysicsCommands() {
    }

    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("mcabody")
                .then(Commands.literal("physics").executes(BodyPhysicsCommands::status)));
    }

    private static String onOff(Boolean value, String unread) {
        return value == null ? unread : value ? "on" : "off";
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        boolean toiOn = BodyShapeClientConfig.physicsEnabled();
        float bustI = BodyShapeClientConfig.bustIntensity();
        float seatI = BodyShapeClientConfig.seatIntensity();
        boolean mcabp = McaPhysicsCompat.isLoaded();
        String physics = !mcabp ? "-" : onOff(McaPhysicsCompat.physicsEnabledOrNull(), "unread, treated as on");
        String rendering = !mcabp ? "-" : onOff(McaPhysicsCompat.renderingEnabledOrNull(), "unread, treated as on");
        source.sendSuccess(() -> Component.translatable("commands.mcabodyshapes.physics",
                toiOn ? "on" : "off", bustI, seatI, mcabp ? "installed" : "not installed", physics, rendering), false);

        Minecraft mc = Minecraft.getInstance();
        Entity hit = mc.crosshairPickEntity != null ? mc.crosshairPickEntity : mc.player;
        if (hit instanceof LivingEntity living) {
            BodyShape shape = BodyShape.get(living);
            boolean eligible = BodyPhysics.eligible(living);
            boolean mcabpBust = BustModels.clientUsesMcabp(living);
            String stored = BustModels.stored(living);
            boolean toiBust = eligible && !mcabpBust && PhysicsRules.bustJiggles(toiOn, bustI, shape.bust());
            boolean toiSeat = eligible && PhysicsRules.seatJiggles(toiOn, seatI, shape.seat());
            source.sendSuccess(() -> Component.translatable("commands.mcabodyshapes.physics_target",
                    living.getName(), eligible ? "yes" : "no",
                    stored == null ? "toi (not rolled)" : stored,
                    mcabpBust ? "mcabp's bust" : "TOI's bust",
                    shape.bust(), shape.seat(),
                    toiBust ? "yes" : (mcabpBust ? "no (mcabp's own)" : "no"),
                    toiSeat ? "yes" : "no"), false);
            if (!(living instanceof Player)) {
                McaPhysicsCompat.McabpState state = McaPhysicsCompat.probeEntity(living);
                if (state != null) {
                    source.sendSuccess(() -> Component.translatable("commands.mcabodyshapes.physics_mcabp",
                            state.supported() ? "yes" : "no", state.gender(), state.bustSize(),
                            state.physicsY(), state.physicsX()), false);
                }
            }
        }
        return 1;
    }
}
