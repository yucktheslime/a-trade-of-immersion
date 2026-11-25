package dev.yucktheslime.tradeofimmersion.skills.item;

import dev.yucktheslime.tradeofimmersion.TradeOfImmersion;
import dev.yucktheslime.tradeofimmersion.entity.CompanionEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Hand-written key actions for specific mods: "pressing key K with item I" done directly on the server for the
 * companion's fake player. Asked before learned packets. None are built in yet; addons call {@link #register}.
 */
public final class KeybindAdapters {
    public interface Adapter {
        String id();

        /** @param key the key label from the tooltip or the observed key mapping name */
        boolean handles(String itemId, String key);

        /** @return a short result text, or null when it did nothing */
        String press(CompanionEntity c, ServerPlayer fakePlayer, ItemStack stack, String key) throws Exception;
    }

    private static final List<Adapter> ADAPTERS = new CopyOnWriteArrayList<>();

    private KeybindAdapters() {
    }

    public static void register(Adapter a) {
        ADAPTERS.add(a);
        TradeOfImmersion.LOGGER.info("[tradeofimmersion] key adapter {} registered", a.id());
    }

    public static Adapter find(String itemId, String key) {
        for (Adapter a : ADAPTERS) {
            try {
                if (a.handles(itemId, key)) return a;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    public static List<Adapter> all() {
        return List.copyOf(ADAPTERS);
    }
}
