package dev.yucktheslime.mcaplayer2.command;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.commands.base.ArgParser;
import com.player2.playerengine.commands.base.Command;
import com.player2.playerengine.commands.base.CommandException;
import dev.yucktheslime.mcaplayer2.util.McaVillagerUtil;
import dev.yucktheslime.mcaplayer2.util.VillagerProfile;
import dev.yucktheslime.mcaplayer2.memory.ConversationMemoryStore;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/**
 * LLM-callable PlayerEngine command: {@code nearby_villagers}. Returns (as command feedback to the
 * companion's LLM) the MCA villagers within 32 blocks with their profile and how many past
 * exchanges are in the bridge's memory log.
 */
public class NearbyVillagersCommand extends Command {
    private static final double RADIUS = 32.0;

    public NearbyVillagersCommand() throws CommandException {
        super("nearby_villagers", "List MCA villagers near you with their name, job, personality, mood and relationship.");
    }

    @Override
    protected void call(PlayerEngineController mod, ArgParser parser) {
        LivingEntity self = mod.getEntity();
        List<VillagerEntityMCA> villagers = McaVillagerUtil.villagersNear(self, RADIUS);
        if (villagers.isEmpty()) {
            finishWithInfo("There are no MCA villagers within " + (int) RADIUS + " blocks.");
            return;
        }
        StringBuilder sb = new StringBuilder("MCA villagers nearby:\n");
        int n = 0;
        for (VillagerEntityMCA v : villagers) {
            if (n++ >= 8) break;
            VillagerProfile p = McaVillagerUtil.profile(v, mod.getOwner());
            int past = ConversationMemoryStore.get().entryCount(v.getUUID());
            sb.append("- ").append(p.describe())
                    .append(" Distance: ").append((int) self.distanceTo(v)).append(" blocks.")
                    .append(past > 0 ? " You have " + past + " remembered exchanges with them." : "")
                    .append('\n');
        }
        finishWithInfo(sb.toString());
    }
}
