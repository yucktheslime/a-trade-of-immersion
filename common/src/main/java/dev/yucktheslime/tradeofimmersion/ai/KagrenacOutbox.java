package dev.yucktheslime.tradeofimmersion.ai;

import dev.yucktheslime.tradeofimmersion.config.ToiConfig;
import dev.yucktheslime.tradeofimmersion.util.QuietLog;

import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Log-only world events (deaths, kills, fights, gifts, villager talk...) on their way to Kagrenac's event.php.
 * The game thread only appends to a bounded queue; one background thread sends the events in order, one at a
 * time. While Kagrenac is unreachable (PC off, tunnel down) events stay queued and the sender retries with a
 * doubling wait (2 s up to {@code kagrenacRetryMaxSeconds}); beyond {@code kagrenacEventQueueSize} the oldest are
 * dropped. Problems are logged at most once a minute ({@link QuietLog}); nothing ever goes to chat.
 */
final class KagrenacOutbox {
    private static final ScheduledExecutorService SENDER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "toi-kagrenac-events");
        t.setDaemon(true);
        return t;
    });
    private static final String WARN_KEY = "kagrenac-events";

    private record Item(String type, String npcId, String npcName, String text, Map<String, String> ctx, long created) {
    }

    private final KagrenacBackend backend;
    private final Deque<Item> queue = new ArrayDeque<>();
    private boolean busy;
    private boolean closed;
    private long nextAttempt;
    private int failures;
    private long dropped, sent;
    private String lastProblem = "";

    KagrenacOutbox(KagrenacBackend backend) {
        this.backend = backend;
        SENDER.scheduleWithFixedDelay(this::pump, 1, 1, TimeUnit.SECONDS);
    }

    /** Game thread: queue one event. Never blocks, never throws. */
    void add(String type, String npcId, String npcName, String text, Map<String, String> ctx) {
        if (text == null || text.isBlank()) return;
        synchronized (this) {
            if (closed) return;
            int max = Math.max(10, ToiConfig.get().kagrenacEventQueueSize);
            while (queue.size() >= max) {
                queue.pollFirst();
                dropped++;
            }
            queue.addLast(new Item(type, npcId, npcName, text, ctx == null ? Map.of() : Map.copyOf(ctx), System.currentTimeMillis()));
        }
        SENDER.execute(this::pump);
    }

    /** This backend was replaced (config reload): stop sending and hand the waiting events to the new one. */
    void closeInto(KagrenacOutbox next) {
        Deque<Item> left;
        synchronized (this) {
            closed = true;
            left = new ArrayDeque<>(queue);
            queue.clear();
        }
        if (next == null || next == this) return;
        synchronized (next) {
            for (Item i : left) next.queue.addLast(i);
        }
    }

    synchronized String status() {
        String s = queue.size() + " queued, " + sent + " sent" + (dropped > 0 ? ", " + dropped + " dropped (queue full)" : "");
        if (failures > 0) s += ", retrying in " + Math.max(0, (nextAttempt - System.currentTimeMillis()) / 1000) + "s (" + lastProblem + ")";
        return s;
    }

    synchronized int size() {
        return queue.size();
    }

    /** Kagrenac looked unreachable the last time an event was tried. */
    synchronized boolean offline() {
        return failures > 0;
    }

    private void pump() {
        Item item;
        synchronized (this) {
            if (closed || busy || queue.isEmpty() || System.currentTimeMillis() < nextAttempt) return;
            busy = true;
            item = queue.peekFirst();
        }
        try {
            (backend.healthy() ? java.util.concurrent.CompletableFuture.completedFuture("") : backend.refreshHealth())
                    .thenCompose(info -> {
                        if (!backend.healthy()) throw new IllegalStateException("health: " + info);
                        if (!backend.acceptsEvent(item.type())) return java.util.concurrent.CompletableFuture.completedFuture(null);
                        return backend.sendEvent(item.type(), item.npcId(), item.npcName(), withAge(item), item.ctx());
                    })
                    .whenComplete((o, err) -> done(item, err));
        } catch (Throwable t) {
            done(item, t);
        }
    }

    private static String withAge(Item item) {
        long ms = System.currentTimeMillis() - item.created();
        return ms < 60_000 ? item.text() : item.text() + " (" + PromptBuilder.ago(ms) + ")";
    }

    private void done(Item item, Throwable err) {
        Throwable root = err;
        while (root != null && root.getCause() != null && root != root.getCause()) root = root.getCause();
        boolean retry;
        synchronized (this) {
            busy = false;
            if (root == null) {
                if (queue.peekFirst() == item) queue.pollFirst();
                sent++;
                if (failures > 0) {
                    failures = 0;
                    nextAttempt = 0;
                    QuietLog.clear(WARN_KEY);
                    dev.yucktheslime.tradeofimmersion.TradeOfImmersion.LOGGER.info("Kagrenac reachable again; delivered a queued event, {} more queued", queue.size());
                }
                retry = false;
            } else {
                String msg = root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
                // 400 bad_request and similar: this event will never be accepted, drop it and go on
                boolean permanent = msg.matches(".*HTTP 4(?!08|29|01|03|04)\\d\\d.*");
                if (permanent) {
                    if (queue.peekFirst() == item) queue.pollFirst();
                    dropped++;
                    QuietLog.warn(WARN_KEY + "-rejected", "Kagrenac rejected a {} event ({}); dropped it", item.type(), msg);
                    retry = false;
                } else {
                    failures++;
                    long wait = Math.min(Math.max(5, ToiConfig.get().kagrenacRetryMaxSeconds) * 1000L, 2000L << Math.min(failures - 1, 16));
                    nextAttempt = System.currentTimeMillis() + wait;
                    lastProblem = root instanceof ConnectException ? "connection refused"
                            : root instanceof HttpTimeoutException ? "timed out" : msg.length() > 80 ? msg.substring(0, 80) : msg;
                    QuietLog.warn(WARN_KEY, "Kagrenac unreachable at {} ({}); {} event(s) queued, retrying in {}s",
                            KagrenacBackend.safeUrl(ToiConfig.get().kagrenacBaseUrl), lastProblem, queue.size(), wait / 1000);
                    retry = false;
                }
            }
        }
        if (!retry) SENDER.execute(this::pump);
    }
}
