package dev.yucktheslime.tradeofimmersion.client.menu;

import dev.yucktheslime.tradeofimmersion.entity.CompanionEntity;
import dev.yucktheslime.tradeofimmersion.menu.CompanionMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * The companion's right-click menu, laid out like MCA's interaction screen: the companion on the left, actions on
 * the right, and a line to talk to it (its AI answers in chat and, with voice on, out loud).
 */
public class CompanionMenuScreen extends Screen {
    private final CompanionEntity companion;
    private final CompanionMenu.Open info;
    private EditBox talk;

    public CompanionMenuScreen(CompanionEntity companion, CompanionMenu.Open info) {
        super(Component.literal(companion.getName().getString()));
        this.companion = companion;
        this.info = info;
    }

    private void act(String action, String arg, boolean close) {
        CompanionMenuClient.send(companion.getId(), action, arg);
        if (close) onClose();
    }

    @Override
    protected void init() {
        int x = width / 2 + 10, y = height / 2 - 100, w = 140, h = 20, gap = 22;
        if (info.owner()) {
            int third = (w - 4) / 3;
            addRenderableWidget(Button.builder(Component.literal("Follow"), b -> act("follow", "", true)).bounds(x, y, third, h).build()).active = !"follow".equals(info.mode());
            addRenderableWidget(Button.builder(Component.literal("Stay"), b -> act("stay", "", true)).bounds(x + third + 2, y, third, h).build()).active = !"stay".equals(info.mode());
            addRenderableWidget(Button.builder(Component.literal("Wander"), b -> act("wander", "", true)).bounds(x + 2 * (third + 2), y, third, h).build()).active = !"wander".equals(info.mode());
            y += gap;
            addRenderableWidget(Button.builder(Component.literal("Inventory"), b -> act("inventory", "", false)).bounds(x, y, w, h).build());
            y += gap;
            addRenderableWidget(Button.builder(Component.literal("Give held item"), b -> act("gift", "", false)).bounds(x, y, w, h).build());
            y += gap;
        }
        if (info.canEdit()) {
            addRenderableWidget(Button.builder(Component.literal("Edit (MCA editor)"), b -> act("edit", "", false)).bounds(x, y, w, h).build());
            y += gap;
            int half = (w - 2) / 2;
            addRenderableWidget(Button.builder(Component.literal("Clothes"), b -> act("needle", "", false)).bounds(x, y, half, h).build());
            addRenderableWidget(Button.builder(Component.literal("Hair"), b -> act("comb", "", false)).bounds(x + half + 2, y, half, h).build());
            y += gap;
        }
        addRenderableWidget(Button.builder(Component.literal("Voice"), b -> act("voice", "", false)).bounds(x, y, w, h).build());
        y += gap;
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose()).bounds(x, y, w, h).build());

        int tw = Math.min(320, width - 40);
        talk = new EditBox(font, width / 2 - tw / 2, height - 44, tw - 54, 20, Component.literal("Say"));
        talk.setMaxLength(256);
        talk.setHint(Component.literal("Say something to " + companion.getName().getString() + "..."));
        addRenderableWidget(talk);
        addRenderableWidget(Button.builder(Component.literal("Say"), b -> say()).bounds(width / 2 + tw / 2 - 50, height - 44, 50, 20).build());
        setInitialFocus(talk);
    }

    private void say() {
        String text = talk.getValue().trim();
        if (!text.isEmpty()) {
            CompanionMenuClient.send(companion.getId(), "talk", text);
            talk.setValue("");
        }
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && talk != null && talk.isFocused()) {
            say();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        int cx = width / 2 - 90;
        g.drawCenteredString(font, title, cx, height / 2 - 110, 0xFFFFFF);
        g.drawCenteredString(font, Component.literal(companion.getMode() + " · " + Math.round(companion.getHealth()) + " hp"), cx, height / 2 - 98, 0xAAAAAA);
        if (companion.isAlive()) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, cx - 50, height / 2 - 85, cx + 50, height / 2 + 60, 55, 0.0625f, mouseX, mouseY, companion);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!companion.isAlive() || minecraft != null && minecraft.player != null && companion.distanceToSqr(minecraft.player) > 12 * 12) onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
