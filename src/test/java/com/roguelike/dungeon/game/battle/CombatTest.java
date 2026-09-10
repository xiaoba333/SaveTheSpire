package com.roguelike.dungeon.game.battle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.roguelike.dungeon.flow.LevelResult;
import com.roguelike.dungeon.game.card.CardInstance;
import com.roguelike.dungeon.game.card.CardLibrary;
import com.roguelike.dungeon.game.entity.Player;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class CombatTest {

    @Test
    void playingCardConsumesEnergyAndMovesCardToDiscard() {
        List<String> logs = new ArrayList<>();
        Combat combat = new Combat(logs::add);

        assertEquals(3, combat.getEnergy());
        assertEquals(5, combat.getHand().size());
        assertEquals(5, combat.getDrawPileSize());
        assertEquals(0, combat.getDiscardPileSize());
        assertEquals(1, combat.getTurnNumber());
        assertEquals("PLAYER_TURN", combat.getPhase());
        assertNull(combat.getResult());
        assertNotNull(combat.getMonsterIntentInfo());
        assertEquals("ATTACK", combat.getMonsterIntentInfo().type());
        assertTrue(!combat.drainNewLogs().isEmpty());

        assertEquals(PlayCardResult.SUCCESS, combat.playCard(0));

        assertEquals(2, combat.getEnergy());
        assertEquals(4, combat.getHand().size());
        assertEquals(1, combat.getDiscardPileSize());
        assertTrue(logs.stream().anyMatch(line -> line.contains("消耗 1 点能量")));
    }

    @Test
    void canPlayCardByInstanceId() {
        List<String> logs = new ArrayList<>();
        Combat combat = new Combat(logs::add);
        String instanceId = combat.getHand().get(0).id();

        assertEquals(PlayCardResult.SUCCESS, combat.playCard(instanceId));

        assertFalse(combat.getHand().stream().anyMatch(instance -> instance.id().equals(instanceId)));
        assertEquals(4, combat.getHand().size());
        assertEquals(1, combat.getDiscardPileSize());
    }

    @Test
    void cardCannotBePlayedWithoutEnoughEnergy() {
        List<String> logs = new ArrayList<>();
        Combat combat = new Combat(logs::add);

        combat.playCard(0);
        combat.playCard(0);
        combat.playCard(0);

        int handSize = combat.getHand().size();
        int discardSize = combat.getDiscardPileSize();
        int energy = combat.getEnergy();

        combat.playCard(0);

        assertEquals(0, energy);
        assertEquals(handSize, combat.getHand().size());
        assertEquals(discardSize, combat.getDiscardPileSize());
        assertTrue(logs.stream().anyMatch(line -> line.contains("能量不足")));
    }

    @Test
    void endingTurnDiscardsHandAndRefreshesEnergy() {
        List<String> logs = new ArrayList<>();
        Combat combat = new Combat(logs::add);

        combat.playCard(0);
        combat.playCard(0);
        combat.playCard(0);
        combat.endPlayerTurn();

        assertEquals(3, combat.getEnergy());
        assertEquals(5, combat.getHand().size());
        assertTrue(combat.isPlayerTurn());
        assertEquals(2, combat.getTurnNumber());
        assertEquals("PLAYER_TURN", combat.getPhase());
        assertTrue(!combat.drainNewLogs().isEmpty());
    }

    @Test
    void connectedCombatShouldUseSharedPlayerAndNotifyVictoryOnce() {
        Player player = new Player(50, 3);
        player.setHealth(37);
        List<CardInstance> deck = java.util.stream.IntStream.range(0, 10)
                .mapToObj(index -> new CardInstance(
                        "quick-slash-" + index, CardLibrary.QUICK_SLASH))
                .toList();
        AtomicReference<LevelResult> result = new AtomicReference<>();
        AtomicInteger notificationCount = new AtomicInteger();
        Combat combat = new Combat(
                player,
                deck,
                line -> { },
                levelResult -> {
                    result.set(levelResult);
                    notificationCount.incrementAndGet();
                });

        playWholeHand(combat);
        combat.endPlayerTurn();
        playWholeHand(combat);

        assertTrue(combat.isFinished());
        assertEquals("VICTORY", combat.getResult());
        assertEquals(LevelResult.COMPLETED, result.get());
        assertEquals(1, notificationCount.get());
        assertEquals(combat.getPlayerHp(), player.getHealth());

        combat.endPlayerTurn();
        assertEquals(1, notificationCount.get());
    }

    private static void playWholeHand(Combat combat) {
        while (!combat.getHand().isEmpty() && !combat.isFinished()) {
            assertEquals(PlayCardResult.SUCCESS, combat.playCard(0));
        }
    }
}
