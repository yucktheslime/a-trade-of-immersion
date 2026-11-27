package dev.yucktheslime.mcabodyshapes.client;

import com.mojang.brigadier.context.CommandContext;
import dev.yucktheslime.mcabodyshapes.body.BodyShape;
import dev.yucktheslime.mcabodyshapes.compat.McaPhysicsCompat;
import dev.yucktheslime.mcabodyshapes.config.BodyShapeClientConfig;
import dev.yucktheslime.tradeofimmersion.body.physics.PhysicsGating;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * Client-only {@code /mcabody physics} status readout (config, mcabp gating, looked-at or self).
 * Physics run on each player's client, so this has to be a client command.
 */
public final class BodyPhysicsCommands {
    private BodyPhysicsCommands() {
    }

    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("mcabody")
                .then(Commands.literal("physics").executes(BodyPhysicsCommands::status)));
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        boolean toiOn = BodyShapeClientConfig.physicsEnabled();
        float bustI = BodyShapeClientConfig.bustIntensity();
        float seatI = BodyShapeClientConfig.seatIntensity();
        boolean mcabp = McaPhysicsCompat.isLoaded();
        Boolean toggle = McaPhysicsCompat.physicsEnabledOrNull();
        float bounce = McaPhysicsCompat.effectiveBounceScale();
        boolean drivesBust = PhysicsGating.toiDrivesBust(toiOn, mcabp, toggle);
        String mcabpPhysics = !mcabp ? "not installed"
                : toggle == null ? "on (config unread, treated as on)"
                : (toggle ? "on" : "off");
        source.sendSuccess(() -> Component.translatable(
                "commands.mcabodyshapes.physics",
                toiOn ? "on" : "off",
                bustI,
                PhysicsGating.bustIntensity(bustI, mcabp, bounce),
                seatI,
                mcabp ? "installed" : "not installed",
                mcabpPhysics,
                bounce,
                drivesBust ? "yes" : "no",
                mcabp ? "0 (no double bust)" : "from bust slider"
        ), false);

        Minecraft mc = Minecraft.getInstance();
        Entity hit = mc.crosshairPickEntity;
        if (hit == null) {
            hit = mc.player;
        }
        if (hit instanceof LivingEntity living) {
            BodyShape shape = BodyShape.get(living);
            boolean eligible = BodyPhysics.eligible(living);
            source.sendSuccess(() -> Component.translatable(
                    "commands.mcabodyshapes.physics_target",
                    living.getName(),
                    eligible ? "yes" : "no",
                    shape.bust(),
                    shape.seat()
            ), false);
        }
        return 1;
    }
}
