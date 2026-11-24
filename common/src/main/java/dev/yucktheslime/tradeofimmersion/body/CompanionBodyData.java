package dev.yucktheslime.tradeofimmersion.body;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-companion body overrides set with {@code /toi gender|body}. Gene keys are MCA Reborn's own
 * {@code Genetics.GeneType} names ({@code Size}, {@code Width}, {@code Breast}, {@code Melanin}, ...), values 0..1.
 */
public final class CompanionBodyData {
    /** {@code male}, {@code female}, {@code neutral}, or null (= config / character detection). */
    public String gender;
    /** Last known companion name (for display and tab completion only). */
    public String name;
    public final Map<String, Float> genes = new LinkedHashMap<>();
    /**
     * Appearance saved with MCA Reborn's villager editor, needle and thread or comb (the editor's own keys:
     * Hair*, Clothes, Skin, colors, Traits, Personality, genes, ...), or null. See {@code compat.McaEditorBridge}.
     */
    public CompoundTag mca;
    /**
     * {@code mca}: render with the MCA villager look from {@link #mca}; {@code player}: the companion's player skin
     * (opt-out with {@code /npc skin <npc> player}); null = the default (config companionAppearance, MCA since 0.3.7).
     */
    public String look;
    /** Gender the generated MCA clothes and hair were picked for (regenerated when the gender changes), or null. */
    public String lookGender;

    public boolean isEmpty() {
        return gender == null && genes.isEmpty() && (mca == null || mca.isEmpty()) && look == null && lookGender == null;
    }

    public boolean mcaLook() {
        return "mca".equals(look);
    }

    public boolean playerLook() {
        return "player".equals(look);
    }

    public CompoundTag toTag() {
        CompoundTag t = new CompoundTag();
        if (gender != null) t.putString("gender", gender);
        if (name != null) t.putString("name", name);
        CompoundTag g = new CompoundTag();
        genes.forEach(g::putFloat);
        t.put("genes", g);
        if (mca != null && !mca.isEmpty()) t.put("mca", mca.copy());
        if (look != null) t.putString("look", look);
        if (lookGender != null) t.putString("lookGender", lookGender);
        return t;
    }

    public static CompanionBodyData fromTag(CompoundTag t) {
        CompanionBodyData d = new CompanionBodyData();
        if (t.contains("gender")) d.gender = t.getString("gender");
        if (t.contains("name")) d.name = t.getString("name");
        CompoundTag g = t.getCompound("genes");
        for (String k : g.getAllKeys()) d.genes.put(k, g.getFloat(k));
        if (t.contains("mca", Tag.TAG_COMPOUND)) d.mca = t.getCompound("mca");
        if (t.contains("look")) d.look = t.getString("look");
        if (t.contains("lookGender")) d.lookGender = t.getString("lookGender");
        return d;
    }
}
