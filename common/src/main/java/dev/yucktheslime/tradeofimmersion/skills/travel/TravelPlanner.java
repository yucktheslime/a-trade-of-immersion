package dev.yucktheslime.tradeofimmersion.skills.travel;

/** When a trip is long enough to fetch a mount or vehicle first. Pure logic, unit tested. */
public final class TravelPlanner {
    private TravelPlanner() {
    }

    public enum Mode { WALK, SEEK_VEHICLE, KEEP_RIDING }

    /**
     * @param distance       horizontal blocks to the destination
     * @param threshold      configured long-trip distance (default 150)
     * @param enabled        long-distance travel switch
     * @param riding         already on something
     * @param ridingSteerable what it rides can be driven
     */
    public static Mode plan(double distance, double threshold, boolean enabled, boolean riding, boolean ridingSteerable) {
        if (riding && ridingSteerable) return Mode.KEEP_RIDING;
        if (!enabled || threshold <= 0) return Mode.WALK;
        return distance > threshold ? Mode.SEEK_VEHICLE : Mode.WALK;
    }

    /**
     * Whether a vehicle {@code detour} blocks out of the way is worth it for a trip of {@code distance}: walking to it
     * must cost less than a third of the trip (riding is ~2-4x faster than walking).
     */
    public static boolean worthDetour(double distance, double detour) {
        return detour >= 0 && detour < distance / 3.0;
    }

    /** Pick-score for a candidate vehicle: preference class first (unowned, own player's, others'), then distance. */
    public static double score(int preference, double detour) {
        return preference * 1000.0 + detour;
    }
}
