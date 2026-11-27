package dev.yucktheslime.tradeofimmersion.client.dialogue;

import com.mojang.blaze3d.platform.InputConstants;
import dev.architectury.event.EventResult;
import dev.architectury.event.events.client.ClientGuiEvent;
import dev.architectury.event.events.client.ClientPlayerEvent;
import dev.architectury.event.events.client.ClientScreenInputEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.networking.NetworkManager;
import dev.architectury.registry.client.keymappings.KeyMappingRegistry;
import dev.yucktheslime.tradeofimmersion.TradeOfImmersion;
import dev.yucktheslime.tradeofimmersion.client.stt.SttClient;
import dev.yucktheslime.tradeofimmersion.config.ToiConfig;
import dev.yucktheslime.tradeofimmersion.dialogue.DialogueNet;
import dev.yucktheslime.tradeofimmersion.entity.CompanionEntity;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

/**
 * Client side of the NPC dialogue box: keeps the recent NPC lines, draws the box ({@link DialogueHud}), and owns
 * the keys: talk (V, or Enter while looking at an NPC), push-to-talk (hold B) and the scroll-back (H).
 */
public final class DialogueClient {
    public static final KeyMapping TALK = new KeyMapping("key.tradeofimmersion.talk", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.tradeofimmersion");
    public static final KeyMapping PUSH_TO_TALK = new KeyMapping("key.tradeofimmersion.push_to_talk", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, "key.categories.tradeofimmersion");
    public static final KeyMapping HISTORY = new KeyMapping("key.tradeofimmersion.dialogue_history", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, "key.categories.tradeofimmersion");

    /** One line in the box. {@code time} is System.currentTimeMillis() when it arrived. */
    public record Entry(int entityId, String speaker, String text, int kind, String target, long time) {
    }

    private static final Deque<Entry> HISTORY_LINES = new ArrayDeque<>();
    private static int lastSpeakerId = -1;
    private static long lastSpeakerTime;
    private static boolean enterWasDown;
    private static Button mcaTalkButton;
    private static final DialogueOpenGuard.State OPEN_GUARD = new DialogueOpenGuard.State();

    private DialogueClient() {
    }

    public static void init() {
        KeyMappingRegistry.register(TALK);
        KeyMappingRegistry.register(PUSH_TO_TALK);
        KeyMappingRegistry.register(HISTORY);
        ClientTickEvent.CLIENT_POST.register(DialogueClient::tick);
        ClientGuiEvent.RENDER_HUD.register((g, delta) -> DialogueHud.render(g));
        ClientPlayerEvent.CLIENT_PLAYER_JOIN.register(p -> sendHello());
        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(p -> {
            synchronized (HISTORY_LINES) {
                HISTORY_LINES.clear();
            }
            lastSpeakerId = -1;
            SttClient.cancel();
        });
        // MCA's interaction screen: a "Talk" button (and the talk key) open the dialogue box's talk field.
        ClientGuiEvent.RENDER_POST.register((screen, g, mx, my, delta) -> {
            Entity v = McaScreens.villager(screen);
            if (v == null) return;
            mcaTalkButton = Button.builder(Component.literal("Talk (AI)"), b -> {
            }).bounds(8, screen.height - 28, 80, 20).build();
            mcaTalkButton.render(g, mx, my, delta.getGameTimeDeltaPartialTick(true));
        });
        ClientScreenInputEvent.MOUSE_CLICKED_PRE.register((mc, screen, mx, my, button) -> {
            Entity v = McaScreens.villager(screen);
            if (v != null && button == 0 && mcaTalkButton != null && mcaTalkButton.isMouseOver(mx, my)) {
                mcaTalkButton.playDownSound(mc.getSoundManager());
                openFromMca(screen, v, false);
                return EventResult.interruptTrue();
            }
            return EventResult.pass();
        });
        ClientScreenInputEvent.KEY_PRESSED_PRE.register((mc, screen, key, scan, mods) -> {
            Entity v = McaScreens.villager(screen);
            if (v != null && TALK.matches(key, scan)) {
                openFromMca(screen, v, true);
                return EventResult.interruptTrue();
            }
            return EventResult.pass();
        });
    }

    public static void sendHello() {
        if (NetworkManager.canServerReceive(DialogueNet.Hello.TYPE)) {
            NetworkManager.sendToServer(new DialogueNet.Hello(ToiConfig.get().dialogueBox, 1));
        }
    }

    public static boolean serverHasMod() {
        return NetworkManager.canServerReceive(DialogueNet.Talk.TYPE);
    }

    // ------------------------------------------------------------------ lines

    public static void onLine(DialogueNet.Line l) {
        add(new Entry(l.entityId(), l.speaker(), l.text(), l.kind(), l.target(), System.currentTimeMillis()));
        ToiConfig cfg = ToiConfig.get();
        if (!cfg.dialogueBox || cfg.mirrorToChat) {
            // the server sent it as a box line; also (or instead) show it in chat
            Minecraft.getInstance().gui.getChat().addMessage(chatLine(l.speaker(), l.text(), l.kind(), l.target()));
        }
    }

    /** From the MCA villager message mixin. Returns true when the line went to the box (so chat can be skipped). */
    public static boolean onMcaVillagerLine(Component prefix, Component message, UUID villager) {
        ToiConfig cfg = ToiConfig.get();
        if (!cfg.dialogueBox) return false;
        Minecraft mc = Minecraft.getInstance();
        Entity e = null;
        if (mc.level != null) {
            for (Entity x : mc.level.entitiesForRendering()) {
                if (x.getUUID().equals(villager)) {
                    e = x;
                    break;
                }
            }
        }
        String name = prefix.getString().replaceAll(":\\s*$", "").trim();
        String mcaPrefix = net.conczin.mca.Config.getInstance().villagerChatPrefix;
        if (mcaPrefix != null && !mcaPrefix.isEmpty() && name.startsWith(mcaPrefix)) name = name.substring(mcaPrefix.length()).trim();
        if (e != null && name.isEmpty()) name = e.getName().getString();
        add(new Entry(e == null ? -1 : e.getId(), name, message.getString(), DialogueNet.KIND_VILLAGER, "", System.currentTimeMillis()));
        return !cfg.mirrorToChat;
    }

    /** A local note (errors, "say something"), shown in the box, or above the hotbar when the box is off. */
    public static void info(String text) {
        if (ToiConfig.get().dialogueBox) add(new Entry(-1, "", text, DialogueNet.KIND_INFO, "", System.currentTimeMillis()));
        else if (Minecraft.getInstance().player != null) Minecraft.getInstance().player.displayClientMessage(Component.literal(text), true);
    }

    private static void add(Entry e) {
        synchronized (HISTORY_LINES) {
            HISTORY_LINES.addLast(e);
            int max = Math.max(20, ToiConfig.get().dialogueHistorySize);
            while (HISTORY_LINES.size() > max) HISTORY_LINES.pollFirst();
        }
        if (e.entityId() >= 0 && (e.kind() == DialogueNet.KIND_COMPANION || e.kind() == DialogueNet.KIND_VILLAGER)) {
            lastSpeakerId = e.entityId();
            lastSpeakerTime = e.time();
        }
    }

    public static List<Entry> history() {
        synchronized (HISTORY_LINES) {
            return new ArrayList<>(HISTORY_LINES);
        }
    }

    public static Component chatLine(String speaker, String text, int kind, String target) {
        if (kind == DialogueNet.KIND_INFO) return Component.literal(text).withStyle(s -> s.withColor(0xAAAAAA).withItalic(true));
        String head = kind == DialogueNet.KIND_PLAYER ? "<" + speaker + (target.isEmpty() ? "" : " to " + target) + "> " : "<" + speaker + "> ";
        return Component.literal(head).withStyle(s -> s.withColor(DialogueHud.nameColor(kind)))
                .append(Component.literal(text).withStyle(s -> s.withColor(kind == DialogueNet.KIND_PLAYER ? 0xCCCCCC : 0xFFFFFF)));
    }

    // ------------------------------------------------------------------ targets

    public static boolean talkable(Entity e) {
        return e != null && e.isAlive() && (e instanceof CompanionEntity || e instanceof VillagerEntityMCA);
    }

    /** The NPC to talk to: the one under the crosshair, else the one you look towards, else the last one that spoke. */
    public static Entity target() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return null;
        if (mc.hitResult instanceof EntityHitResult eh && talkable(eh.getEntity())) return eh.getEntity();
        Entity cone = lookedTowards(12, 12);
        if (cone != null) return cone;
        if (lastSpeakerId >= 0 && System.currentTimeMillis() - lastSpeakerTime < 180_000) {
            Entity e = mc.level.getEntity(lastSpeakerId);
            if (talkable(e) && e.distanceTo(mc.player) < 20) return e;
        }
        return null;
    }

