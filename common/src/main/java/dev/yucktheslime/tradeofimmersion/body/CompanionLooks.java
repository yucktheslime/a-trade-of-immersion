package dev.yucktheslime.tradeofimmersion.body;

import dev.architectury.platform.Platform;
import dev.yucktheslime.tradeofimmersion.TradeOfImmersion;
import dev.yucktheslime.tradeofimmersion.config.ToiConfig;
import dev.yucktheslime.tradeofimmersion.entity.CompanionEntity;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.network.c2s.VillagerEditorSyncRequest;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Server side: gives every companion an MCA villager look the way MCA gives one to a villager it spawns
 * ({@code VillagerLike.initialize}: genes from the biome, traits, then {@code initializeSkin}: body skin, clothes and
 * hair picked for the gender, sometimes dyed hair). The result is saved in the world's companion_bodies.json
 * ({@link CompanionBodyData#mca}, the same keys MCA's villager editor writes) and synced to every client, so all
 * players see the same companion. Existing companions get one the first time they tick after the update.
 *
 * <p>With MCA Body Shapes installed, the companion also gets a random body shape like that addon gives villagers.
 */
public final class CompanionLooks {
    /** Keys removed from the stored look (same list as {@code McaEditorBridge}). */
    private static final List<String> DROP = List.of("CustomName", "CustomNameVisible", "Age", "AgeState", "FamilyTreeNewFatherName",
            "FamilyTreeNewMotherName", "FamilyTreeNewSpouseName", "InfectionProgress");
    private static boolean dirty;
    private static Boolean bodyShapesMissing;
    private static Method bsHasSaved, bsRandom, bsSet;

    private CompanionLooks() {
    }

    /** {@code mca} or {@code player}: what a companion with no choice of its own uses (server config). */
    public static String defaultLook() {
        return "player_skin".equalsIgnoreCase(ToiConfig.get().companionAppearance) ? "player" : "mca";
    }

    public static String genderOf(CompanionEntity c, CompanionBodyData d) {
        String g = d != null && d.gender != null ? d.gender : c.getGender();
        if (g == null || g.isBlank()) g = ToiConfig.get().companionGender;
        return CompanionMcaData.genderName(CompanionMcaData.parseGender(g, Gender.MALE));
    }

    /** Called from the companion's server tick (cheap when nothing is missing). */
    public static void tick(CompanionEntity c) {
        if (!ToiConfig.get().companionMcaBody) return;
        CompanionBodyData d = CompanionBodyStore.get().find(c.getUUID());
        String gender = genderOf(c, d);
        boolean needLook = d == null || d.look == null;
        boolean needAppearance = d == null || d.lookGender == null || !d.lookGender.equals(gender)
                || d.mca == null || d.mca.getString("Clothes").isBlank();
        if (needLook || needAppearance) {
            try {
                d = CompanionBodyStore.get().getOrCreate(c.getUUID());
                if (d.look == null) d.look = defaultLook();
                if (needAppearance) generate(c, d, gender);
                d.name = c.name();
                dirty = true;
            } catch (Throwable t) {
                TradeOfImmersion.LOGGER.warn("[tradeofimmersion] Could not give companion {} an MCA look", c.getUUID(), t);
                // Mark it done for this gender so a broken MCA install doesn't retry every second.
                CompanionBodyData fail = CompanionBodyStore.get().getOrCreate(c.getUUID());
                if (fail.look == null) fail.look = defaultLook();
                fail.lookGender = gender;
                if (fail.mca == null) fail.mca = new CompoundTag();
                if (fail.mca.getString("Clothes").isBlank()) fail.mca.putString("Clothes", "mca:missing");
                dirty = true;
            }
        }
        if (d != null && d.mcaLook()) assignBodyShape(c);
    }

    /** Saves and syncs once per server tick when looks were added. */
    public static void flush(MinecraftServer server) {
        if (!dirty) return;
        dirty = false;
        CompanionBodyStore.get().save();
        CompanionBodySync.broadcast(server);
    }

    /**
     * New companion (no saved look): genes, traits, skin, clothes and hair like a newly spawned MCA villager.
     * A look set earlier with MCA's editor keeps its genes and traits; only blank clothes and hair are filled, and
     * clothes and hair are picked again when the gender changed.
     */
    static void generate(CompanionEntity c, CompanionBodyData d, String genderName) {
        Gender gender = CompanionMcaData.parseGender(genderName, Gender.MALE);
        boolean fresh = d.mca == null || d.mca.isEmpty();
        boolean genderChanged = d.lookGender != null && !d.lookGender.equals(genderName);
        VillagerEntityMCA v = CompanionMcaData.create(c.level(), c.getUUID(), gender, d, ToiConfig.get().companionBreastSize, null, null);
        if (v == null) throw new IllegalStateException("MCA could not create a villager record");
        v.moveTo(c.getX(), c.getY(), c.getZ()); // genes follow the biome like MCA's
        if (fresh) {
            v.getGenetics().randomize();
            v.getGenetics().setGender(gender);
            if (ToiConfig.get().companionBreastSize >= 0) {
                v.getGenetics().setGene(Genetics.BREAST, (float) Math.min(1.0, ToiConfig.get().companionBreastSize));
            }
            v.getTraits().randomize();
            v.initializeSkin(false);
        } else {
            if (genderChanged || v.getSkin().isBlank()) v.randomizeSkin();
            if (genderChanged || v.getClothes().isBlank()) v.forceRandomizeClothes();
            boolean hairBlank = true;
            for (String k : List.of("Hair", "HairStyle", "HairBase", "HairBangs", "HairBack", "HairFront", "HairExtra")) {
                if (!d.mca.getString(k).isBlank()) hairBlank = false;
            }
            if (genderChanged || hairBlank) v.randomizeHair();
        }
        CompoundTag full = new CompoundTag();
        v.saveWithoutId(full);
        CompoundTag stored = VillagerEditorSyncRequest.createEditorPatch(full);
        DROP.forEach(stored::remove);
        if (stored.getString("Clothes").isBlank()) stored.putString("Clothes", "mca:missing"); // pool empty: don't retry
        d.mca = stored;
        d.lookGender = genderName;
        // /toi body gene edits stay in d.genes and still win over the generated genes.
        TradeOfImmersion.LOGGER.info("[tradeofimmersion] Companion {} got an MCA look ({}, clothes {}, hair {})", c.name(), genderName,
                v.getClothes(), v.getHairStyleId());
    }

    /** MCA Body Shapes (optional, NeoForge): a random adult body like the addon gives MCA villagers. Reflection only. */
    private static void assignBodyShape(Entity e) {
        if (Boolean.TRUE.equals(bodyShapesMissing)) return;
        try {
            if (bodyShapesMissing == null) {
                bodyShapesMissing = !Platform.isModLoaded("mcabodyshapes");
                if (bodyShapesMissing) return;
                Class<?> bs = Class.forName("dev.yucktheslime.mcabodyshapes.body.BodyShape");
                bsHasSaved = bs.getMethod("hasSaved", Entity.class);
                bsRandom = bs.getMethod("random", RandomSource.class);
                bsSet = bs.getMethod("set", Entity.class, bs);
                TradeOfImmersion.LOGGER.info("[tradeofimmersion] MCA Body Shapes found: companions get a random body shape like villagers");
            }
            if (!(Boolean) bsHasSaved.invoke(null, e)) {
                bsSet.invoke(null, e, bsRandom.invoke(null, ((net.minecraft.world.entity.LivingEntity) e).getRandom()));
            }
        } catch (Throwable t) {
            bodyShapesMissing = true;
            TradeOfImmersion.LOGGER.warn("[tradeofimmersion] MCA Body Shapes is installed but its API changed; companions keep MCA's default body", t);
        }
    }
}
