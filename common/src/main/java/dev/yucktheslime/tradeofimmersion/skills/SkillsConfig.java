package dev.yucktheslime.tradeofimmersion.skills;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.architectury.platform.Platform;
import dev.yucktheslime.tradeofimmersion.TradeOfImmersion;
import dev.yucktheslime.tradeofimmersion.skills.mount.MountRules;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Phase 3 settings (jobs, habits, mounts, long trips, item learning), in their own file
 * {@code config/tradeofimmersion-skills.json} so they don't clash with the main config. Re-written on load so new
 * keys show up. Every feature has its own switch.
 */
public final class SkillsConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static SkillsConfig instance = new SkillsConfig();

    // ---- jobs
    /** Master switch for the farm/chop/mine/dig/fetch jobs. */
    public boolean jobsEnabled = true;
    /** Break blocks in mine/chop/dig jobs (the companion's fake player does it, so claim/protection mods apply). */
    public boolean miningEnabled = true;
    /** Place blocks from the bag under the companion to cross gaps while digging or bridging. */
    public boolean bridgingEnabled = true;
    /** Fetch may take items out of nearby chests and barrels. */
    public boolean fetchFromContainers = true;
    /** Largest job radius in blocks. */
    public int jobMaxRadius = 24;
    /** Most blocks one mine/chop job breaks. */
    public int mineMaxBlocks = 64;
    /** Longest tunnel / staircase / bridge one dig job makes. */
    public int digMaxLength = 48;
    /** Replant crops and saplings after harvesting / chopping when seeds are in the bag. */
    public boolean replant = true;

    // ---- habits
    /** Watch what each player does (farming, chopping, mining, building, fishing) and tell their companions. */
    public boolean habitLearning = true;
    /** Companions that are following start helping on their own when their player does something they often do. */
    public boolean habitAutoHelp = true;
    /** Score at which an activity counts as a habit (about this many recent actions). */
    public double habitThreshold = 12.0;
    /** In-game days for a habit score to halve. */
    public double habitHalfLifeDays = 3.0;
    /** Seconds between two unasked offers to help from the same companion. */
    public int habitHelpCooldownSeconds = 120;

    // ---- mounts
    /** Companions steer what they ride and follow the mount rules. */
    public boolean mountControl = true;
    /** May ride a dragon nobody owns. A dragon owned by a player is never ridden (not a setting). */
    public boolean allowWildDragons = true;
    /** May ride tamed animals that belong to the companion's own player. */
    public boolean allowOwnersMounts = true;
    /** May ride tamed animals that belong to other players. */
    public boolean allowOthersMounts = true;
    /** May ride bikes, cars, planes and ships from other mods. */
    public boolean allowVehicles = true;
    /** While following, get on an allowed mount nearby when the player rides off. */
    public boolean mountWhenOwnerRides = true;
    /** Pigs need a carrot on a stick and striders a warped fungus on a stick in the bag to be steered. */
    public boolean requireSteerItem = true;

    // ---- long trips
    /** On trips longer than longTravelDistance, look for a mount or vehicle nearby first. */
    public boolean longTravel = true;
    /** Blocks: trips longer than this make the companion look for a ride. */
    public double longTravelDistance = 150.0;
    /** How far around itself the companion looks for a ride. */
    public double vehicleSearchRadius = 32.0;
    /** Get off when the trip ends. */
    public boolean dismountOnArrival = false;

    // ---- item learning
    /** Read item tooltips ("right-click to ...", "press V to ...") and learn how to use items. */
    public boolean itemLearning = true;
    /** Ask the owner's client for the full tooltip (some mods only build tooltips on the client). */
    public boolean tooltipFromClient = true;
    /** Learn which packet a mod key sends: when a player presses a mod key while holding an item, remember the packet. */
    public boolean keybindObservation = true;
    /** Let companions replay learned key packets with their own fake player. */
    public boolean keybindReplay = true;
    /** Failed tries of one way of using an item before it is marked unusable. */
    public int maxUseAttempts = 3;

    public static SkillsConfig get() {
        return instance;
    }

    public MountRules.Settings mountSettings() {
        return new MountRules.Settings(mountControl, allowWildDragons, allowOwnersMounts, allowOthersMounts, allowVehicles, requireSteerItem);
    }

    public static Path path() {
        return Platform.getConfigFolder().resolve(TradeOfImmersion.MOD_ID + "-skills.json");
    }

    public static void load() {
        Path path = path();
        SkillsConfig loaded = null;
        if (Files.exists(path)) {
            try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                loaded = GSON.fromJson(r, SkillsConfig.class);
            } catch (Exception e) {
                TradeOfImmersion.LOGGER.error("Failed to read {}, using defaults", path, e);
            }
        }
        instance = loaded != null ? loaded : new SkillsConfig();
        try {
            Files.createDirectories(path.getParent());
            try (Writer w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(instance, w);
            }
        } catch (Exception e) {
            TradeOfImmersion.LOGGER.warn("Could not write config {}", path, e);
        }
    }
}
