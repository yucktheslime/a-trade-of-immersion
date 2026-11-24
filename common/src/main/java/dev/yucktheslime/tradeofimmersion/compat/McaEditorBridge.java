package dev.yucktheslime.tradeofimmersion.compat;

import dev.yucktheslime.tradeofimmersion.TradeOfImmersion;
import dev.yucktheslime.tradeofimmersion.body.CompanionBodyData;
import dev.yucktheslime.tradeofimmersion.body.CompanionBodyStore;
import dev.yucktheslime.tradeofimmersion.body.CompanionBodySync;
import dev.yucktheslime.tradeofimmersion.body.CompanionMcaData;
import dev.yucktheslime.tradeofimmersion.config.ToiConfig;
import dev.yucktheslime.tradeofimmersion.entity.CompanionEntity;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.c2s.VillagerEditorSyncRequest;
import net.conczin.mca.network.s2c.GetVillagerResponse;
import net.conczin.mca.network.s2c.OpenGuiRequest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Objects;

/**
 * MCA Reborn's villager editor, needle and thread and comb on companions. Companions are not MCA villagers, so
 * MCA's screens talk to the server about them through two small mixins ({@code mixin.GetVillagerRequestMixin} and
 * {@code mixin.VillagerEditorSyncRequestMixin}) that call this class: the editor gets an MCA villager record built
 * from the companion ({@link CompanionMcaData}), and what the editor saves becomes the companion's name, gender,
 * genes and MCA look ({@link CompanionBodyData#mca}), synced to every client and kept in the world's
 * companion_bodies.json.
 */
public final class McaEditorBridge {
    public static final String EDITOR = "mca:villager_editor";
    public static final String NEEDLE = "mca:needle_and_thread";
    public static final String COMB = "mca:comb";
    /** Editor keys that change how the villager looks beyond genes (switch the companion to the MCA look). */
    private static final List<String> VISUAL_KEYS = List.of("Clothes", "Skin", "Hair", "HairStyle", "HairBase", "HairBangs",
            "HairBack", "HairFront", "HairExtra", "SkinColor", "HairColor", "EyeColor", "EyeColorLeft", "HairDye", "SkinDye", "EyeDye", "EyeLeftDye");

    private McaEditorBridge() {
    }

    public static String itemId(ItemStack s) {
        return s.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
    }

    /** Owner or operator; with the MCA editor item in hand anyone (MCA allows the same on its villagers). */
    public static boolean mayEdit(Player p, CompanionEntity c) {
        return c.isOwner(p) || p.hasPermissions(2)
                || EDITOR.equals(itemId(p.getItemInHand(InteractionHand.MAIN_HAND))) && ToiConfig.get().mcaEditorItemForEveryone;
    }

    /** Opens MCA's editor (or needle/comb screen) for the companion. {@code which}: editor, needle or comb. */
    public static boolean open(ServerPlayer p, CompanionEntity c, String which) {
        if (!mayEdit(p, c)) {
            p.displayClientMessage(Component.literal("Only " + c.getOwnerName() + " (or an operator) can change " + c.name() + "."), true);
            return false;
        }
        OpenGuiRequest.Type type = switch (which) {
            case "needle" -> OpenGuiRequest.Type.NEEDLE_AND_THREAD;
            case "comb" -> OpenGuiRequest.Type.COMB;
            default -> OpenGuiRequest.Type.VILLAGER_EDITOR;
        };
        Network.sendToPlayer(new OpenGuiRequest(type, c), p);
        return true;
    }

    private static VillagerEntityMCA template(CompanionEntity c) {
        CompanionBodyData edits = CompanionBodyStore.get().find(c.getUUID());
        String g = edits != null && edits.gender != null ? edits.gender : c.getGender();
        if (g == null || g.isBlank()) g = ToiConfig.get().companionGender;
        VillagerEntityMCA v = CompanionMcaData.create(c.level(), c.getUUID(), CompanionMcaData.parseGender(g, Gender.MALE), edits,
                ToiConfig.get().companionBreastSize, null, null);
        if (v != null) v.setCustomName(Component.literal(c.name()));
        return v;
    }

