package dev.yucktheslime.mcaplayer2.body;

import net.minecraft.nbt.CompoundTag;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-companion body overrides set with {@code /mcaplayer2 gender|body}. Gene keys are MCA Reborn's own
 * {@code Genetics.GeneType} names ({@code Size}, {@code Width}, {@code Breast}, {@code Melanin}, ...), values 0..1.
 */
public final class CompanionBodyData {
    /** {@code male}, {@code female}, {@code neutral}, or null (= config / character detection). */
    public String gender;
    /** Last known companion name (for display and tab completion only). */
    public String name;
    public final Map<String, Float> genes = new LinkedHashMap<>();

    public boolean isEmpty() {
        return gender == null && genes.isEmpty();
    }

    public CompoundTag toTag() {
        CompoundTag t = new CompoundTag();
        if (gender != null) t.putString("gender", gender);
        if (name != null) t.putString("name", name);
        CompoundTag g = new CompoundTag();
        genes.forEach(g::putFloat);
        t.put("genes", g);
        return t;
    }

    public static CompanionBodyData fromTag(CompoundTag t) {
        CompanionBodyData d = new CompanionBodyData();
        if (t.contains("gender")) d.gender = t.getString("gender");
        if (t.contains("name")) d.name = t.getString("name");
        CompoundTag g = t.getCompound("genes");
        for (String k : g.getAllKeys()) d.genes.put(k, g.getFloat(k));
        return d;
    }
}
