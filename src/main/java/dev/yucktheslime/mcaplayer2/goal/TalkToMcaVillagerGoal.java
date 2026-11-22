package dev.yucktheslime.mcaplayer2.goal;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Placeholder goal for a Player2 companion to approach and converse with an MCA villager.
 *
 * In a full implementation this would:
 * 1. Use Automatone / PlayerEngine pathing to move near the target villager
 * 2. Extract villager name, personality, relationship, and recent dialogue context
 * 3. Inject that context into the Player2 companion's prompt
 * 4. Optionally trigger an MCA dialogue response
 */
public class TalkToMcaVillagerGoal {
    private static final Logger LOGGER = LoggerFactory.getLogger("McaPlayer2Bridge");

    private final LivingEntity companion;
    private final LivingEntity targetVillager;
    private boolean finished;

    public TalkToMcaVillagerGoal(LivingEntity companion, LivingEntity targetVillager) {
        this.companion = companion;
        this.targetVillager = targetVillager;
    }

    public boolean isFinished() {
        return finished;
    }

    public void tick() {
        if (finished || targetVillager == null || !targetVillager.isAlive()) {
            finished = true;
            return;
        }

        Level level = companion.level();
        if (level.isClientSide) return;

        double distance = companion.distanceTo(targetVillager);

        if (distance > 4.0) {
            // TODO: Hand off to Automatone / PlayerEngine pathfinding
            LOGGER.debug("Companion needs to path closer to MCA villager ({} blocks)", distance);
        } else {
            // In range — build context and send to Player2
            String context = buildVillagerContext();
            LOGGER.info("Companion is in range. Injecting MCA context into Player2 prompt: {}", context);

            // TODO: Call Player2 / PlayerEngine conversation API with the injected context
            // Example: companionController.addSystemMessage(context);

            finished = true;
        }
    }

    private String buildVillagerContext() {
        // Placeholder — replace with real MCA API calls once dependency is present
        String name = targetVillager.getName().getString();
        return String.format(
                "You are talking to an MCA villager named %s. " +
                "They have their own personality and relationship with the player. " +
                "Speak naturally to them as another person in the world.",
                name
        );
    }
}
