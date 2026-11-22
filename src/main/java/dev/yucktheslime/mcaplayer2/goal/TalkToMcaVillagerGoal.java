package dev.yucktheslime.mcaplayer2.goal;

import com.player2.playerengine.player2api.AgentConversationData;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.movement.GetToEntityTask;
import com.player2.playerengine.util.helpers.LookHelper;
import dev.yucktheslime.mcaplayer2.bridge.ConversationBridge;
import dev.yucktheslime.mcaplayer2.config.BridgeConfig;
import dev.yucktheslime.mcaplayer2.player2.CompanionAdapter;
import dev.yucktheslime.mcaplayer2.util.McaVillagerUtil;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.entity.LivingEntity;

import java.util.Optional;

/**
 * PlayerEngine task ("goal") for a Player2 companion: approach an MCA villager, stay in range,
 * face it, and hold a conversation.
 *
 * <ol>
 *   <li><b>Approach</b> - while farther than {@code talkArrivalDistance}, returns PlayerEngine's
 *       {@link GetToEntityTask} as a sub-task, which drives the Automatone/Baritone pathfinder.</li>
 *   <li><b>Open</b> - on arrival calls {@link ConversationBridge#beginConversation}, which injects the
 *       villager's MCA data + remembered exchanges into the companion's Player2 prompt and makes the
 *       villager greet it.</li>
 *   <li><b>Stay</b> - keeps facing the villager ({@link LookHelper#lookAt(com.player2.playerengine.PlayerEngineController, net.minecraft.world.phys.Vec3)})
 *       and re-paths if it wanders beyond {@code talkStayInRangeDistance}. Finishes after
 *       {@code talkIdleTimeoutTicks} without a new line, or {@code talkMaxDurationTicks} total.</li>
 * </ol>
 *
 * <p>PlayerEngine API used here was verified with javap/CFR against PlayerEngine 1.21.1-1.4.0:
 * {@code Task} (abstract {@code onStart/onTick/onStop/isEqual/toDebugString}, public field
 * {@code controller}, {@code fail(String)}, {@code setDebugState(String)}, overridable {@code isFinished()}),
 * {@code GetToEntityTask(Entity, double)} and {@code LookHelper.lookAt(PlayerEngineController, Vec3)}
 * (the same call PlayerEngine's own {@code LookAtOwnerTask} uses).
 *
 * <p>Started by the LLM-callable {@code talk_to_villager <name|uuid>} command, or from code via
 * {@code controller.runUserTask(new TalkToMcaVillagerGoal(...))}.
 */
public class TalkToMcaVillagerGoal extends Task {
    private static final double SEARCH_RADIUS = 64.0;

    private final String target;
    private VillagerEntityMCA villager;
    private boolean opened;
    private boolean finished;
    private long openedAt;

    /** @param target villager UUID string or (part of) its MCA name; underscores are treated as spaces */
    public TalkToMcaVillagerGoal(String target) {
        this.target = target;
    }

    /**
     * Kept from the original scaffold. The companion is implied by the PlayerEngine controller
     * that runs this task, so {@code companion} is only used for logging/compatibility.
     */
    public TalkToMcaVillagerGoal(LivingEntity companion, LivingEntity targetVillager) {
        this(targetVillager.getStringUUID());
        if (targetVillager instanceof VillagerEntityMCA v) this.villager = v;
    }

    @Override
    protected void onStart() {
        opened = false;
        finished = false;
        if (villager == null || !villager.isAlive()) resolve();
    }

    private void resolve() {
        LivingEntity self = controller.getEntity();
        if (self == null || self.getServer() == null) return;
        Optional<VillagerEntityMCA> found = McaVillagerUtil.find(self.getServer(), self, target, SEARCH_RADIUS);
        villager = found.orElse(null);
    }

    @Override
    protected Task onTick() {
        if (finished) return null;
        if (villager == null || !villager.isAlive() || villager.isRemoved()) {
            resolve();
            if (villager == null) {
                if (opened) {
                    finished = true; // villager left/died mid-conversation
                } else {
                    fail("No MCA villager named or with UUID '" + target + "' within " + (int) SEARCH_RADIUS + " blocks");
                }
                return null;
            }
        }

        BridgeConfig cfg = BridgeConfig.get();
        LivingEntity self = controller.getEntity();
        double dist = self.distanceTo(villager);
        String vName = McaVillagerUtil.name(villager);

        if (!opened) {
            if (dist > cfg.talkArrivalDistance + 0.5) {
                setDebugState("Walking to " + vName);
                return new GetToEntityTask(villager, cfg.talkArrivalDistance);
            }
            LookHelper.lookAt(controller, villager.getEyePosition());
            setDebugState("Greeting " + vName);
            ConversationBridge.get().beginConversation(CompanionAdapter.dataFor(controller), villager, true);
            opened = true;
            openedAt = self.level().getGameTime();
            return null;
        }

        long now = self.level().getGameTime();
        AgentConversationData data = CompanionAdapter.dataFor(controller);
        long last = Math.max(openedAt, ConversationBridge.get().lastActivity(data, villager));
        if (now - last > cfg.talkIdleTimeoutTicks || now - openedAt > cfg.talkMaxDurationTicks) {
            finished = true;
            return null;
        }
        if (dist > cfg.talkStayInRangeDistance) {
            setDebugState("Following " + vName + " to keep talking");
            return new GetToEntityTask(villager, cfg.talkArrivalDistance);
        }
        setDebugState("Talking to " + vName);
        LookHelper.lookAt(controller, villager.getEyePosition());
        return null;
    }

    @Override
    public boolean isFinished() {
        return finished;
    }

    @Override
    protected void onStop(Task interruptTask) {
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof TalkToMcaVillagerGoal t && t.target.equalsIgnoreCase(target);
    }

    @Override
    protected String toDebugString() {
        return "Talk to MCA villager " + target;
    }
}
