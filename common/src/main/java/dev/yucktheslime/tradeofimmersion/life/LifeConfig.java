package dev.yucktheslime.tradeofimmersion.life;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.architectury.platform.Platform;
import dev.yucktheslime.tradeofimmersion.TradeOfImmersion;
import dev.yucktheslime.tradeofimmersion.life.pregnancy.PregnancyPhases;
import dev.yucktheslime.tradeofimmersion.life.relation.RelationshipMath;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Phase 2 "MCA life" settings, in their own file {@code config/tradeofimmersion-life.json} (same style as
 * tradeofimmersion.json: missing keys get defaults, the file is re-written on load). Every feature has a switch.
 * Reload with {@code /npc life reload}.
 */
public final class LifeConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static LifeConfig instance = new LifeConfig();

    /** Master switch for everything below. */
    public boolean enabled = true;

    // ---- MCA life hooks + memory
    /** Watch MCA villagers near players for marriages, engagements, births, pregnancies, deaths, gifts and mood changes. */
    public boolean lifeHooks = true;
    public int lifeScanSeconds = 10;
    public double lifeScanRadius = 96;
    /** Companions within this many blocks remember villager life events and gifts they saw. */
    public double lifeWitnessRadius = 24;
    /** Also send villager life events to Kagrenac as log-only world events. */
    public boolean lifeEventsToKagrenac = true;
    /** Mood changes are reported only when MCA's mood value moves by at least this much. */
    public int moodChangeThreshold = 15;

    // ---- pregnancy
    /** Pregnancy goes through phases instead of MCA's instant baby item (and villager-villager gestation uses them too). */
    public boolean pregnancyPhases = true;
    public int pregnancyPhaseCount = 3;
    /** In-game days per phase (one day = 20 real minutes). */
    public double pregnancyDaysPerPhase = 3.0;
    /** The "Pregnant" status effect and its side effects. Off = pregnancies still run, without effects. */
    public boolean pregnancyEffects = true;
    /** Slowness, hunger, nausea, mining fatigue by phase (needs pregnancyEffects). */
    public boolean pregnancySideEffects = true;
    /** Mood swings in dialogue (and MCA mood for villagers). */
    public boolean pregnancyMoodSwings = true;
    /** Miscarriage can happen (rare, mostly early). Off = every pregnancy ends in a birth. */
    public boolean miscarriage = true;
    /** Chance per in-game day by phase (first, second, third; the last value repeats for more phases). */
    public double[] miscarriageChancePerDay = {0.02, 0.006, 0.002};
    /** A player's pregnancy ends if they die. */
    public boolean playerPregnancyEndsOnDeath = true;
    /** The belly grows on MCA-model villagers, companions and players. */
    public boolean bellyVisual = true;
    /** How far the belly sticks out at full term, in model pixels. */
    public double bellyMaxDepth = 3.5;

    // ---- marriage
    /** AI companions can date, get engaged and marry (players, other companions, MCA villagers). */
    public boolean companionMarriage = true;
    /** Engagement/wedding rings and divorce papers work on companions. */
    public boolean ringsOnCompanions = true;
    /** Courtship stages: dating (MCA "promised") -> engaged -> married, tied to affection. Off = rings skip dating. */
    public boolean courtshipStages = true;
    /** Married companion couples (and companion-villager couples) can have children on their own. */
    public boolean npcProcreation = true;
    /** Chance per in-game day that a married NPC couple who are near each other conceive. */
    public double npcProcreationChancePerDay = 0.15;
    /** Minimum in-game days between pregnancies of the same carrier. */
    public double procreationCooldownDays = 3.0;

    // ---- relationships
    /** Affection, jealousy and drift between companions, players and villagers. */
    public boolean relationships = true;
    /** NPCs move through courtship on their own (date, propose, marry, break up, divorce) based on mutual affection. */
    public boolean autonomousRomance = true;
    /** A companion may ask its player to date/marry when affection is high. */
    public boolean companionProposesToPlayer = true;
    /** Neglect, jealousy and breakups can end marriages (divorce). */
    public boolean divorce = true;
    public double affectionChat = 1.0, affectionGift = 4.0, affectionFlirt = 3.0, affectionHurt = -15.0, affectionTimeTogetherPerDay = 2.0;
    public double driftGraceDays = 2.0, driftPerDay = 3.0, jealousyGain = 20.0, jealousyDecayPerDay = 10.0;
    public double datingAt = 40, proposeAt = 70, marryAt = 80, divorceBelow = -20, breakUpBelow = 5;
    /** NPCs within this range of each other count as spending time together. */
    public double togetherRadius = 12;

    // ---- family-tree restriction
    /**
     * Relatives who can't marry or date, by shared-ancestor depth: 0 = only parents/children/grandparents (the direct
     * line, always blocked), 1 = + siblings, 2 = + aunts/uncles, nieces/nephews and first cousins (default),
     * 3 = + second cousins and great-aunts/uncles, ...
     */
    public int kinBlockDepth = 2;

    public static LifeConfig get() {
        return instance;
    }

    public RelationshipMath.Params relationParams() {
        RelationshipMath.Params p = new RelationshipMath.Params();
        p.chat = affectionChat;
        p.gift = affectionGift;
        p.flirt = affectionFlirt;
        p.hurt = affectionHurt;
        p.timeTogetherPerDay = affectionTimeTogetherPerDay;
        p.driftGraceDays = driftGraceDays;
        p.driftPerDay = driftPerDay;
        p.jealousyGain = jealousyGain;
        p.jealousyDecayPerDay = jealousyDecayPerDay;
        p.datingAt = datingAt;
        p.proposeAt = proposeAt;
        p.marryAt = marryAt;
        p.divorceBelow = divorceBelow;
        p.breakUpBelow = breakUpBelow;
        return p;
    }

    public PregnancyPhases.Params pregnancyParams() {
        PregnancyPhases.Params p = new PregnancyPhases.Params();
        p.phases = Math.max(1, Math.min(12, pregnancyPhaseCount));
        p.ticksPerPhase = Math.max(200L, Math.round(pregnancyDaysPerPhase * PregnancyPhases.DAY));
        p.miscarriage = miscarriage;
        p.miscarriagePerDay = miscarriageChancePerDay == null ? new double[0] : miscarriageChancePerDay.clone();
        return p;
    }

    public boolean on(boolean feature) {
        return enabled && feature;
    }

    public static Path path() {
        return Platform.getConfigFolder().resolve("tradeofimmersion-life.json");
    }

    public static void load() {
        Path path = path();
        LifeConfig loaded = null;
        if (Files.exists(path)) {
            try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                loaded = GSON.fromJson(r, LifeConfig.class);
            } catch (Exception e) {
                TradeOfImmersion.LOGGER.warn("[tradeofimmersion] Bad {}, using defaults", path, e);
            }
        }
        instance = loaded != null ? loaded : new LifeConfig();
        try {
            Files.createDirectories(path.getParent());
            try (Writer w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(instance, w);
            }
        } catch (Exception e) {
            TradeOfImmersion.LOGGER.warn("[tradeofimmersion] Could not write {}", path, e);
        }
    }
}
