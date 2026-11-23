package dev.yucktheslime.tradeofimmersion.body;

import net.minecraft.nbt.CompoundTag;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Client-side copy of the server's companion body overrides (plain data; safe to load on any side). */
public final class ClientCompanionBodies {
    private static volatile Map<UUID, CompanionBodyData> data = Collections.emptyMap();
    private static volatile int revision;

    private ClientCompanionBodies() {
    }

    public static void accept(CompoundTag snapshot) {
        Map<UUID, CompanionBodyData> m = new HashMap<>();
        for (String k : snapshot.getAllKeys()) {
            try {
                m.put(UUID.fromString(k), CompanionBodyData.fromTag(snapshot.getCompound(k)));
            } catch (IllegalArgumentException ignored) {
                // bad key
            }
        }
        data = m;
        revision++;
    }

    public static CompanionBodyData get(UUID id) {
        return data.get(id);
    }

    /** Bumped on every sync so renderers can rebuild their cached MCA data. */
    public static int revision() {
        return revision;
    }
}
