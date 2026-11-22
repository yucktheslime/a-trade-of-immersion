package dev.yucktheslime.mcaplayer2.mixin;

import com.player2.playerengine.PlayerEngineCommands;
import com.player2.playerengine.PlayerEngineController;
import dev.yucktheslime.mcaplayer2.McaPlayer2Bridge;
import dev.yucktheslime.mcaplayer2.command.NearbyVillagersCommand;
import dev.yucktheslime.mcaplayer2.command.TalkToVillagerCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Registers the bridge's commands on every companion. PlayerEngine 1.21.1-1.4.0 registers its
 * built-in commands in the public static {@code PlayerEngineCommands.init(PlayerEngineController)}
 * via {@code controller.getCommandExecutor().registerNewCommand(...)} (verified by decompiling).
 * There is no public registration hook, hence the mixin.
 */
@Mixin(value = PlayerEngineCommands.class, remap = false)
public abstract class PlayerEngineCommandsMixin {
    @Inject(method = "init", at = @At("TAIL"), require = 0)
    private static void mcap2b$registerCommands(PlayerEngineController controller, CallbackInfo ci) {
        try {
            controller.getCommandExecutor().registerNewCommand(new TalkToVillagerCommand(), new NearbyVillagersCommand());
        } catch (Throwable t) {
            McaPlayer2Bridge.LOGGER.error("Failed to register MCA bridge commands", t);
        }
    }
}