    /** Talkable NPCs near the player, nearest first (Tab in the talk screen cycles through them). */
    public static List<Entity> nearby(double r) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return List.of();
        List<Entity> list = new ArrayList<>(mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(r), DialogueClient::talkable));
        list.sort(Comparator.comparingDouble(e -> e.distanceToSqr(mc.player)));
        return list;
    }

    private static Entity lookedTowards(double range, double degrees) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 eye = mc.player.getEyePosition(), look = mc.player.getViewVector(1f);
        double bestDot = Math.cos(Math.toRadians(degrees));
        Entity best = null;
        for (Entity e : nearby(range)) {
            Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
            double len = to.length();
            if (len < 0.3) continue;
            double dot = to.scale(1 / len).dot(look);
            if (dot > bestDot && mc.player.hasLineOfSight(e)) {
                bestDot = dot;
                best = e;
            }
        }
        return best;
    }

    /** Sends a typed or spoken line to the NPC (the server echoes it back into the box). */
    public static void say(Entity target, String text, boolean spoken) {
        String t = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        if (t.isEmpty()) return;
        if (target == null) {
            info("Look at an NPC to talk to them.");
            return;
        }
        if (!serverHasMod()) {
            info("This server does not have A Trade of Immersion.");
            return;
        }
        if (t.length() > DialogueNet.MAX_TALK) t = t.substring(0, DialogueNet.MAX_TALK);
        NetworkManager.sendToServer(new DialogueNet.Talk(target.getId(), t, spoken));
        lastSpeakerId = target.getId();
        lastSpeakerTime = System.currentTimeMillis();
    }

    // ------------------------------------------------------------------ keys

    /**
     * Close MCA's interaction screen and open the talk box a couple of ticks later. MCA's close tells the
     * server the interaction ended, and the server's {@code closeContainer()} reply would otherwise wipe the
     * new box with {@code setScreen(null)}. {@code fromTalkKey} also swallows the leaked V (or rebound key)
     * so it is not typed into the field.
     */
    private static void openFromMca(Screen mcaScreen, Entity villager, boolean fromTalkKey) {
        McaScreens.close(mcaScreen);
        DialogueOpenGuard.request(OPEN_GUARD, villager.getId(), false, fromTalkKey);
    }

    static void userClosedDialogue() {
        DialogueOpenGuard.markUserClosed(OPEN_GUARD);
    }

    static boolean suppressTalkChar() {
        return DialogueOpenGuard.suppressTalkChar(OPEN_GUARD, TALK.isDown());
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null) return;
        SttClient.tick(mc);
        Screen screen = mc.screen;
        boolean enterDown = InputConstants.isKeyDown(mc.getWindow().getWindow(), GLFW.GLFW_KEY_ENTER);
        boolean pending = OPEN_GUARD.hasPending();
        if (pending) {
            // V may still be held after we closed MCA's screen; don't also open from the world-key path.
            while (TALK.consumeClick()) {}
            while (HISTORY.consumeClick()) {}
        }
        DialogueOpenGuard.Action action = DialogueOpenGuard.tick(OPEN_GUARD, screen instanceof DialogueScreen);
        if (action == DialogueOpenGuard.Action.OPEN || action == DialogueOpenGuard.Action.REOPEN) {
            int id = action == DialogueOpenGuard.Action.OPEN ? OPEN_GUARD.pendingEntityId() : OPEN_GUARD.guardEntityId();
            boolean history = action == DialogueOpenGuard.Action.OPEN ? OPEN_GUARD.pendingHistory() : OPEN_GUARD.guardHistory();
            Entity v = mc.level != null ? mc.level.getEntity(id) : null;
            DialogueScreen.open(talkable(v) ? v : target(), "", history);
        }
        if (screen == null && !pending && action != DialogueOpenGuard.Action.OPEN && action != DialogueOpenGuard.Action.REOPEN) {
            while (TALK.consumeClick()) DialogueScreen.open(target(), "", false);
            while (HISTORY.consumeClick()) DialogueScreen.open(target(), "", true);
            if (enterDown && !enterWasDown && mc.hitResult instanceof EntityHitResult eh && talkable(eh.getEntity())) {
                DialogueScreen.open(eh.getEntity(), "", false);
            }
            if (PUSH_TO_TALK.isDown() && !SttClient.recording()) SttClient.start(target());
        }
        if (SttClient.recording() && !SttClient.heldByScreen() && !PUSH_TO_TALK.isDown()) SttClient.stop();
        enterWasDown = enterDown;
    }

    static void logMics() {
        TradeOfImmersion.LOGGER.info("Microphones: {}", dev.yucktheslime.tradeofimmersion.client.stt.MicRecorder.devices());
    }
}
