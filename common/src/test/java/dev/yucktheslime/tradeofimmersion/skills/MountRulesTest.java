package dev.yucktheslime.tradeofimmersion.skills;

import dev.yucktheslime.tradeofimmersion.skills.mount.MountRules;
import dev.yucktheslime.tradeofimmersion.skills.mount.MountRules.Facts;
import dev.yucktheslime.tradeofimmersion.skills.mount.MountRules.Kind;
import dev.yucktheslime.tradeofimmersion.skills.mount.MountRules.Settings;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MountRulesTest {
    static final UUID OWNER = UUID.randomUUID();
    static final UUID OTHER = UUID.randomUUID();
    static final Settings S = Settings.defaults();

    static Facts f(Kind k, UUID owner, boolean saddled) {
        return new Facts(k, owner, saddled, false, true, false, false);
    }

    @Test
    void playerOwnedDragonIsNeverRidden() {
        assertFalse(MountRules.decide(f(Kind.DRAGON, OTHER, true), OWNER, S).allowed());
        // not even the companion's own player's dragon
        assertFalse(MountRules.decide(f(Kind.DRAGON, OWNER, true), OWNER, S).allowed());
        // and not with every other switch on
        Settings all = new Settings(true, true, true, true, true, false);
        assertFalse(MountRules.decide(f(Kind.DRAGON, OWNER, true), OWNER, all).allowed());
    }

    @Test
    void wildDragonMayBeRidden() {
        var d = MountRules.decide(f(Kind.DRAGON, null, false), OWNER, S);
        assertTrue(d.allowed());
        assertTrue(d.steerable());
        // hostile wild dragons too (e.g. one that drains)
        assertTrue(MountRules.decide(new Facts(Kind.DRAGON, null, false, false, true, false, true), OWNER, S).allowed());
        Settings noWild = new Settings(true, false, true, true, true, true);
        assertFalse(MountRules.decide(f(Kind.DRAGON, null, false), OWNER, noWild).allowed());
    }

    @Test
    void playerOwnedTamedAnimalsAllowedByChoice() {
        assertTrue(MountRules.decide(f(Kind.HORSE_LIKE, OWNER, true), OWNER, S).allowed());
        assertTrue(MountRules.decide(f(Kind.HORSE_LIKE, OTHER, true), OWNER, S).allowed());
        Settings noOthers = new Settings(true, true, true, false, true, true);
        assertFalse(MountRules.decide(f(Kind.HORSE_LIKE, OTHER, true), OWNER, noOthers).allowed());
        assertTrue(MountRules.decide(f(Kind.HORSE_LIKE, OWNER, true), OWNER, noOthers).allowed());
    }

    @Test
    void preferenceOrdersUnownedThenOwnThenOthers() {
        int unowned = MountRules.decide(f(Kind.HORSE_LIKE, null, true), OWNER, S).preference();
        int own = MountRules.decide(f(Kind.HORSE_LIKE, OWNER, true), OWNER, S).preference();
        int other = MountRules.decide(f(Kind.HORSE_LIKE, OTHER, true), OWNER, S).preference();
        assertTrue(unowned < own && own < other);
    }

    @Test
    void vehiclesBikesCars() {
        assertTrue(MountRules.decide(f(Kind.VEHICLE, OTHER, false), OWNER, S).allowed());
        Settings noVeh = new Settings(true, true, true, true, false, true);
        assertFalse(MountRules.decide(f(Kind.VEHICLE, null, false), OWNER, noVeh).allowed());
    }

    @Test
    void steering() {
        assertFalse(MountRules.decide(f(Kind.HORSE_LIKE, null, false), OWNER, S).steerable());
        assertTrue(MountRules.decide(f(Kind.HORSE_LIKE, null, false), OWNER, S).allowed());
        // pig: saddle + stick
        assertFalse(MountRules.decide(f(Kind.STICK_STEERED, null, true), OWNER, S).steerable());
        assertTrue(MountRules.decide(new Facts(Kind.STICK_STEERED, null, true, false, true, true, false), OWNER, S).steerable());
        Settings noStick = new Settings(true, true, true, true, true, false);
        assertTrue(MountRules.decide(f(Kind.STICK_STEERED, null, true), OWNER, noStick).steerable());
        assertTrue(MountRules.decide(f(Kind.BOAT, null, false), OWNER, S).steerable());
        assertTrue(MountRules.decide(f(Kind.MINECART, null, false), OWNER, S).steerable());
    }

    @Test
    void seatsAndSwitches() {
        assertFalse(MountRules.decide(new Facts(Kind.BOAT, null, false, true, true, false, false), OWNER, S).allowed());
        assertFalse(MountRules.decide(new Facts(Kind.BOAT, null, false, false, false, false, false), OWNER, S).allowed());
        assertFalse(MountRules.decide(new Facts(Kind.OTHER_MOB, null, true, false, true, false, true), OWNER, S).allowed());
        assertFalse(MountRules.decide(f(Kind.NOT_RIDEABLE, null, false), OWNER, S).allowed());
        Settings off = new Settings(false, true, true, true, true, true);
        assertFalse(MountRules.decide(f(Kind.BOAT, null, false), OWNER, off).allowed());
    }

    @Test
    void dragonDetection() {
        assertTrue(MountRules.looksLikeDragon("iceandfire:fire_dragon", List.of()));
        assertTrue(MountRules.looksLikeDragon("somemod:wyvern", List.of("WyvernEntity", "AbstractDragonEntity")));
        assertTrue(MountRules.looksLikeDragon("isleofberk:night_fury", List.of("NightFuryEntity", "ADragonBase")));
        assertFalse(MountRules.looksLikeDragon("minecraft:dragon_fireball", List.of("DragonFireball")));
        assertFalse(MountRules.looksLikeDragon("minecraft:horse", List.of("Horse", "AbstractHorse")));
        assertFalse(MountRules.looksLikeDragon("iceandfire:dragon_egg", List.of()));
    }
}
