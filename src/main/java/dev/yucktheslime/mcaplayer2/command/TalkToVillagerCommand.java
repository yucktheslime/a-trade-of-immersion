package dev.yucktheslime.mcaplayer2.command;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.commands.base.Arg;
import com.player2.playerengine.commands.base.ArgParser;
import com.player2.playerengine.commands.base.Command;
import com.player2.playerengine.commands.base.CommandException;
import dev.yucktheslime.mcaplayer2.goal.TalkToMcaVillagerGoal;

/**
 * LLM-callable PlayerEngine command: {@code talk_to_villager <name|uuid>}.
 * Registered into every companion's CommandExecutor by {@code PlayerEngineCommandsMixin}, so it
 * shows up in the command list PlayerEngine puts in the companion's system prompt.
 *
 * <p>Pattern copied from PlayerEngine's own {@code FollowCommand}: {@code super(name, description, Arg...)},
 * {@code parser.get(String.class)}, {@code mod.runUserTask(Task, Runnable)} and {@code finish()}.
 */
public class TalkToVillagerCommand extends Command {
    public TalkToVillagerCommand() throws CommandException {
        super("talk_to_villager",
                "Walk up to an MCA villager and start a conversation with them. Use the villager's name "
                        + "(spaces as underscores) or UUID. Example: `talk_to_villager Alice`. Use `nearby_villagers` to see names.",
                new Arg<>(String.class, "villager"));
    }

    @Override
    protected void call(PlayerEngineController mod, ArgParser parser) throws CommandException {
        String target = parser.get(String.class);
        if (target == null || target.isBlank()) {
            finishWithError("Missing villager name or UUID");
            return;
        }
        mod.runUserTask(new TalkToMcaVillagerGoal(target), this::finish);
    }
}
