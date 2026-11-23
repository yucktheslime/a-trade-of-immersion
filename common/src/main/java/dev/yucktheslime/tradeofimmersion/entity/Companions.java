package dev.yucktheslime.tradeofimmersion.entity;

import dev.yucktheslime.tradeofimmersion.config.ToiConfig;
import dev.yucktheslime.tradeofimmersion.registry.ModRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Lookup and spawning helpers for companions. */
public final class Companions {
    private static final String[] MALE = {"Aldric", "Bram", "Cedric", "Doran", "Elric", "Finn", "Garrick", "Hale", "Ivo", "Jory", "Kael", "Lorne"};
    private static final String[] FEMALE = {"Aria", "Brynn", "Cora", "Dahlia", "Elara", "Freya", "Gwen", "Hazel", "Isla", "Juniper", "Kira", "Lyra"};

    private Companions() {
    }

    public static List<CompanionEntity> all(MinecraftServer server) {
        List<CompanionEntity> out = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity e : level.getAllEntities()) if (e instanceof CompanionEntity c && c.isAlive()) out.add(c);
        }
        return out;
    }

    /** By UUID, exact name, or name prefix (case-insensitive); prefers ones owned by {@code owner}. */
    @Nullable
    public static CompanionEntity find(MinecraftServer server, String query, @Nullable Player owner) {
        List<CompanionEntity> all = all(server);
        if (owner != null) all.sort((a, b) -> Boolean.compare(!a.isOwner(owner), !b.isOwner(owner)));
        for (CompanionEntity c : all) if (c.getUUID().toString().equalsIgnoreCase(query) || c.name().equalsIgnoreCase(query)) return c;
        for (CompanionEntity c : all) if (c.name().toLowerCase().startsWith(query.toLowerCase())) return c;
        return null;
    }

    public static String randomName(String gender) {
        String[] pool = "female".equalsIgnoreCase(gender) ? FEMALE : MALE;
        return pool[new Random().nextInt(pool.length)];
    }

    public static CompanionEntity spawn(ServerLevel level, Vec3 pos, @Nullable Player owner, @Nullable String name, @Nullable String gender, @Nullable String skin) {
        CompanionEntity c = ModRegistry.COMPANION.get().create(level);
        if (c == null) throw new IllegalStateException("could not create companion");
        String g = gender == null || gender.isBlank() ? ToiConfig.get().companionGender : gender.toLowerCase();
        if (!g.equals("female") && !g.equals("male") && !g.equals("neutral")) g = "male";
        c.moveTo(pos.x, pos.y, pos.z, owner != null ? owner.getYRot() + 180 : 0, 0);
        c.setGender(g);
        c.setCustomName(Component.literal(name == null || name.isBlank() ? randomName(g) : name));
        c.setSkin(skin != null ? skin : ToiConfig.get().defaultSkin);
        c.setSlim("female".equals(g));
        if (owner != null) c.setOwner(owner);
        c.finalizeSpawn(level, level.getCurrentDifficultyAt(c.blockPosition()), MobSpawnType.COMMAND, null);
        level.addFreshEntity(c);
        return c;
    }
}
