package dev.yucktheslime.tradeofimmersion.memory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class NpcMemoryStoreTest {
    private static final UUID NPC = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    @Test
    void unloadPersistsAndReloads(@TempDir Path dir) {
        AtomicReference<UUID> touched = new AtomicReference<>();
        NpcMemoryStore store = NpcMemoryStore.get();
        NpcMemoryStore.onAccess = touched::set;
        try {
            store.open(dir);
            store.event(NPC, "death", "Steve died nearby");
            store.chat(NPC, "Steve", "hello");
            assertEquals(1, store.cacheSize());
            assertEquals(NPC, touched.get());

            store.unload(NPC);
            assertEquals(0, store.cacheSize());
            assertTrue(dir.resolve(NPC + ".json").toFile().isFile());

            List<NpcMemoryStore.Line> events = store.recentEvents(NPC, 10);
            List<NpcMemoryStore.Line> chat = store.recentChat(NPC, 10);
            assertEquals(1, store.cacheSize());
            assertEquals(1, events.size());
            assertEquals("death", events.get(0).who());
            assertEquals("Steve died nearby", events.get(0).text());
            assertEquals(1, chat.size());
            assertEquals("hello", chat.get(0).text());
        } finally {
            store.close();
            NpcMemoryStore.onAccess = id -> { };
        }
    }
}
