package dev.yucktheslime.tradeofimmersion.memory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ConversationMemoryStoreTest {
    private static final UUID V = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

    @Test
    void unloadPersistsAndReloads(@TempDir Path dir) {
        ConversationMemoryStore store = ConversationMemoryStore.get();
        try {
            store.open(dir);
            store.append(V, "Ada", "Bob", ConversationMemoryStore.Speaker.COMPANION, "hi there", 10);
            store.append(V, "Ada", "Bob", ConversationMemoryStore.Speaker.VILLAGER, "hello Bob", 20);
            assertEquals(1, store.cacheSize());
            assertEquals(2, store.entryCount(V));

            store.unload(V);
            assertEquals(0, store.cacheSize());
            assertTrue(dir.resolve(V + ".json").toFile().isFile());

            List<String> lines = store.recentLines(V, "Bob", 10);
            assertEquals(1, store.cacheSize());
            assertEquals(List.of("Bob: hi there", "Ada: hello Bob"), lines);
        } finally {
            store.close();
            ConversationMemoryStore.onAccess = id -> { };
        }
    }
}
