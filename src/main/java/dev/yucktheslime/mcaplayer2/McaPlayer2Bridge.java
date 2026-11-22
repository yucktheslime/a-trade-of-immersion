package dev.yucktheslime.mcaplayer2;

import dev.yucktheslime.mcaplayer2.goal.TalkToMcaVillagerGoal;
import dev.yucktheslime.mcaplayer2.util.McaVillagerUtil;
import net.fabricmc.api.ModInitializer;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class McaPlayer2Bridge implements ModInitializer {
    public static final String MOD_ID = "mcaplayer2";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("MCA-Player2 Bridge initializing...");
        // Core pieces are in place:
        // - McaVillagerUtil for finding MCA villagers
        // - TalkToMcaVillagerGoal for approach + context injection
        // Next steps: hook into PlayerEngine companion tick / command system
        // and register a proper goal or task that Player2 companions can use.
    }

    /**
     * Example helper that a Player2 companion controller could call.
     */
    public static TalkToMcaVillagerGoal createTalkGoal(LivingEntity companion, double searchRadius) {
        return McaVillagerUtil.findNearestMcaVillager(companion, searchRadius)
                .map(villager -> new TalkToMcaVillagerGoal(companion, villager))
                .orElse(null);
    }
}
