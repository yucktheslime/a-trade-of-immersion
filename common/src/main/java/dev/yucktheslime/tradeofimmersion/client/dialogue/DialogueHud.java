package dev.yucktheslime.tradeofimmersion.client.dialogue;

import dev.yucktheslime.tradeofimmersion.client.stt.SttClient;
import dev.yucktheslime.tradeofimmersion.config.ToiConfig;
import dev.yucktheslime.tradeofimmersion.dialogue.DialogueNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the dialogue box: a dark MCA-style panel with a thin gold frame, the speaker of the newest line on a tab,
 * and the recent lines (name in colour, text in white) that fade out after {@code dialogueBoxLineSeconds}. The
 * push-to-talk state ("Listening...", "Transcribing...") shows on its last row.
 */
public final class DialogueHud {
    static final int FRAME = 0xFFB08D57, FRAME_DARK = 0xFF5A4526;

    private DialogueHud() {
    }

    public static int nameColor(int kind) {
        return switch (kind) {
            case DialogueNet.KIND_COMPANION -> 0x7FD4FF;
            case DialogueNet.KIND_VILLAGER -> 0xFFD27F;
            case DialogueNet.KIND_PLAYER -> 0xB0B0B0;
            default -> 0x9A9A9A;
        };
    }

    /** One entry as a component: "Name: text" (players: "Name → Target: text", notes in grey italics). */
    static Component line(DialogueClient.Entry e) {
        if (e.kind() == DialogueNet.KIND_INFO) return Component.literal(e.text()).withStyle(s -> s.withColor(0xA0A0A0).withItalic(true));
        String head = e.speaker() + (e.kind() == DialogueNet.KIND_PLAYER && !e.target().isEmpty() ? " \u2192 " + e.target() : "") + ": ";
        return Component.literal(head).withStyle(s -> s.withColor(nameColor(e.kind())))
                .append(Component.literal(e.text()).withStyle(s -> s.withColor(e.kind() == DialogueNet.KIND_PLAYER ? 0xD8D8D8 : 0xFFFFFF)));
    }

