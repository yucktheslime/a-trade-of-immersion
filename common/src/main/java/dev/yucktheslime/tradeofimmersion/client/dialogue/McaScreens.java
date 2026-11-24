package dev.yucktheslime.tradeofimmersion.client.dialogue;

import net.conczin.mca.client.gui.InteractScreen;
import net.conczin.mca.entity.VillagerLike;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.Entity;

import java.lang.reflect.Field;

/** MCA's interaction screen keeps its villager in a private field; read it once by reflection. */
final class McaScreens {
    private static Field villagerField;
    private static boolean failed;

    private McaScreens() {
    }

    /** The villager of an open MCA interaction screen, or null for any other screen. */
    static Entity villager(Screen screen) {
        if (!(screen instanceof InteractScreen) || failed) return null;
        try {
            if (villagerField == null) {
                villagerField = InteractScreen.class.getDeclaredField("villager");
                villagerField.setAccessible(true);
            }
            Object v = villagerField.get(screen);
            return v instanceof VillagerLike<?> vl ? vl.asEntity() : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            failed = true;
            dev.yucktheslime.tradeofimmersion.TradeOfImmersion.LOGGER.warn("MCA interaction screen: no Talk button ({})", e.toString());
            return null;
        }
    }

    /** Closes MCA's screen the way MCA does (it tells the server the interaction ended). */
    static void close(Screen screen) {
        screen.onClose();
    }
}
