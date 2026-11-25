package dev.yucktheslime.tradeofimmersion.skills.client;

import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.networking.NetworkManager;
import dev.yucktheslime.tradeofimmersion.skills.SkillsConfig;
import dev.yucktheslime.tradeofimmersion.skills.item.SkillNet;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Client half of item learning: answers the server's tooltip questions with the full client tooltip, and reports
 * presses of other mods' keys while holding an item so the server can learn which packet the key sends. Only the key
 * mapping's name and the held item id are sent; nothing is sent for vanilla keys.
 */
public final class SkillsClient {
    private static final Set<String> VANILLA_CATEGORIES = Set.of("key.categories.movement", "key.categories.gameplay", "key.categories.inventory",
            "key.categories.creative", "key.categories.multiplayer", "key.categories.ui", "key.categories.misc", "key.categories.tradeofimmersion");
    private static final Map<String, Boolean> DOWN = new HashMap<>();
    private static final Map<String, Long> LAST = new HashMap<>();

    private SkillsClient() {
    }

    public static void init() {
        ClientTickEvent.CLIENT_POST.register(SkillsClient::tick);
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null || !SkillsConfig.get().keybindObservation) return;
        boolean canSend;
        try {
            canSend = NetworkManager.canServerReceive(SkillNet.KeyObserved.TYPE);
        } catch (Throwable t) {
            canSend = false;
        }
        for (KeyMapping k : mc.options.keyMappings) {
            String name = k.getName();
            boolean down = k.isDown();
            boolean was = DOWN.getOrDefault(name, false);
            DOWN.put(name, down);
            if (!down || was || mc.screen != null || !canSend) continue;
            if (VANILLA_CATEGORIES.contains(k.getCategory()) || name.startsWith("key.tradeofimmersion")) continue;
            ItemStack held = mc.player.getMainHandItem();
            if (held.isEmpty()) continue;
            long now = System.currentTimeMillis();
            if (now - LAST.getOrDefault(name, 0L) < 300) continue;
            LAST.put(name, now);
            String label = I18n.get(name);
            String bound = k.getTranslatedKeyMessage().getString();
            NetworkManager.sendToServer(new SkillNet.KeyObserved(cut(name, 128), cut(label, 128), cut(bound, 64),
                    BuiltInRegistries.ITEM.getKey(held.getItem()).toString()));
        }
    }

    public static void onTooltipRequest(SkillNet.TooltipRequest req) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || req.stack().isEmpty()) return;
        List<String> out = new ArrayList<>();
        try {
            List<Component> lines = req.stack().getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.Default.NORMAL);
            for (int i = 1; i < lines.size() && out.size() < SkillNet.MAX_LINES; i++) {
                String s = lines.get(i).getString();
                if (!s.isBlank()) out.add(cut(s, SkillNet.MAX_LINE));
            }
        } catch (Throwable ignored) {
            // a mod's tooltip code failed; send what we have
        }
        NetworkManager.sendToServer(new SkillNet.TooltipReport(req.id(), BuiltInRegistries.ITEM.getKey(req.stack().getItem()).toString(), out));
    }

    private static String cut(String s, int n) {
        if (s == null) return "";
        return s.length() > n ? s.substring(0, n) : s;
    }
}