    public static void render(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        ToiConfig cfg = ToiConfig.get();
        if (!cfg.dialogueBox || mc.options.hideGui || mc.player == null || mc.screen instanceof DialogueScreen) return;
        long now = System.currentTimeMillis();
        long show = Math.max(1, cfg.dialogueBoxLineSeconds) * 1000L, fade = Math.max(0, cfg.dialogueBoxFadeSeconds) * 1000L;
        List<DialogueClient.Entry> recent = new ArrayList<>();
        for (DialogueClient.Entry e : DialogueClient.history()) if (now - e.time() < show + fade) recent.add(e);
        String status = SttClient.statusLine();
        if (recent.isEmpty() && status == null) return;

        float scale = Math.max(0.5f, Math.min(2f, cfg.dialogueBoxScale));
        int sw = Math.round(g.guiWidth() / scale), sh = Math.round(g.guiHeight() / scale);
        int w = Math.max(120, Math.min(sw - 8, cfg.dialogueBoxWidth));
        Font font = mc.font;
        int maxLines = Math.max(1, Math.min(30, cfg.dialogueBoxMaxLines));
        // whole entries, newest last, as many as fit in maxLines rows (a very long newest entry shows its end)
        int extra = status != null ? 1 : 0;
        int room = Math.max(1, maxLines - extra);
        List<List<FormattedCharSequence>> picked = new ArrayList<>();
        List<Float> pickedAlpha = new ArrayList<>();
        int used = 0;
        for (int i = recent.size() - 1; i >= 0 && used < room; i--) {
            DialogueClient.Entry e = recent.get(i);
            long age = now - e.time();
            float a = age < show ? 1f : fade == 0 ? 0f : Math.max(0f, 1f - (age - show) / (float) fade);
            List<FormattedCharSequence> wrapped = font.split(line(e), w - 10);
            if (used + wrapped.size() > room) {
                if (!picked.isEmpty()) break;
                wrapped = wrapped.subList(wrapped.size() - room, wrapped.size());
            }
            picked.add(0, wrapped);
            pickedAlpha.add(0, a);
            used += wrapped.size();
        }
        List<FormattedCharSequence> rows = new ArrayList<>();
        List<Float> alphas = new ArrayList<>();
        for (int i = 0; i < picked.size(); i++) {
            for (FormattedCharSequence r : picked.get(i)) {
                rows.add(r);
                alphas.add(pickedAlpha.get(i));
            }
        }
        int from = 0;
        int count = rows.size() + extra;
        if (count <= 0) return;
        float boxAlpha = 0f;
        for (int i = from; i < rows.size(); i++) boxAlpha = Math.max(boxAlpha, alphas.get(i));
        if (status != null) boxAlpha = 1f;
        if (boxAlpha <= 0.02f) return;

        int h = count * (font.lineHeight + 1) + 8;
        int[] xy = position(cfg.dialogueBoxAnchor, sw, sh, w, h, cfg.dialogueBoxOffsetX, cfg.dialogueBoxOffsetY);
        int x = xy[0], y = xy[1];
        g.pose().pushPose();
        g.pose().scale(scale, scale, 1f);
        frame(g, x, y, w, h, cfg.dialogueBoxOpacity * boxAlpha, boxAlpha);
        // tab with the newest speaker's name
        DialogueClient.Entry newest = null;
        for (int i = recent.size() - 1; i >= 0; i--) if (recent.get(i).kind() != DialogueNet.KIND_INFO && recent.get(i).kind() != DialogueNet.KIND_PLAYER) {
            newest = recent.get(i);
            break;
        }
        if (newest != null && !newest.speaker().isEmpty()) {
            int tw = font.width(newest.speaker()) + 10;
            int a = alpha(boxAlpha);
            frame(g, x + 4, y - font.lineHeight - 4, tw, font.lineHeight + 4, cfg.dialogueBoxOpacity * boxAlpha, boxAlpha);
            if (a > 8) g.drawString(font, newest.speaker(), x + 9, y - font.lineHeight - 1, (a << 24) | nameColor(newest.kind()), false);
        }
        int ty = y + 5;
        for (int i = from; i < rows.size(); i++) {
            int a = alpha(alphas.get(i));
            if (a > 8) g.drawString(font, rows.get(i), x + 5, ty, (a << 24) | 0xFFFFFF, true);
            ty += font.lineHeight + 1;
        }
        if (status != null) g.drawString(font, status, x + 5, ty, SttClient.statusColor(), true);
        g.pose().popPose();
    }

    private static int alpha(float a) {
        return Math.max(0, Math.min(255, Math.round(a * 255)));
    }

    /** Dark panel with a two-tone gold frame, like MCA's interaction screen panels. */
    static void frame(GuiGraphics g, int x, int y, int w, int h, float bgOpacity, float frameAlpha) {
        int bg = alpha(Math.max(0f, Math.min(1f, bgOpacity))) << 24 | 0x15110C;
        int fa = alpha(frameAlpha);
        g.fill(x, y, x + w, y + h, bg);
        int f = (fa << 24) | (FRAME & 0xFFFFFF), d = (fa << 24) | (FRAME_DARK & 0xFFFFFF);
        g.fill(x, y, x + w, y + 1, f);
        g.fill(x, y + h - 1, x + w, y + h, d);
        g.fill(x, y, x + 1, y + h, f);
        g.fill(x + w - 1, y, x + w, y + h, d);
    }

    /** Top-left corner of the box for the anchor (offsets point towards the middle of the screen). */
    public static int[] position(String anchor, int sw, int sh, int w, int h, int ox, int oy) {
        String a = anchor == null ? "bottom_left" : anchor.toLowerCase(java.util.Locale.ROOT);
        int x = a.endsWith("right") ? sw - w - ox : a.endsWith("center") ? (sw - w) / 2 + ox : ox;
        int y = a.startsWith("top") ? oy + 12 : sh - h - oy;
        x = Math.max(0, Math.min(sw - w, x));
        y = Math.max(12, Math.min(sh - h, y));
        return new int[]{x, y};
    }
}
