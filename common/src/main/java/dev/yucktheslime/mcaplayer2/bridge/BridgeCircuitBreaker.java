package dev.yucktheslime.mcaplayer2.bridge;

import dev.yucktheslime.mcaplayer2.McaPlayer2Bridge;
import dev.yucktheslime.mcaplayer2.config.BridgeConfig;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

/**
 * Stops the bridge from generating Player2 traffic after a billing / auth / quota failure, and rate-limits
 * the companion LLM turns the bridge triggers.
 *
 * <p>While open: no villager LLM requests (canned lines are used) and no villager lines/greetings are fed to
 * companions, so the bridge can't keep PlayerEngine retrying a request it can't pay for. Each trip logs one
 * warning to the server log; nothing is sent to chat.
 */
public final class BridgeCircuitBreaker {
    private static long openUntilMs;
    private static String lastReason;
    private static final Deque<Long> companionTurns = new ArrayDeque<>();

    private BridgeCircuitBreaker() {
    }

    public static synchronized boolean isOpen() {
        return System.currentTimeMillis() < openUntilMs;
    }

    public static synchronized void trip(String reason) {
        long now = System.currentTimeMillis();
        boolean wasOpen = now < openUntilMs;
        long cooldownMs = Math.max(10, BridgeConfig.get().billingFailureCooldownSeconds) * 1000L;
        openUntilMs = now + cooldownMs;
        lastReason = reason;
        if (!wasOpen) {
            McaPlayer2Bridge.LOGGER.warn("[mcaplayer2] Player2 billing/auth/quota problem ({}). Pausing bridge-triggered AI requests "
                    + "for {}s; villagers use canned lines meanwhile. Make sure the companion's owner is online and signed in to Player2 "
                    + "and has credit.", reason, cooldownMs / 1000);
        }
    }

    public static synchronized String lastReason() {
        return lastReason;
    }

    /** True if the text of an error/response looks like a billing, auth or quota failure. */
    public static boolean isBillingFailure(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (isBillingFailureText(c.getMessage())) return true;
        }
        return false;
    }

    public static boolean isBillingFailureText(String msg) {
        if (msg == null) return false;
        String m = msg.toLowerCase(Locale.ROOT);
        return m.contains("billing") || m.contains("no token") || m.contains("missing token") || m.contains("unauthorized")
                || m.contains("forbidden") || m.contains("payment") || m.contains("quota") || m.contains("budget_hard_limit")
                || m.contains("limit_reached") || m.contains("insufficient") || m.contains("joules")
                || m.matches(".*\\b(http\\s*)?(401|402|403|429)\\b.*");
    }

    /**
     * Global sliding-window limit for companion turns triggered by the bridge (villager lines / greetings).
     *
     * @return true if a turn may be triggered now (and records it)
     */
    public static synchronized boolean tryAcquireCompanionTurn() {
        int max = BridgeConfig.get().maxBridgeCompanionTurnsPerMinute;
        if (max <= 0) return true;
        long now = System.currentTimeMillis();
        while (!companionTurns.isEmpty() && now - companionTurns.peekFirst() > 60_000) companionTurns.removeFirst();
        if (companionTurns.size() >= max) return false;
        companionTurns.addLast(now);
        return true;
    }

    public static synchronized void reset() {
        openUntilMs = 0;
        lastReason = null;
        companionTurns.clear();
    }
}