    private static CompoundTag save(VillagerEntityMCA v) {
        CompoundTag t = new CompoundTag();
        v.saveWithoutId(t);
        return t;
    }

    /** MCA's GetVillagerRequest for a companion: its villager record, or null to let MCA answer. */
    public static CompoundTag editorData(CompanionEntity c) {
        try {
            VillagerEntityMCA v = template(c);
            return v == null ? null : save(v);
        } catch (Throwable t) {
            TradeOfImmersion.LOGGER.warn("[tradeofimmersion] Could not build MCA editor data for companion {}", c.getUUID(), t);
            return null;
        }
    }

    /** MCA's VillagerEditorSyncRequest for a companion: apply the editor patch. */
    public static void saveFromEditor(ServerPlayer player, CompanionEntity c, CompoundTag patch) {
        if (!mayEdit(player, c)) {
            player.displayClientMessage(Component.literal("Only " + c.getOwnerName() + " (or an operator) can change " + c.name() + "."), true);
            return;
        }
        try {
            VillagerEntityMCA v = template(c);
            if (v == null) return;
            CompoundTag before = save(v);
            CompoundTag merged = VillagerEditorSyncRequest.mergeAllowedEditorPatch(before, patch);
            v.syncFromEditor(merged);
            v.setAge(0);
            v.setAgeState(net.conczin.mca.entity.ai.relationship.AgeState.ADULT);
            CompoundTag after = save(v);
            CompoundTag stored = VillagerEditorSyncRequest.createEditorPatch(after);
            for (String k : List.of("CustomName", "CustomNameVisible", "Age", "AgeState", "FamilyTreeNewFatherName",
                    "FamilyTreeNewMotherName", "FamilyTreeNewSpouseName", "InfectionProgress")) stored.remove(k);

            CompanionBodyData body = CompanionBodyStore.get().getOrCreate(c.getUUID());
            boolean visual = false;
            for (String k : VISUAL_KEYS) {
                if (patch.contains(k) && !Objects.equals(before.get(k), after.get(k))) visual = true;
            }
            body.mca = stored;
            if (visual) body.look = "mca";
            if (body.look == null) body.look = dev.yucktheslime.tradeofimmersion.body.CompanionLooks.defaultLook();
            String gender = CompanionMcaData.genderName(v.getGenetics().getGender());
            body.gender = gender;
            body.lookGender = gender; // the editor chose clothes and hair for this gender: don't re-pick them
            c.setGender(gender);
            CompanionMcaData.GENE_TYPES.forEach((name, type) -> body.genes.put(name, v.getGenetics().getGene(type)));
            if (patch.contains("CustomName") && v.getCustomName() != null && !v.getCustomName().getString().isBlank()) {
                c.setCustomName(Component.literal(v.getCustomName().getString()));
            }
            body.name = c.name();
            CompanionBodyStore.get().save();
            CompanionBodySync.broadcast(player.server);
            CompoundTag reply = save(v);
            Network.sendToPlayer(new GetVillagerResponse(reply), player);
        } catch (Throwable t) {
            TradeOfImmersion.LOGGER.warn("[tradeofimmersion] Could not apply the MCA editor to companion {}", c.getUUID(), t);
            player.displayClientMessage(Component.literal("The MCA editor change could not be applied to " + c.name() + " (see the log)."), false);
        }
    }

    /** {@code /npc skin}: the player skin (opt-out), or {@code mca} for the MCA villager look (the default). */
    public static void setLook(net.minecraft.server.MinecraftServer server, CompanionEntity c, boolean mca) {
        CompanionBodyData body = CompanionBodyStore.get().getOrCreate(c.getUUID());
        body.look = mca ? "mca" : "player";
        CompanionBodyStore.get().save();
        CompanionBodySync.broadcast(server);
    }
}
