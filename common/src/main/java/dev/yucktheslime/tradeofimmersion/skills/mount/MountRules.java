package dev.yucktheslime.tradeofimmersion.skills.mount;

import java.util.UUID;

/**
 * Who may ride what (Phase 3 rules from the owner). Pure logic, no Minecraft types, unit tested.
 *
 * <ul>
 *   <li>A dragon owned by any player is never ridden, not even one owned by the companion's own player.</li>
 *   <li>An untamed or unowned dragon may be ridden ({@code allowWildDragons}).</li>
 *   <li>Tamed animals owned by a player (the companion's player or someone else) may be ridden if the companion wants to
 *       ({@code allowOwnersMounts} / {@code allowOthersMounts}).</li>
 *   <li>Bikes, cars and other vehicles from mods may be ridden ({@code allowVehicles}).</li>
 *   <li>A seat a player sits in, or a vehicle already full, is never taken.</li>
 * </ul>
 */
public final class MountRules {
    private MountRules() {
    }

    public enum Kind {
        /** Horse, donkey, mule, camel, skeleton/zombie horse: steerable when saddled. */
        HORSE_LIKE,
        /** Pig, strider: steerable when saddled (optionally with the steering item in the bag). */
        STICK_STEERED,
        BOAT,
        MINECART,
        /** Any entity whose type id or class name says dragon. */
        DRAGON,
        /** A non-mob vehicle from another mod (bike, car, plane, ship). */
        VEHICLE,
        /** Some other living mount (modded saddled mob, llama). Steering is attempted through its navigation. */
        OTHER_MOB,
        NOT_RIDEABLE
    }

    /** What the classifier found out about one entity. {@code ownerId} null = nobody owns it. */
    public record Facts(Kind kind, UUID ownerId, boolean saddled, boolean playerAboard, boolean seatFree,
                        boolean steerItemInBag, boolean hostile) {
    }

    public record Settings(boolean mountControl, boolean allowWildDragons, boolean allowOwnersMounts, boolean allowOthersMounts,
                           boolean allowVehicles, boolean requireSteerItem) {
        public static Settings defaults() {
            return new Settings(true, true, true, true, true, true);
        }
    }

    /**
     * @param allowed   may get on it
     * @param steerable can drive it somewhere once on (a free ride on an unsaddled horse is allowed but not steerable)
     * @param preference lower = nicer choice when the companion picks a vehicle for a trip (unowned / own player's first)
     */
    public record Decision(boolean allowed, boolean steerable, int preference, String reason) {
        static Decision no(String why) {
            return new Decision(false, false, Integer.MAX_VALUE, why);
        }
    }

    public static Decision decide(Facts f, UUID companionOwner, Settings s) {
        if (f == null || f.kind() == Kind.NOT_RIDEABLE) return Decision.no("not something you can ride");
        if (!s.mountControl()) return Decision.no("mount control is turned off");
        if (f.playerAboard()) return Decision.no("a player is riding it");
        if (!f.seatFree()) return Decision.no("no free seat");
        if (f.hostile() && f.kind() != Kind.DRAGON) return Decision.no("it is hostile");
        boolean owned = f.ownerId() != null;
        boolean ownersOwn = owned && f.ownerId().equals(companionOwner);
        if (f.kind() == Kind.DRAGON) {
            if (owned) return Decision.no("that dragon belongs to a player; never ride a player's dragon");
            if (!s.allowWildDragons()) return Decision.no("riding wild dragons is turned off");
            return new Decision(true, true, 3, "a wild dragon nobody owns");
        }
        if (owned) {
            if (ownersOwn && !s.allowOwnersMounts()) return Decision.no("riding your player's animals is turned off");
            if (!ownersOwn && !s.allowOthersMounts()) return Decision.no("it belongs to another player");
        }
        int pref = !owned ? 0 : ownersOwn ? 1 : 2;
        String whose = !owned ? "unowned" : ownersOwn ? "your player's" : "another player's";
        return switch (f.kind()) {
            case HORSE_LIKE -> f.saddled() ? new Decision(true, true, pref, whose + ", saddled")
                    : new Decision(true, false, pref + 10, whose + ", no saddle so it can't be steered");
            case STICK_STEERED -> {
                if (!f.saddled()) yield new Decision(true, false, pref + 10, whose + ", no saddle so it can't be steered");
                if (s.requireSteerItem() && !f.steerItemInBag()) yield new Decision(true, false, pref + 10, whose + ", needs a carrot/fungus on a stick to steer");
                yield new Decision(true, true, pref, whose + ", saddled");
            }
            case BOAT, MINECART -> new Decision(true, true, pref, whose);
            case VEHICLE -> s.allowVehicles() ? new Decision(true, true, pref, whose + " vehicle") : Decision.no("riding mod vehicles is turned off");
            case OTHER_MOB -> new Decision(true, f.saddled(), pref + (f.saddled() ? 0 : 10), whose + (f.saddled() ? "" : ", may not steer"));
            default -> Decision.no("not something you can ride");
        };
    }

    /** Dragon detection by name: entity type id path or any class name in its hierarchy containing "dragon". */
    public static boolean looksLikeDragon(String typeId, Iterable<String> classSimpleNames) {
        String id = typeId == null ? "" : typeId.toLowerCase(java.util.Locale.ROOT);
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        if (path.contains("fireball") || path.contains("breath") || path.contains("egg") || path.contains("part")) return false;
        if (path.contains("dragon")) return true;
        if (classSimpleNames != null) {
            for (String n : classSimpleNames) {
                String l = n == null ? "" : n.toLowerCase(java.util.Locale.ROOT);
                if (l.contains("dragon") && !l.contains("fireball") && !l.contains("part") && !l.contains("breath")) return true;
            }
        }
        return false;
    }
}
