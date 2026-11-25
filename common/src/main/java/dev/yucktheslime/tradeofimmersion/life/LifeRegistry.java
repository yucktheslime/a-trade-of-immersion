package dev.yucktheslime.tradeofimmersion.life;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import dev.yucktheslime.tradeofimmersion.TradeOfImmersion;
import dev.yucktheslime.tradeofimmersion.life.pregnancy.PregnantEffect;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;

/** Phase 2 registry entries (own DeferredRegister so it doesn't touch the shared ModRegistry). */
public final class LifeRegistry {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(TradeOfImmersion.MOD_ID, Registries.MOB_EFFECT);
    /** A RegistrySupplier is a Holder, which is what MobEffectInstance takes in 1.21. */
    public static final RegistrySupplier<MobEffect> PREGNANT = EFFECTS.register("pregnant", PregnantEffect::new);

    private LifeRegistry() {
    }

    public static void init() {
        EFFECTS.register();
    }
}
