package dev.yucktheslime.tradeofimmersion.client.dialogue;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DialogueOpenGuardTest {

    @Test
    void defersThenOpensAndGuards() {
        DialogueOpenGuard.State s = new DialogueOpenGuard.State();
        DialogueOpenGuard.request(s, 42, false, true);
        assertTrue(s.hasPending());
        assertEquals(DialogueOpenGuard.Action.WAIT, DialogueOpenGuard.tick(s, false));
        assertEquals(DialogueOpenGuard.Action.WAIT, DialogueOpenGuard.tick(s, false));
        assertEquals(DialogueOpenGuard.Action.OPEN, DialogueOpenGuard.tick(s, false));
        assertFalse(s.hasPending());
        assertEquals(42, s.guardEntityId());
        assertFalse(s.guardHistory());
    }

    @Test
    void reopensOnceIfClosedWithoutUserAction() {
        DialogueOpenGuard.State s = openNow(7, true, false);
        assertEquals(DialogueOpenGuard.Action.IDLE, DialogueOpenGuard.tick(s, true));
        assertEquals(DialogueOpenGuard.Action.REOPEN, DialogueOpenGuard.tick(s, false));
        // still in the window, but only once
        assertEquals(DialogueOpenGuard.Action.IDLE, DialogueOpenGuard.tick(s, false));
        assertEquals(DialogueOpenGuard.Action.IDLE, DialogueOpenGuard.tick(s, false));
        assertEquals(7, s.guardEntityId());
        assertTrue(s.guardHistory());
    }

    @Test
    void doesNotReopenAfterUserCloses() {
        DialogueOpenGuard.State s = openNow(3, false, false);
        DialogueOpenGuard.markUserClosed(s);
        assertEquals(DialogueOpenGuard.Action.IDLE, DialogueOpenGuard.tick(s, false));
        assertEquals(DialogueOpenGuard.Action.IDLE, DialogueOpenGuard.tick(s, false));
    }

    @Test
    void reopenWindowExpires() {
        DialogueOpenGuard.State s = openNow(9, false, false);
        for (int i = 0; i < DialogueOpenGuard.REOPEN_WINDOW_TICKS; i++) {
            assertEquals(DialogueOpenGuard.Action.IDLE, DialogueOpenGuard.tick(s, true));
        }
        // closed after the window: leave it closed
        assertEquals(DialogueOpenGuard.Action.IDLE, DialogueOpenGuard.tick(s, false));
    }

    @Test
    void suppressTalkCharUntilKeyReleased() {
        DialogueOpenGuard.State s = new DialogueOpenGuard.State();
        DialogueOpenGuard.request(s, 1, false, true);
        assertTrue(DialogueOpenGuard.suppressTalkChar(s, true));
        assertTrue(DialogueOpenGuard.suppressTalkChar(s, true));
        assertFalse(DialogueOpenGuard.suppressTalkChar(s, false));
        assertFalse(DialogueOpenGuard.suppressTalkChar(s, true)); // already cleared
    }

    @Test
    void mouseOpenDoesNotSuppressChars() {
        DialogueOpenGuard.State s = new DialogueOpenGuard.State();
        DialogueOpenGuard.request(s, 1, false, false);
        assertFalse(DialogueOpenGuard.suppressTalkChar(s, true));
    }

    @Test
    void userCloseCancelsPendingOpen() {
        DialogueOpenGuard.State s = new DialogueOpenGuard.State();
        DialogueOpenGuard.request(s, 5, false, true);
        DialogueOpenGuard.markUserClosed(s);
        assertFalse(s.hasPending());
        assertEquals(DialogueOpenGuard.Action.IDLE, DialogueOpenGuard.tick(s, false));
    }

    private static DialogueOpenGuard.State openNow(int id, boolean history, boolean fromTalkKey) {
        DialogueOpenGuard.State s = new DialogueOpenGuard.State();
        DialogueOpenGuard.request(s, id, history, fromTalkKey);
        DialogueOpenGuard.Action a;
        do {
            a = DialogueOpenGuard.tick(s, false);
        } while (a == DialogueOpenGuard.Action.WAIT);
        assertEquals(DialogueOpenGuard.Action.OPEN, a);
        return s;
    }
}
