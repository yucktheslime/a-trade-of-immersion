package dev.yucktheslime.tradeofimmersion.chatfilter;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NameMatcherTest {
    @Test
    void wholeWordNameAndAlias() {
        assertTrue(NameMatcher.mentions("hey Steve, follow me", "Steve", List.of()));
        assertTrue(NameMatcher.mentions("Steve", "Steve", List.of()));
        assertTrue(NameMatcher.mentions("talk to Old Tom please", "Old Tom", List.of()));
        assertTrue(NameMatcher.mentions("Tom, are you there?", "Old Tom", List.of()));
        assertTrue(NameMatcher.mentions("hi Sparky", "Wolf", List.of("Sparky")));
        assertFalse(NameMatcher.mentions("I like tomatoes", "Tom", List.of()));
        assertFalse(NameMatcher.mentions("hello there", "Steve", List.of()));
        assertFalse(NameMatcher.mentions("hey A, go", "A", List.of())); // token too short
    }

    @Test
    void accentInsensitive() {
        assertTrue(NameMatcher.mentions("hey Jose", "José", List.of()));
        assertTrue(NameMatcher.mentions("hey José", "Jose", List.of()));
    }

    @Test
    void groupVocativesOnlyAtStart() {
        var words = java.util.Set.of("everyone", "everybody", "guys", "team", "all of you");
        assertTrue(NameMatcher.isGroupAddress("everyone follow me", words));
        assertTrue(NameMatcher.isGroupAddress("Guys, come here", words));
        assertTrue(NameMatcher.isGroupAddress("all of you stay", words));
        assertTrue(NameMatcher.isGroupAddress("team:", words));
        assertFalse(NameMatcher.isGroupAddress("tell everyone hello", words));
        assertFalse(NameMatcher.isGroupAddress("all right, let's go", words));
        assertFalse(NameMatcher.isGroupAddress("almost there", words));
        assertFalse(NameMatcher.isGroupAddress("hey everyone", words));
    }
}
