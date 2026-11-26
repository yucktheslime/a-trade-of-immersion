package dev.yucktheslime.mcabodyshapes.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.yucktheslime.mcabodyshapes.body.BodyShape;
import dev.yucktheslime.mcabodyshapes.body.BodyShapeLimits;
import dev.yucktheslime.mcabodyshapes.body.BodyShapeAge;
import dev.yucktheslime.mcabodyshapes.body.BodyShapeWrites;
import dev.yucktheslime.mcabodyshapes.body.BodyShapePresets;
import dev.yucktheslime.mcabodyshapes.body.PregnancyProgress;
import dev.yucktheslime.mcabodyshapes.body.PregnancyWrites;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class BodyShapeCommands {
    private static final SimpleCommandExceptionType NOT_LIVING = new SimpleCommandExceptionType(
            Component.translatable("commands.mcabodyshapes.not_living")
    );
    private static final SimpleCommandExceptionType NOT_ADULT = new SimpleCommandExceptionType(
            Component.translatable("commands.mcabodyshapes.not_adult")
    );
    private static final DynamicCommandExceptionType UNKNOWN_CHANNEL = new DynamicCommandExceptionType(
            value -> Component.translatable("argument.mcabodyshapes.channel.invalid", value)
    );
    private static final DynamicCommandExceptionType UNKNOWN_PRESET = new DynamicCommandExceptionType(
            value -> Component.translatable("argument.mcabodyshapes.preset.invalid", value)
    );
    private static final SuggestionProvider<CommandSourceStack> CHANNEL_SUGGESTIONS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(
                    java.util.stream.Stream.of(
                            java.util.stream.Stream.of("bust_size", "butt_size", "hip_width", "waist_width", "thigh_thickness"),
                            java.util.Arrays.stream(BodyShape.Channel.values()).map(BodyShape.Channel::alias)
                                    .filter(java.util.Objects::nonNull),
                            java.util.Arrays.stream(BodyShape.Channel.values()).map(BodyShape.Channel::key))
                            .flatMap(s -> s)
                            .distinct(),
                    builder
            );
    private static final SuggestionProvider<CommandSourceStack> PRESET_SUGGESTIONS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(BodyShapePresets.ids(), builder);

    private BodyShapeCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("mcabody")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("get")
                        .then(Commands.argument("target", EntityArgument.entity())
                                .executes(BodyShapeCommands::get)))
                .then(Commands.literal("set")
                        .then(Commands.argument("target", EntityArgument.entity())
                                .then(Commands.argument("channel", StringArgumentType.word())
                                        .suggests(CHANNEL_SUGGESTIONS)
                                        .then(Commands.argument("value", FloatArgumentType.floatArg(BodyShape.MIN, BodyShape.SEAT_MAX))
                                                .executes(BodyShapeCommands::set)))))
                .then(Commands.literal("preset")
                        .then(Commands.argument("target", EntityArgument.entity())
                                .then(Commands.argument("preset", StringArgumentType.word())
                                        .suggests(PRESET_SUGGESTIONS)
                                        .executes(BodyShapeCommands::preset))))
                .then(Commands.literal("pregnancy")
                        .then(Commands.argument("target", EntityArgument.entity())
                                .then(Commands.argument("progress", FloatArgumentType.floatArg(0.0f, 1.0f))
                                        .executes(BodyShapeCommands::pregnancy))))
                .then(Commands.literal("reset")
                        .then(Commands.argument("target", EntityArgument.entity())
                                .executes(BodyShapeCommands::reset))));
    }

    private static int get(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        LivingEntity target = living(ctx);
        BodyShape shape = BodyShape.get(target);
        float pregnancy = PregnancyProgress.of(target) * 100.0f;
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "commands.mcabodyshapes.get",
                target.getName(),
                shape.bust(), shape.hips(), shape.seat(), shape.waist(), shape.weight(),
                shape.muscle(), shape.shoulders(), shape.chest(), shape.gut(), shape.height(),
                shape.curves(), shape.thighs(),
                pregnancy
        ), false);
        return 1;
    }

    private static int set(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        LivingEntity target = living(ctx);
        requireAdult(target);
        String key = StringArgumentType.getString(ctx, "channel");
        BodyShape.Channel channel = BodyShape.Channel.byKey(key);
        if (channel == null) {
            throw UNKNOWN_CHANNEL.create(key);
        }
        float value = FloatArgumentType.getFloat(ctx, "value");
        // Clamped to the configured range; the reply shows the stored value.
        BodyShape updated = BodyShapeLimits.clamp(BodyShape.get(target).with(channel, value));
        apply(target, updated);
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "commands.mcabodyshapes.set",
                target.getName(),
                channel.key(),
                updated.get(channel)
        ), true);
        return 1;
    }

    private static int preset(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        LivingEntity target = living(ctx);
        requireAdult(target);
        String id = StringArgumentType.getString(ctx, "preset");
        BodyShape shape = BodyShapePresets.get(id);
        if (shape == null) {
            throw UNKNOWN_PRESET.create(id);
        }
        apply(target, shape);
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "commands.mcabodyshapes.preset",
                id,
                target.getName()
        ), true);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        LivingEntity target = living(ctx);
        requireAdult(target);
        apply(target, BodyShape.defaults());
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "commands.mcabodyshapes.reset",
                target.getName()
        ), true);
        return 1;
    }

    private static int pregnancy(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        LivingEntity target = living(ctx);
        requireAdult(target);
        float progress = FloatArgumentType.getFloat(ctx, "progress");
        if (!PregnancyWrites.apply(target, progress) && progress > 0.001f) {
            // Pregnancy runs on A Trade of Immersion's phases; with pregnancyPhases off there is none to set.
            ctx.getSource().sendFailure(Component.translatable("commands.mcabodyshapes.pregnancy_unavailable", target.getName()));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "commands.mcabodyshapes.pregnancy",
                target.getName(),
                PregnancyProgress.of(target) * 100.0f
        ), true);
        return 1;
    }

    private static void apply(LivingEntity target, BodyShape shape) {
        BodyShapeWrites.apply(target, shape);
    }

    private static void requireAdult(LivingEntity target) throws CommandSyntaxException {
        if (!BodyShapeAge.isShapeableAdult(target)) {
            throw NOT_ADULT.create();
        }
    }

    private static LivingEntity living(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Entity entity = EntityArgument.getEntity(ctx, "target");
        if (entity instanceof LivingEntity living) {
            return living;
        }
        throw NOT_LIVING.create();
    }
}
