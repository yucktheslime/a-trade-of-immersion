package dev.yucktheslime.tradeofimmersion.life.pregnancy;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/** The persistent "Pregnant" status (icon only; the side effects are separate vanilla effects, by phase). */
public class PregnantEffect extends MobEffect {
    public PregnantEffect() {
        super(MobEffectCategory.NEUTRAL, 0xF29BC1);
    }
}
