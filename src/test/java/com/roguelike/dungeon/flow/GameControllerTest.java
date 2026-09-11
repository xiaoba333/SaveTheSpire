package com.roguelike.dungeon.flow;

import com.roguelike.dungeon.game.battle.Combat;
import com.roguelike.dungeon.game.battle.PlayCardResult;
import com.roguelike.dungeon.game.card.Card;
import com.roguelike.dungeon.game.card.CardInstance;
import com.roguelike.dungeon.game.card.CardLibrary;
import com.roguelike.dungeon.game.campfire.CampfireActionStatus;
import com.roguelike.dungeon.game.entity.BattleEndHealRelic;
import com.roguelike.dungeon.game.entity.Player;
import com.roguelike.dungeon.game.event.EventActionStatus;
import com.roguelike.dungeon.game.event.EventChoice;
import com.roguelike.dungeon.game.map.MapNode;
import com.roguelike.dungeon.game.map.MapNodeType;
import com.roguelike.dungeon.game.run.RunState;
import com.roguelike.dungeon.game.shop.ShopActionResult;
import com.roguelike.dungeon.game.shop.ShopItem;
import com.roguelike.dungeon.game.shop.ShopService;
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
    void battleVictoryShouldHealFromBurningBloodRelic() {
        Player player = new Player(50, 3);
        player.setHealth(20);
        player.addRelic(new BattleEndHealRelic());
        RunState runState = new RunState(
                player,
                List.of(new CardInstance("strike-1", CardLibrary.STRIKE)),
                0,
                12345L,
                1);
        GameController controller = new GameController(runState, List.of(), line -> { });
        MapNode node = controller.getMapService().getAvailableNodes().getFirst();

        controller.selectNode(node.id());
        assertEquals(GamePhase.BATTLE, controller.getPhase());

        controller.onLevelFinished(LevelResult.COMPLETED);

        assertEquals(26, player.getHealth());
        assertEquals(GamePhase.REWARD, controller.getPhase());
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
    void eventChoiceShouldResolveEventAndCompleteMapNode() {
        GameController controller = new GameController(
                newRunState(1), REWARD_POOL, line -> { });

        int safetyCounter = 0;
        while (controller.getPhase() != GamePhase.EVENT && safetyCounter++ < 15) {
            MapNode node = controller.getMapService().getAvailableNodes().stream()
                    .filter(candidate -> candidate.type() == MapNodeType.EVENT)
                    .findFirst()
                    .orElseGet(() -> controller.getMapService()
                            .getAvailableNodes().getFirst());
            controller.selectNode(node.id());
            if (controller.getPhase() == GamePhase.BATTLE) {
                controller.onLevelFinished(LevelResult.COMPLETED);
                controller.skipRewardCard();
            } else if (controller.getPhase() == GamePhase.SHOP
                    || controller.getPhase() == GamePhase.REST) {
                controller.onLevelFinished(LevelResult.COMPLETED);
            }
        }

        assertEquals(GamePhase.EVENT, controller.getPhase());
        int eventNodeId = controller.getCurrentNode().orElseThrow().id();
        assertTrue(controller.getCurrentEvent().isPresent());
        EventChoice choice = controller.getCurrentEventChoices().stream()
                .filter(EventChoice::available)
                .findFirst()
                .orElseThrow();

        assertEquals(EventActionStatus.SUCCESS,
                controller.chooseEventChoice(choice.id()).status());

        assertEquals(GamePhase.MAP, controller.getPhase());
        assertTrue(controller.getCurrentEvent().isEmpty());
        assertTrue(controller.getCurrentEventChoices().isEmpty());
        assertTrue(controller.getMapService().getCompletedNodeIds().contains(eventNodeId));
    }

    @Test
    void campfireRestShouldHealAndCompleteMapNode() {
        RunState runState = newRunState(1);
        runState.getPlayer().setHealth(20);
        GameController controller = new GameController(
                runState, REWARD_POOL, line -> { });

        int safetyCounter = 0;
        while (controller.getPhase() != GamePhase.REST && safetyCounter++ < 15) {
            MapNode node = controller.getMapService().getAvailableNodes().stream()
                    .filter(candidate -> candidate.type() == MapNodeType.REST)
                    .findFirst()
                    .orElseGet(() -> controller.getMapService()
                            .getAvailableNodes().getFirst());
            controller.selectNode(node.id());
            if (controller.getPhase() == GamePhase.BATTLE) {
                controller.onLevelFinished(LevelResult.COMPLETED);
                controller.skipRewardCard();
            } else if (controller.getPhase() == GamePhase.EVENT
                    || controller.getPhase() == GamePhase.SHOP) {
                controller.onLevelFinished(LevelResult.COMPLETED);
            }
        }

        assertEquals(GamePhase.REST, controller.getPhase());
        int restNodeId = controller.getCurrentNode().orElseThrow().id();
        assertFalse(controller.getCurrentCampfireActions().isEmpty());
        assertFalse(controller.getCampfireUpgradeableCards().isEmpty());

        assertEquals(CampfireActionStatus.SUCCESS,
                controller.restAtCampfire().status());

        assertEquals(35, runState.getPlayer().getHealth());
        assertEquals(GamePhase.MAP, controller.getPhase());
        assertTrue(controller.getCurrentCampfireActions().isEmpty());
        assertTrue(controller.getCampfireUpgradeableCards().isEmpty());
        assertTrue(controller.getMapService().getCompletedNodeIds().contains(restNodeId));
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

    @Test
    void shopShouldSupportBuyingRemovingCardAndLeavingToMap() {
        RunState runState = new RunState(
                new Player(50, 3),
                List.of(new CardInstance("strike-1", CardLibrary.STRIKE)),
                200,
                12345L,
                1);
        GameController controller = new GameController(runState, REWARD_POOL, line -> { });

        int safetyCounter = 0;
        while (controller.getPhase() != GamePhase.SHOP && safetyCounter++ < 10) {
            if (controller.getPhase() == GamePhase.MAP) {
                MapNode node = controller.getMapService().getAvailableNodes().getFirst();
                controller.selectNode(node.id());
            }
            if (controller.getPhase() == GamePhase.BATTLE) {
                controller.onLevelFinished(LevelResult.COMPLETED);
                controller.skipRewardCard();
            } else if (controller.getPhase() == GamePhase.EVENT
                    || controller.getPhase() == GamePhase.REST) {
                controller.onLevelFinished(LevelResult.COMPLETED);
            }
        }

        assertEquals(GamePhase.SHOP, controller.getPhase());
        int shopNodeId = controller.getCurrentNode().orElseThrow().id();
        int goldBefore = runState.getGold();
        int deckSizeBefore = runState.getDeck().size();
        ShopItem item = controller.getCurrentShopItems().getFirst();

        assertEquals(ShopActionResult.SUCCESS, controller.buyShopItem(item.id()));
        assertEquals(goldBefore - item.price(), runState.getGold());
        assertEquals(deckSizeBefore + 1, runState.getDeck().size());

        String originalCardId = "strike-1";
        assertEquals(ShopActionResult.SUCCESS,
                controller.removeCardAtShop(originalCardId));
        assertEquals(goldBefore - item.price() - ShopService.CARD_REMOVAL_PRICE,
                runState.getGold());
        assertFalse(runState.getDeck().stream()
                .anyMatch(card -> card.id().equals(originalCardId)));

        controller.leaveShop();

        assertEquals(GamePhase.MAP, controller.getPhase());
        assertTrue(controller.getMapService().getCompletedNodeIds().contains(shopNodeId));
        assertTrue(controller.getCurrentShopItems().isEmpty());
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
            assertEquals(PlayCardResult.SUCCESS, combat.playCard(0));
        }
    }
}
