package dev.yucktheslime.tradeofimmersion.client.menu;

import dev.yucktheslime.tradeofimmersion.entity.CompanionEntity;
import dev.yucktheslime.tradeofimmersion.menu.CompanionMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * Pick the companion's voice: every PocketTTS speaker (including samples uploaded on the dashboard's NPC voices
 * page), Automatic, and pitch. Saved on the Kagrenac server for this NPC; its next line uses it.
 */
public class VoicePickerScreen extends Screen {
    private static final int COLS = 3, ROWS = 7;
    private final CompanionEntity companion;
    private final Screen parent;
    private CompanionMenu.Voices data;
    private int page;

    public VoicePickerScreen(CompanionEntity companion, CompanionMenu.Voices data, Screen parent) {
        super(Component.literal(companion.getName().getString() + "'s voice"));
        this.companion = companion;
        this.data = data;
        this.parent = parent;
    }

    public int entityId() {
        return companion.getId();
    }

    public void update(CompanionMenu.Voices v) {
        data = v;
        rebuildWidgets();
    }

    @Override
    protected void init() {
        int bw = 100, bh = 20, x0 = width / 2 - (COLS * (bw + 4)) / 2, y0 = 56;
        int perPage = COLS * ROWS, pages = Math.max(1, (data.speakers().size() + perPage - 1) / perPage);
        page = Math.min(page, pages - 1);
        for (int i = 0; i < perPage; i++) {
            int idx = page * perPage + i;
            if (idx >= data.speakers().size()) break;
            String speaker = data.speakers().get(idx);
            Button b = Button.builder(Component.literal(speaker), btn -> CompanionMenuClient.send(companion.getId(), "voice_set", speaker))
                    .bounds(x0 + (i % COLS) * (bw + 4), y0 + (i / COLS) * (bh + 2), bw, bh).build();
            b.active = !(speaker.equalsIgnoreCase(data.current()) && !"auto".equals(data.source()));
            addRenderableWidget(b);
        }
        int y = y0 + ROWS * (bh + 2) + 6;
        addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            page = Math.max(0, page - 1);
            rebuildWidgets();
        }).bounds(width / 2 - 160, y, 20, bh).build()).active = page > 0;
        addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            page++;
            rebuildWidgets();
        }).bounds(width / 2 - 136, y, 20, bh).build()).active = page < pages - 1;
        addRenderableWidget(Button.builder(Component.literal("Automatic"), b -> CompanionMenuClient.send(companion.getId(), "voice_set", ""))
                .bounds(width / 2 - 110, y, 80, bh).build());
        addRenderableWidget(Button.builder(Component.literal("Pitch -"), b -> pitch(-0.05f)).bounds(width / 2 - 26, y, 50, bh).build());
        addRenderableWidget(Button.builder(Component.literal("Pitch +"), b -> pitch(0.05f)).bounds(width / 2 + 28, y, 50, bh).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose()).bounds(width / 2 + 82, y, 78, bh).build());
    }

    private void pitch(float d) {
        float p = Math.max(0.5f, Math.min(2f, Math.round((data.pitch() + d) * 100f) / 100f));
        CompanionMenuClient.send(companion.getId(), "voice_pitch", String.format(Locale.ROOT, "%.2f", p));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        g.drawCenteredString(font, title, width / 2, 14, 0xFFFFFF);
        String now = data.current().isEmpty() ? "automatic (picked on the next line)" : data.current()
                + ("auto".equals(data.source()) || data.source().isEmpty() ? " (automatic)" : "");
        g.drawCenteredString(font, Component.literal("Now: " + now + String.format(Locale.ROOT, " · pitch %.2f", data.pitch())), width / 2, 28, 0xAAAAAA);
        g.drawCenteredString(font, Component.literal("Add voices: dashboard > Configuration > NPC voices (upload WAVs or a zip)"), width / 2, 40, 0x777777);
        if (!data.message().isEmpty()) g.drawCenteredString(font, Component.literal(data.message()), width / 2, height - 22, 0xFF8080);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent instanceof CompanionMenuScreen ? parent : null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
