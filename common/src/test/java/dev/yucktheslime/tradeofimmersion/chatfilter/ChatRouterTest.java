package dev.yucktheslime.tradeofimmersion.chatfilter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ChatRouterTest {
    private static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-000000000003");

    private ChatFilterSettings cfg;
    private NpcTurnCooldown cooldown;
    private ChatCandidate steve;
    private ChatCandidate alex;
    private ChatCandidate bob;

    @BeforeEach
    void setUp() {
        cfg = new ChatFilterSettings();
        cooldown = new NpcTurnCooldown();
        steve = companion(STEVE, "Steve", true, 3, true, false);
        alex = companion(ALEX, "Alex", true, 5, true, false);
        bob = villager(BOB, "Bob", 4, true, false);
    }

    @Test
    void unnamedNearbyChatDoesNotStartATurn() {
        RouteDecision d = pub("I need some wood", List.of(steve), null, false);
        assertEquals(RouteDecision.Action.OVERHEAR_ONLY, d.action());
        assertEquals("not addressed", d.reason());
    }

    @Test
    void nameStartsATurn() {
        RouteDecision d = pub("Steve, follow me", List.of(steve, alex, bob), null, false);
        assertEquals(RouteDecision.Action.AI_TURN, d.action());
        assertEquals(STEVE, d.target().id());
        assertEquals("named", d.reason());
    }

    @Test
    void nameBeatsLook() {
        ChatCandidate looked = companion(ALEX, "Alex", true, 2, true, true);
        RouteDecision d = pub("Steve, over here", List.of(steve, looked), null, false);
        assertEquals(STEVE, d.target().id());
        assertEquals("named", d.reason());
    }

    @Test
    void lookWithoutNameStartsATurn() {
        ChatCandidate looked = companion(STEVE, "Steve", true, 3, true, true);
        RouteDecision d = pub("can you stay here?", List.of(looked, alex), null, false);
        assertEquals(RouteDecision.Action.AI_TURN, d.action());
        assertEquals(STEVE, d.target().id());
        assertEquals("look", d.reason());
    }

    @Test
    void lookingAtAVillagerStartsATurn() {
        ChatCandidate looked = villager(BOB, "Bob", 3, true, true);
        RouteDecision d = pub("how's the farm?", List.of(steve, looked), null, false);
        assertEquals(BOB, d.target().id());
        assertEquals(ChatCandidate.Kind.VILLAGER, d.target().kind());
        assertEquals("look", d.reason());
    }

    @Test
    void namingAVillagerStartsATurn() {
        RouteDecision d = pub("hey Bob, got any bread?", List.of(steve, bob), null, false);
        assertEquals(BOB, d.target().id());
        assertEquals("named", d.reason());
    }

    @Test
    void mcaChatAiTakesPublicVillagerLines() {
        RouteDecision d = pub("hey Bob, got any bread?", List.of(bob), null, true);
        assertEquals(RouteDecision.Action.MCA_CHAT_AI, d.action());
        assertEquals(BOB, d.target().id());
    }

    @Test
    void dialogueBoxStillUsesOurAiWhenMcaChatAiIsOn() {
        RouteDecision d = box("hey Bob", bob, true);
        assertEquals(RouteDecision.Action.AI_TURN, d.action());
        assertEquals("dialogue box", d.reason());
    }

    @Test
    void villagerPublicChatCanBeDisabled() {
        cfg.aiVillagerPublicChat = false;
        RouteDecision d = pub("hey Bob", List.of(bob), null, false);
        assertEquals(RouteDecision.Action.OVERHEAR_ONLY, d.action());
        RouteDecision boxed = box("hey Bob", bob, false);
        assertEquals(RouteDecision.Action.AI_TURN, boxed.action());
    }

    @Test
    void groupAddressHitsOwnedCompanionsOnly() {
        ChatCandidate stranger = companion(UUID.fromString("00000000-0000-0000-0000-000000000009"), "Kim", false, 2, true, false);
        RouteDecision d = pub("everyone follow me", List.of(steve, alex, stranger, bob), null, false);
        assertEquals(RouteDecision.Action.AI_TURN, d.action());
        assertEquals("group address", d.reason());
        assertEquals(2, d.targets().size());
        assertTrue(d.targets().stream().allMatch(c -> c.ownedBySpeaker() && c.kind() == ChatCandidate.Kind.COMPANION));
    }

    @Test
    void ownerProximityOnlyWhenEnabled() {
        RouteDecision off = pub("what should we do?", List.of(steve), null, false);
        assertEquals(RouteDecision.Action.OVERHEAR_ONLY, off.action());

        cfg.ownerChatWithoutAddress = true;
        RouteDecision on = pub("what should we do?", List.of(steve), null, false);
        assertEquals(RouteDecision.Action.AI_TURN, on.action());
        assertEquals("owner proximity", on.reason());
    }

    @Test
    void severalOwnedCompanionsNeedADecision() {
        cfg.ownerChatWithoutAddress = true;
        RouteDecision d = pub("what should we do?", List.of(steve, alex), null, false);
        assertEquals(RouteDecision.Action.DECIDE, d.action());
        assertEquals(2, d.targets().size());
    }

    @Test
    void stickyConversationContinuesWithoutTheName() {
        RouteDecision d = pub("ok, and then?", List.of(steve, alex), STEVE, false);
        assertEquals(RouteDecision.Action.AI_TURN, d.action());
        assertEquals(STEVE, d.target().id());
        assertEquals("continue", d.reason());
    }

    @Test
    void dialogueBoxIsAnExplicitTarget() {
        RouteDecision d = box("can you follow me?", steve, false);
        assertEquals(RouteDecision.Action.AI_TURN, d.action());
        assertEquals(STEVE, d.target().id());
        assertEquals("dialogue box", d.reason());
    }

    @Test
    void perNpcCooldownBlocksASecondTurn() {
        cooldown.mark(STEVE, 1_000);
        RouteDecision d = ChatRouter.route(
                ChatRouter.Channel.PUBLIC_CHAT, "Steve, again", List.of(steve), null, null,
                cfg, cooldown, 1_000 + 500, false);
        assertEquals(RouteDecision.Action.RATE_LIMITED, d.action());
        RouteDecision later = ChatRouter.route(
                ChatRouter.Channel.PUBLIC_CHAT, "Steve, again", List.of(steve), null, null,
                cfg, cooldown, 1_000 + 2_000, false);
        assertEquals(RouteDecision.Action.AI_TURN, later.action());
    }

    @Test
    void noiseNeverStartsATurn() {
        RouteDecision d = pub("/home", List.of(steve), null, false);
        assertEquals(RouteDecision.Action.SKIP, d.action());
    }

    @Test
    void filterOffDisablesPublicChatTurns() {
        cfg.chatFilterEnabled = false;
        RouteDecision d = pub("Steve, follow me", List.of(steve), null, false);
        assertEquals(RouteDecision.Action.DISABLED, d.action());
        RouteDecision boxed = box("Steve, follow me", steve, false);
        assertEquals(RouteDecision.Action.AI_TURN, boxed.action());
    }

    @Test
    void closerNamedNpcWins() {
        ChatCandidate farSteve = companion(UUID.fromString("00000000-0000-0000-0000-000000000011"), "Steve", false, 20, false, false);
        RouteDecision d = pub("Steve, hi", List.of(farSteve, steve), null, false);
        assertEquals(STEVE, d.target().id());
    }

    @Test
    void openConversationHelperHonoursRangeAndWindow() {
        OpenConversations open = new OpenConversations();
        open.remember(STEVE, ALEX, 10_000);
        assertEquals(ALEX, open.current(STEVE, 10_000 + 30_000, 60_000, java.util.Set.of(ALEX)));
        assertNull(open.current(STEVE, 10_000 + 61_000, 60_000, java.util.Set.of(ALEX)));
        assertNull(open.current(STEVE, 10_000 + 1_000, 60_000, java.util.Set.of(STEVE)));
        assertNull(open.current(STEVE, 10_000 + 1_000, 0, java.util.Set.of(ALEX)));
    }

    private RouteDecision pub(String text, List<ChatCandidate> near, UUID continued, boolean mca) {
        return ChatRouter.route(ChatRouter.Channel.PUBLIC_CHAT, text, near, null, continued, cfg, cooldown, 10_000, mca);
    }

    private RouteDecision box(String text, ChatCandidate target, boolean mca) {
        return ChatRouter.route(ChatRouter.Channel.DIALOGUE_BOX, text, List.of(target), target, null, cfg, cooldown, 10_000, mca);
    }

    private static ChatCandidate companion(UUID id, String name, boolean owned, double dist, boolean hear, boolean look) {
        return new ChatCandidate(id, name, List.of(), ChatCandidate.Kind.COMPANION, owned, dist, hear, look);
    }

    private static ChatCandidate villager(UUID id, String name, double dist, boolean hear, boolean look) {
        return new ChatCandidate(id, name, List.of(), ChatCandidate.Kind.VILLAGER, false, dist, hear, look);
    }
}
