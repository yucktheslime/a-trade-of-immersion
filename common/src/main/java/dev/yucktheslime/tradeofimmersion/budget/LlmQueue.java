package dev.yucktheslime.tradeofimmersion.budget;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Limits how many LLM / AI-backend requests may run at once. Extra work waits in a FIFO queue
 * and starts when a slot is released. {@code limit <= 0} means unlimited (the request runs immediately).
 *
 * <p>This class has no Minecraft types and is unit-tested. The live server uses {@link #get()}.
 */
public final class LlmQueue {
    private static final LlmQueue INSTANCE = new LlmQueue();

    private final ArrayDeque<Runnable> queue = new ArrayDeque<>();
    private int limit;
    private int inFlight;

    public LlmQueue() {
        this(0);
    }

    public LlmQueue(int limit) {
        this.limit = limit;
    }

    public static LlmQueue get() {
        return INSTANCE;
    }

    public synchronized int limit() {
        return limit;
    }

    public synchronized int inFlight() {
        return inFlight;
    }

    public synchronized int queued() {
        return queue.size();
    }

    /** Replace the concurrency cap; starts waiting work if the new cap has room (or is unlimited). */
    public void setLimit(int newLimit) {
        List<Runnable> start = new ArrayList<>();
        synchronized (this) {
            limit = newLimit;
            drainLocked(start);
        }
        start.forEach(Runnable::run);
    }

    public synchronized void reset() {
        queue.clear();
        inFlight = 0;
    }

    /**
     * Runs {@code start} when a slot is free. The slot is held until the future {@code start}
     * returns completes (success, failure or cancel). {@code start} itself should not block.
     */
    public <T> CompletableFuture<T> submit(Supplier<CompletableFuture<T>> start) {
        CompletableFuture<T> out = new CompletableFuture<>();
        offer(() -> bind(start, out));
        return out;
    }

    private <T> void bind(Supplier<CompletableFuture<T>> start, CompletableFuture<T> out) {
        CompletableFuture<T> inner;
        try {
            inner = start.get();
        } catch (Throwable t) {
            out.completeExceptionally(t);
            end();
            return;
        }
        if (inner == null) {
            out.complete(null);
            end();
            return;
        }
        inner.whenComplete((v, e) -> {
            try {
                if (e != null) out.completeExceptionally(e);
                else out.complete(v);
            } finally {
                end();
            }
        });
    }

    private void offer(Runnable task) {
        boolean runNow;
        synchronized (this) {
            if (unlimitedLocked() || inFlight < limit) {
                inFlight++;
                runNow = true;
            } else {
                queue.addLast(task);
                runNow = false;
            }
        }
        if (runNow) task.run();
    }

    private void end() {
        List<Runnable> start = new ArrayList<>(1);
        synchronized (this) {
            if (inFlight > 0) inFlight--;
            drainLocked(start);
        }
        start.forEach(Runnable::run);
    }

    private boolean unlimitedLocked() {
        return limit <= 0;
    }

    private void drainLocked(List<Runnable> start) {
        while (!queue.isEmpty() && (unlimitedLocked() || inFlight < limit)) {
            inFlight++;
            start.add(queue.pollFirst());
        }
    }
}
