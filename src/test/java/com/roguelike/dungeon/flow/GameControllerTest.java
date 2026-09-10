package com.roguelike.dungeon.flow;

import com.roguelike.dungeon.game.battle.Combat;
import com.roguelike.dungeon.game.card.Card;
import com.roguelike.dungeon.game.card.CardInstance;
import com.roguelike.dungeon.game.card.CardLibrary;
import com.roguelike.dungeon.game.entity.Player;
import com.roguelike.dungeon.game.map.MapNode;
import com.roguelike.dungeon.game.run.RunState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameControllerTest {
    private static final List<Card> REWARD_POOL = List.of(
            CardLibrary.BASH,
            CardLibrary.HEAVY_STRIKE,
            CardLibrary.IRON_WAVE,
            CardLibrary.SHRUG_IT_OFF);

    @Test
    void selectingBattleShouldUseRunStateAndOpenRewardAfterRealVictory() {
        Player player = new Player(50, 3);
        List<CardInstance> deck = java.util.stream.IntStream.range(0, 10)
                .mapToObj(index -> new CardInstance(
                        "quick-slash-" + index, CardLibrary.QUICK_SLASH))
                .toList();
        RunState runState = new RunState(player, deck, 5, 12345L, 1);
        GameController controller = new GameController(runState, REWARD_POOL, line -> { });
        MapNode node = controller.getMapService().getAvailableNodes().getFirst();

        controller.selectNode(node.id());

        assertEquals(GamePhase.BATTLE, controller.getPhase());
        Combat combat = controller.getCurrentCombat().orElseThrow();
        assertEquals(player.getHealth(), combat.getPlayerHp());
        assertTrue(combat.getHand().stream().allMatch(deck::contains));

        int safety = 0;
        while (controller.getPhase() == GamePhase.BATTLE && safety++ < 20) {
            playWholeHand(combat);
            combat.endPlayerTurn();
        }

        assertEquals(GamePhase.REWARD, controller.getPhase());
        assertTrue(controller.getCurrentCombat().isEmpty());
        assertTrue(controller.getCurrentReward().isPresent());
        assertFalse(controller.getMapService().getCompletedNodeIds().contains(node.id()));

        controller.skipRewardCard();

        assertEquals(GamePhase.MAP, controller.getPhase());
        assertEquals(25, runState.getGold());
        assertTrue(controller.getCurrentReward().isEmpty());
        assertTrue(controller.getMapService().getCompletedNodeIds().contains(node.id()));
    }

    @Test
    void battleDefeatShouldEndRunWithoutCompletingNode() {
        Player player = new Player(50, 3);
        player.setHealth(1);
        RunState runState = new RunState(
                player, List.of(), 0, 12345L, 1);
        GameController controller = new GameController(runState, REWARD_POOL, line -> { });
        MapNode node = controller.getMapService().getAvailableNodes().getFirst();
        controller.selectNode(node.id());

        Combat combat = controller.getCurrentCombat().orElseThrow();
        // BATTLE 节点现在按种子随机挑怪，部分怪物首回合先叠甲，循环到战斗结束。
        int safety = 0;
        while (controller.getPhase() == GamePhase.BATTLE && safety++ < 20) {
            combat.endPlayerTurn();
        }

        assertEquals(GamePhase.DEFEAT, controller.getPhase());
        assertEquals(0, player.getHealth());
        assertFalse(controller.getMapService().getCompletedNodeIds().contains(node.id()));
        assertThrows(IllegalStateException.class,
                () -> controller.selectNode(node.id()));
    }

    @Test
    void nonBattleLevelShouldReturnToMapAfterCompletion() {
        GameController controller = new GameController(
                newRunState(1), REWARD_POOL, line -> { });

        for (int step = 0; step < 10 && controller.getPhase() == GamePhase.MAP; step++) {
            MapNode node = controller.getMapService().getAvailableNodes().getFirst();
            controller.selectNode(node.id());
            if (controller.getPhase() == GamePhase.BATTLE) {
                controller.onLevelFinished(LevelResult.COMPLETED);
                controller.skipRewardCard();
            }
        }

        assertTrue(controller.getPhase() == GamePhase.EVENT
                || controller.getPhase() == GamePhase.SHOP
                || controller.getPhase() == GamePhase.REST);
        int nodeId = controller.getCurrentNode().orElseThrow().id();

        controller.onLevelFinished(LevelResult.COMPLETED);

        assertEquals(GamePhase.MAP, controller.getPhase());
        assertTrue(controller.getMapService().getCompletedNodeIds().contains(nodeId));
    }

    @Test
    void bossShouldAdvanceToNextActAndFinalBossShouldFinishRun() {
        RunState runState = newRunState(2);
        GameController controller = new GameController(runState, List.of(), line -> { });

        int safetyCounter = 0;
        while (runState.getCurrentAct() == 1 && safetyCounter++ < 20) {
            completeOneNode(controller);
        }

        assertEquals(2, runState.getCurrentAct());
        assertEquals(GamePhase.MAP, controller.getPhase());
        assertTrue(controller.getMapService().getCompletedNodeIds().isEmpty());

        while (controller.getPhase() != GamePhase.VICTORY && safetyCounter++ < 40) {
            completeOneNode(controller);
        }

        assertEquals(GamePhase.VICTORY, controller.getPhase());
        assertFalse(runState.hasNextAct());
        assertTrue(controller.getCurrentNode().isEmpty());
    }

    private static void completeOneNode(GameController controller) {
        assertEquals(GamePhase.MAP, controller.getPhase());
        MapNode node = controller.getMapService().getAvailableNodes().getFirst();
        controller.selectNode(node.id());

        switch (controller.getPhase()) {
            case BATTLE -> {
                controller.onLevelFinished(LevelResult.COMPLETED);
                if (controller.getPhase() == GamePhase.REWARD) {
                    controller.skipRewardCard();
                }
            }
            case EVENT, SHOP, REST -> controller.onLevelFinished(LevelResult.COMPLETED);
            default -> {
                // Boss 可能直接把流程切到新章节或最终胜利。
            }
        }
    }

    private static RunState newRunState(int totalActs) {
        return new RunState(
                new Player(50, 3),
                List.of(new CardInstance("strike-1", CardLibrary.STRIKE)),
                0,
                12345L,
                totalActs);
    }

    private static void playWholeHand(Combat combat) {
        while (!combat.getHand().isEmpty() && !combat.isFinished()) {
            assertEquals(Combat.PlayCardResult.SUCCESS, combat.playCard(0));
        }
    }
}
