package dev.yucktheslime.tradeofimmersion.budget;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class LlmQueueTest {
    @Test
    void unlimitedRunsImmediately() {
        LlmQueue q = new LlmQueue(0);
        CompletableFuture<String> inner = new CompletableFuture<>();
        CompletableFuture<String> out = q.submit(() -> inner);
        assertEquals(1, q.inFlight());
        assertEquals(0, q.queued());
        inner.complete("ok");
        assertEquals("ok", out.getNow(null));
        assertEquals(0, q.inFlight());
    }

    @Test
    void capQueuesAndDrainsInOrder() {
        LlmQueue q = new LlmQueue(2);
        List<CompletableFuture<Integer>> inners = new ArrayList<>();
        List<CompletableFuture<Integer>> outs = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            CompletableFuture<Integer> inner = new CompletableFuture<>();
            inners.add(inner);
            int n = i;
            outs.add(q.submit(() -> inner.thenApply(x -> n)));
        }
        assertEquals(2, q.inFlight());
        assertEquals(3, q.queued());
        assertFalse(outs.get(0).isDone());
        assertFalse(outs.get(2).isDone());

        inners.get(0).complete(0);
        assertTrue(outs.get(0).isDone());
        assertEquals(2, q.inFlight());
        assertEquals(2, q.queued());

        inners.get(1).complete(1);
        inners.get(2).complete(2);
        inners.get(3).complete(3);
        inners.get(4).complete(4);
        for (int i = 0; i < 5; i++) {
            assertEquals(i, outs.get(i).getNow(-1));
        }
        assertEquals(0, q.inFlight());
        assertEquals(0, q.queued());
    }

    @Test
    void neverExceedsCapEvenWhenCompletionsAreAsync() throws Exception {
        LlmQueue q = new LlmQueue(2);
        AtomicInteger current = new AtomicInteger();
        AtomicInteger max = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(5);
        CountDownLatch release = new CountDownLatch(1);
        List<CompletableFuture<Void>> outs = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            outs.add(q.submit(() -> CompletableFuture.supplyAsync(() -> {
                int now = current.incrementAndGet();
                max.updateAndGet(m -> Math.max(m, now));
                started.countDown();
                try {
                    release.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                current.decrementAndGet();
                return null;
            })));
        }
        // only two should be running; the latch of 5 will not open until we release
        assertTrue(started.await(200, TimeUnit.MILLISECONDS) == false || current.get() <= 2);
        Thread.sleep(50);
        assertEquals(2, q.inFlight());
        assertTrue(max.get() <= 2);
        release.countDown();
        CompletableFuture.allOf(outs.toArray(CompletableFuture[]::new)).get(2, TimeUnit.SECONDS);
        assertEquals(0, q.inFlight());
        assertTrue(max.get() <= 2);
        assertEquals(2, max.get());
    }

    @Test
    void failureReleasesSlotAndPropagates() {
        LlmQueue q = new LlmQueue(1);
        CompletableFuture<String> first = new CompletableFuture<>();
        CompletableFuture<String> a = q.submit(() -> first);
        CompletableFuture<String> b = q.submit(() -> CompletableFuture.completedFuture("second"));
        assertEquals(1, q.queued());
        first.completeExceptionally(new IllegalStateException("boom"));
        assertTrue(a.isCompletedExceptionally());
        assertEquals("second", b.getNow(null));
        assertEquals(0, q.inFlight());
        assertEquals(0, q.queued());
    }

    @Test
    void startThrowsReleasesSlot() {
        LlmQueue q = new LlmQueue(1);
        CompletableFuture<String> a = q.submit(() -> { throw new RuntimeException("nope"); });
        CompletableFuture<String> b = q.submit(() -> CompletableFuture.completedFuture("ok"));
        assertTrue(a.isCompletedExceptionally());
        assertEquals("ok", b.getNow(null));
        assertEquals(0, q.inFlight());
    }

    @Test
    void raisingLimitDrainsQueue() {
        LlmQueue q = new LlmQueue(1);
        CompletableFuture<Void> hold = new CompletableFuture<>();
        q.submit(() -> hold);
        CompletableFuture<String> waiting = q.submit(() -> CompletableFuture.completedFuture("go"));
        assertEquals(1, q.queued());
        q.setLimit(2);
        assertEquals("go", waiting.getNow(null));
        assertEquals(0, q.queued());
        hold.complete(null);
        assertEquals(0, q.inFlight());
    }
}
