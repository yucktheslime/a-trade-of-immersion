package dev.yucktheslime.mcaplayer2;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class McaPlayer2Bridge implements ModInitializer {
    public static final String MOD_ID = "mcaplayer2";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("MCA-Player2 Bridge initializing...");
        // TODO: Register goals, event hooks, and MCA villager targeting
    }
}
