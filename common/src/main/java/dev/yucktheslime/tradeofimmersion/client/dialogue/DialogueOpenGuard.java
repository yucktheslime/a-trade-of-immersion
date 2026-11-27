package dev.yucktheslime.tradeofimmersion.client.dialogue;

/**
 * Opens the talk box after MCA's interaction screen closes, without losing it to MCA's follow-up
 * {@code setScreen(null)}.
 *
 * <p>{@code InteractScreen.onClose()} sets the screen to null and sends {@code InteractionCloseRequest}.
 * The server then calls {@code VillagerCommandHandler.stopInteracting()} → {@code ServerPlayer.closeContainer()},
 * which sends {@code ClientboundContainerClosePacket}. The client handles that with
 * {@code LocalPlayer.clientSideCloseContainer()}, which always calls {@code Minecraft.setScreen(null)} —
 * even when the player only had the inventory menu open. If the talk box was already on screen, that packet
 * closes it a tick later.
 *
 * <p>This guard defers opening by a couple of client ticks, then reopens the box once if something else
 * closes it in the first moments without the player closing it (Escape or Send call {@link #markUserClosed()}).
 * It also remembers when the talk key opened the box, so the leaked key character is not typed into the field.
 */
public final class DialogueOpenGuard {
    public static final int DEFER_TICKS = 2;
    public static final int REOPEN_WINDOW_TICKS = 10;

    public enum Action {
        IDLE, WAIT, OPEN, REOPEN
    }

    public static final class State {
        int pendingTicks = -1;
        int pendingEntityId = -1;
        boolean pendingHistory;
        boolean suppressTalkChar;
        int guardTicks;
        int guardEntityId = -1;
        boolean guardHistory;
        boolean reopenUsed;
        boolean userClosed;

        public boolean hasPending() {
            return pendingTicks >= 0;
        }

        public int pendingEntityId() {
            return pendingTicks >= 0 ? pendingEntityId : guardEntityId;
        }

        public boolean pendingHistory() {
            return pendingTicks >= 0 ? pendingHistory : guardHistory;
        }

        public int guardEntityId() {
            return guardEntityId;
        }

        public boolean guardHistory() {
            return guardHistory;
        }
    }

    private DialogueOpenGuard() {
    }

    /** Close MCA first, then call this instead of opening the talk box on the same tick. */
    public static void request(State s, int entityId, boolean history, boolean fromTalkKey) {
        s.pendingTicks = DEFER_TICKS;
        s.pendingEntityId = entityId;
        s.pendingHistory = history;
        s.suppressTalkChar = fromTalkKey;
        s.userClosed = false;
        s.reopenUsed = false;
        s.guardTicks = 0;
    }

    /**
     * One client tick. {@code dialogueOpen} is true when a {@link DialogueScreen} is the current screen.
     * {@link #markUserClosed(State)} must already have been called if the player closed that screen.
     */
    public static Action tick(State s, boolean dialogueOpen) {
        if (s.pendingTicks >= 0) {
            if (s.pendingTicks == 0) {
                s.pendingTicks = -1;
                s.guardTicks = REOPEN_WINDOW_TICKS;
                s.guardEntityId = s.pendingEntityId;
                s.guardHistory = s.pendingHistory;
                s.reopenUsed = false;
                s.userClosed = false;
                return Action.OPEN;
            }
            s.pendingTicks--;
            return Action.WAIT;
        }
        if (s.guardTicks > 0) {
            s.guardTicks--;
            if (!dialogueOpen && !s.userClosed && !s.reopenUsed) {
                s.reopenUsed = true;
                return Action.REOPEN;
            }
        }
        return Action.IDLE;
    }

    /** The player closed the talk box (Escape or Send). Do not reopen it. */
    public static void markUserClosed(State s) {
        s.userClosed = true;
        s.pendingTicks = -1;
        s.guardTicks = 0;
    }

    /**
     * Swallow characters while the talk key that opened the box is still held, so a leaked "v" is not typed
     * into the field. Clears itself once the key is released.
     */
    public static boolean suppressTalkChar(State s, boolean talkKeyDown) {
        if (!s.suppressTalkChar) return false;
        if (!talkKeyDown) {
            s.suppressTalkChar = false;
            return false;
        }
        return true;
    }
}
