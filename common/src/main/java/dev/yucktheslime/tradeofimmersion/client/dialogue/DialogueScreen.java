package dev.yucktheslime.tradeofimmersion.client.dialogue;

import dev.yucktheslime.tradeofimmersion.client.stt.SttClient;
import dev.yucktheslime.tradeofimmersion.config.ToiConfig;
import dev.yucktheslime.tradeofimmersion.dialogue.DialogueNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The dialogue box opened for typing: the conversation so far (mouse wheel scrolls back), a field to talk to the
 * NPC you looked at (Tab picks the next NPC nearby), Send, and a microphone button to hold for push-to-talk.
 * Nothing goes to the main chat.
 */
public class DialogueScreen extends Screen {
    private Entity target;
    private final boolean historyMode;
    private final String draft;
    private EditBox input;
    private int scroll;
    private int panelX, panelY, panelW, panelH;

    private DialogueScreen(Entity target, String draft, boolean historyMode) {
        super(Component.literal("Talk"));
        this.target = target;
        this.draft = draft == null ? "" : draft;
        this.historyMode = historyMode;
    }

    public static void open(Entity target, String draft, boolean historyMode) {
        Minecraft.getInstance().setScreen(new DialogueScreen(target, draft, historyMode));
    }

    public Entity target() {
        return target;
    }

    public void setDraft(String text) {
        if (input != null) {
            input.setValue(text);
            setFocused(input);
            input.setFocused(true);
        }
    }

    @Override
    protected void init() {
        panelW = Math.min(380, width - 20);
        panelX = (width - panelW) / 2;
        int rows = historyMode ? Math.max(6, (height - 90) / (font.lineHeight + 1)) : Math.max(4, Math.min(12, (height / 2 - 40) / (font.lineHeight + 1)));
        panelH = rows * (font.lineHeight + 1) + 22;
        panelY = height - 34 - panelH;
        int iw = panelW - 32 - 46 - 4;
        input = new EditBox(font, panelX, height - 28, iw, 20, Component.literal("Say"));
        input.setMaxLength(DialogueNet.MAX_TALK);
        input.setValue(draft);
        input.setHint(Component.literal(target != null ? "Say something to " + target.getName().getString() + "..." : "Look at an NPC (or press Tab)..."));
        addRenderableWidget(input);
        addRenderableWidget(Button.builder(Component.literal("Send"), b -> send()).bounds(panelX + iw + 2, height - 28, 46, 20).build());
        addRenderableWidget(new MicButton(panelX + panelW - 30, height - 28));
        setInitialFocus(input);
    }

    private void send() {
        String text = input.getValue().trim();
        if (text.isEmpty()) return;
        if (target == null) {
            DialogueClient.info("Look at an NPC (or press Tab) to talk to them.");
            return;
        }
        DialogueClient.say(target, text, false);
        input.setValue("");
        onClose();
    }

    private void nextTarget() {
        List<Entity> near = DialogueClient.nearby(16);
        if (near.isEmpty()) return;
        int i = target == null ? -1 : near.indexOf(target);
        target = near.get((i + 1) % near.size());
        input.setHint(Component.literal("Say something to " + target.getName().getString() + "..."));
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (DialogueClient.suppressTalkChar()) return true;
        return super.charTyped(c, mods);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (DialogueClient.suppressTalkChar() && DialogueClient.TALK.matches(key, scan)) return true;
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            send();
            return true;
        }
        if (key == GLFW.GLFW_KEY_TAB) {
            nextTarget();
            return true;
        }
        if (key == GLFW.GLFW_KEY_PAGE_UP) {
            scroll += 5;
            return true;
        }
        if (key == GLFW.GLFW_KEY_PAGE_DOWN) {
            scroll = Math.max(0, scroll - 5);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        scroll = Math.max(0, scroll + (dy > 0 ? 3 : -3));
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        Minecraft mc = Minecraft.getInstance();
        if (target != null && (!target.isAlive() || target.isRemoved() || mc.player == null || target.distanceTo(mc.player) > 24)) target = null;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        // keep the world visible, like MCA's interaction screen
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        ToiConfig cfg = ToiConfig.get();
        DialogueHud.frame(g, panelX - 4, panelY, panelW + 8, panelH, Math.max(0.55f, cfg.dialogueBoxOpacity), 1f);
        String head = target != null ? "Talking to " + target.getName().getString() : "No NPC selected";
        g.drawString(font, head, panelX + 2, panelY + 5, target != null ? DialogueHud.nameColor(target instanceof dev.yucktheslime.tradeofimmersion.entity.CompanionEntity ? DialogueNet.KIND_COMPANION : DialogueNet.KIND_VILLAGER) : 0xA0A0A0, true);
        String hint = "Tab: next NPC \u00b7 wheel: scroll \u00b7 hold Mic to speak";
        hint = font.plainSubstrByWidth(hint, Math.max(0, panelW - font.width(head) - 12));
        g.drawString(font, hint, panelX + panelW - font.width(hint) - 2, panelY + 5, 0x808080, false);
        // history, newest at the bottom
        List<FormattedCharSequence> rows = new ArrayList<>();
        for (DialogueClient.Entry e : DialogueClient.history()) rows.addAll(font.split(DialogueHud.line(e), panelW - 4));
        int visible = (panelH - 22) / (font.lineHeight + 1);
        scroll = Math.min(scroll, Math.max(0, rows.size() - visible));
        int end = rows.size() - scroll, start = Math.max(0, end - visible);
        int y = panelY + panelH - 4 - (end - start) * (font.lineHeight + 1);
        if (rows.isEmpty()) g.drawString(font, "Nothing said yet.", panelX + 2, panelY + 20, 0x808080, false);
        for (int i = start; i < end; i++) {
            g.drawString(font, rows.get(i), panelX + 2, y, 0xFFFFFF, true);
            y += font.lineHeight + 1;
        }
        if (scroll > 0) g.drawString(font, "\u2193 " + scroll + " more", panelX + panelW - 60, panelY + panelH - 12, 0xB08D57, true);
        String status = SttClient.statusLine();
        if (status != null) g.drawString(font, status, panelX, height - 40, SttClient.statusColor(), true);
        super.render(g, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        DialogueClient.userClosedDialogue();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Hold to record (push-to-talk inside the talk screen, where the B key types a letter instead). */
    private final class MicButton extends Button {
        MicButton(int x, int y) {
            super(x, y, 30, 20, Component.literal("Mic"), b -> {
            }, DEFAULT_NARRATION);
        }

        @Override
        public void onClick(double mx, double my) {
            if (!SttClient.recording()) SttClient.startHeld(target);
        }

        @Override
        public void onRelease(double mx, double my) {
            if (SttClient.recording()) SttClient.stop();
        }

        @Override
        public void renderWidget(GuiGraphics g, int mx, int my, float delta) {
            super.renderWidget(g, mx, my, delta);
            if (SttClient.recording()) g.fill(getX() + 2, getY() + 2, getX() + 6, getY() + 6, 0xFFFF3030);
        }
    }
}
