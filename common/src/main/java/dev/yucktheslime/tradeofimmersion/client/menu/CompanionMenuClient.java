package dev.yucktheslime.tradeofimmersion.client.menu;

import dev.architectury.networking.NetworkManager;
import dev.yucktheslime.tradeofimmersion.entity.CompanionEntity;
import dev.yucktheslime.tradeofimmersion.menu.CompanionMenu;
import net.minecraft.client.Minecraft;

/** Client side of the companion menu: opens the screens the server asks for and sends button presses. */
public final class CompanionMenuClient {
    private CompanionMenuClient() {
    }

    public static void open(CompanionMenu.Open p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getEntity(p.entityId()) instanceof CompanionEntity c) mc.setScreen(new CompanionMenuScreen(c, p));
    }

    public static void voices(CompanionMenu.Voices v) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof VoicePickerScreen s && s.entityId() == v.entityId()) {
            s.update(v);
        } else if (mc.level != null && mc.level.getEntity(v.entityId()) instanceof CompanionEntity c) {
            mc.setScreen(new VoicePickerScreen(c, v, mc.screen));
        }
    }

    public static void send(int entityId, String action, String arg) {
        NetworkManager.sendToServer(new CompanionMenu.Action(entityId, action, arg == null ? "" : arg));
    }
}
