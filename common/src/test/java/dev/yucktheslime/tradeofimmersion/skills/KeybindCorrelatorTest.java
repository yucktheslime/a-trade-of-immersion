package dev.yucktheslime.tradeofimmersion.skills;

import dev.yucktheslime.tradeofimmersion.skills.item.KeybindCorrelator;
import dev.yucktheslime.tradeofimmersion.skills.item.KeybindCorrelator.Packet;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class KeybindCorrelatorTest {
    @Test
    void picksQuietChannelNearPress() {
        List<Packet> p = new ArrayList<>();
        p.add(new Packet("mymod:dash", 10_100, 0));
        var out = KeybindCorrelator.correlate(p, 10_000);
        assertEquals(1, out.size());
        assertEquals("mymod:dash", out.get(0).channel());
        // packet slightly before our key note (mod's tick ran first)
        assertEquals(1, KeybindCorrelator.correlate(List.of(new Packet("mymod:dash", 9_850, 0)), 10_000).size());
    }

    @Test
    void ignoresStreamsAndFarPackets() {
        List<Packet> p = new ArrayList<>();
        for (long t = 8_000; t <= 11_000; t += 50) p.add(new Packet("other:sync", t, 0));
        p.add(new Packet("mymod:late", 12_000, 0));
        p.add(new Packet("mymod:early", 9_000, 0));
        p.add(new Packet("mymod:dash", 10_200, 0));
        var out = KeybindCorrelator.correlate(p, 10_000);
        assertEquals(1, out.size());
        assertEquals("mymod:dash", out.get(0).channel());
    }

    @Test
    void burstInsideWindowIsAStream() {
        List<Packet> p = new ArrayList<>();
        for (long t = 10_000; t < 10_500; t += 100) p.add(new Packet("mod:hold", t, 0));
        assertTrue(KeybindCorrelator.correlate(p, 10_000).isEmpty());
    }
}
