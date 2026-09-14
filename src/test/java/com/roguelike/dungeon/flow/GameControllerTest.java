package com.roguelike.dungeon.flow;

import com.roguelike.dungeon.game.battle.Combat;
import com.roguelike.dungeon.game.battle.PlayCardResult;
import com.roguelike.dungeon.game.blessing.BlessingActionResult;
import com.roguelike.dungeon.game.blessing.BlessingOption;
import com.roguelike.dungeon.game.blessing.BlessingType;
import com.roguelike.dungeon.game.card.Card;
import com.roguelike.dungeon.game.card.CardInstance;
import com.roguelike.dungeon.game.card.CardLibrary;
import com.roguelike.dungeon.game.card.CardRarity;
import com.roguelike.dungeon.game.campfire.CampfireActionStatus;
import com.roguelike.dungeon.game.entity.BattleEndHealRelic;
import com.roguelike.dungeon.game.entity.Player;
import com.roguelike.dungeon.game.event.EventActionStatus;
import com.roguelike.dungeon.game.event.EventChoice;
import com.roguelike.dungeon.game.map.MapNode;
import com.roguelike.dungeon.game.map.MapNodeType;
import com.roguelike.dungeon.game.relic.RelicLibrary;
import com.roguelike.dungeon.game.reward.BattleReward;
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
    void openingBlessingShouldOfferThreeOptionsThenEnterMap() {
        RunState runState = newRunState(1);
        GameController controller = new GameController(runState, REWARD_POOL, line -> { });

        assertEquals(GamePhase.BLESSING, controller.getPhase());
        assertEquals(3, controller.getCurrentBlessingOptions().size());
        assertEquals(3, controller.getCurrentBlessingOptions().stream()
                .map(BlessingOption::id)
                .distinct()
                .count());

        completeOpeningBlessing(controller);

        assertEquals(GamePhase.MAP, controller.getPhase());
        assertTrue(controller.getCurrentBlessingOptions().isEmpty());
    }

    @Test
    void selectingBattleShouldUseRunStateAndOpenRewardAfterRealVictory() {
        Player player = new Player(50, 3);
        List<CardInstance> deck = java.util.stream.IntStream.range(0, 10)
                .mapToObj(index -> new CardInstance(
                        "quick-slash-" + index, CardLibrary.QUICK_SLASH))
                .toList();
        RunState runState = new RunState(player, deck, 5, 12345L, 1);
        GameController controller = controllerOnMap(runState, REWARD_POOL);
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

        int goldBeforeSkip = runState.getGold();
        controller.skipRewardCard();

        assertEquals(GamePhase.MAP, controller.getPhase());
        assertEquals(goldBeforeSkip + GameController.BATTLE_GOLD_REWARD, runState.getGold());
        assertTrue(controller.getCurrentReward().isEmpty());
        assertTrue(controller.getMapService().getCompletedNodeIds().contains(node.id()));
    }

    @Test
    void battleVictoryShouldHealFromBurningBloodRelic() {
        Player player = new Player(50, 3);
        player.addRelic(new BattleEndHealRelic());
        RunState runState = new RunState(
                player,
                List.of(new CardInstance("strike-1", CardLibrary.STRIKE)),
                0,
                12345L,
                1);
        GameController controller = controllerOnMap(runState, List.of());
        player.setHealth(20);
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
        RunState runState = new RunState(
                player, List.of(), 0, 12345L, 1);
        GameController controller = controllerOnMap(runState, REWARD_POOL);
        player.setHealth(1);
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
        GameController controller = controllerOnMap(
                newRunState(1), REWARD_POOL);

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
        GameController controller = controllerOnMap(
                newRunState(1), REWARD_POOL);

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
        GameController controller = controllerOnMap(
                runState, REWARD_POOL);
        runState.getPlayer().setHealth(20);

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

        int beforeRest = runState.getPlayer().getHealth();
        int healAmount = Math.max(1, runState.getPlayer().getMaxHealth() * 30 / 100);
        assertEquals(CampfireActionStatus.SUCCESS,
                controller.restAtCampfire().status());

        assertEquals(Math.min(runState.getPlayer().getMaxHealth(), beforeRest + healAmount),
                runState.getPlayer().getHealth());
        assertEquals(GamePhase.MAP, controller.getPhase());
        assertTrue(controller.getCurrentCampfireActions().isEmpty());
        assertTrue(controller.getCampfireUpgradeableCards().isEmpty());
        assertTrue(controller.getMapService().getCompletedNodeIds().contains(restNodeId));
    }

    @Test
    void bossVictoryShouldOfferTowerKeyRareCardsAndGoldThenAdvanceAct() {
        Player player = new Player(50, 3);
        RunState runState = new RunState(
                player,
                List.of(new CardInstance("strike-1", CardLibrary.STRIKE)),
                0,
                12345L,
                2);
        List<Card> pool = CardLibrary.rewardPoolFor(CardLibrary.bloodLordRewardCardIds());
        GameController controller = controllerOnMap(runState, pool);

        int safety = 0;
        while (safety++ < 20) {
            assertEquals(GamePhase.MAP, controller.getPhase());
            MapNode node = controller.getMapService().getAvailableNodes().getFirst();
            controller.selectNode(node.id());
            if (controller.getPhase() == GamePhase.BATTLE) {
                controller.onLevelFinished(LevelResult.COMPLETED);
                if (node.type() == MapNodeType.BOSS) {
                    break;
                }
                controller.skipRewardCard();
            } else {
                controller.onLevelFinished(LevelResult.COMPLETED);
            }
        }

        assertEquals(GamePhase.REWARD, controller.getPhase());
        BattleReward reward = controller.getCurrentReward().orElseThrow();
        assertEquals(GameController.BOSS_GOLD_REWARD, reward.gold());
        assertEquals(RelicLibrary.TOWER_KEY, reward.relic().id());
        assertEquals("高塔之匙", reward.relic().name());
        assertEquals("更深度探索的钥匙......", reward.relic().description());
        assertFalse(reward.cardChoices().isEmpty());
        assertTrue(reward.cardChoices().stream()
                .allMatch(card -> card.rarity() == CardRarity.RARE));

        int goldBefore = runState.getGold();
        controller.skipRewardCard();

        assertEquals(2, runState.getCurrentAct());
        assertEquals(GamePhase.MAP, controller.getPhase());
        assertEquals(goldBefore + GameController.BOSS_GOLD_REWARD, runState.getGold());
        assertTrue(player.hasRelicById(RelicLibrary.TOWER_KEY));
        assertTrue(controller.getMapService().getCompletedNodeIds().isEmpty());
    }

    @Test
    void finalBossRewardShouldFinishRunAfterClaim() {
        RunState runState = newRunState(1);
        GameController controller = controllerOnMap(runState, List.of());

        int safety = 0;
        while (safety++ < 20) {
            assertEquals(GamePhase.MAP, controller.getPhase());
            MapNode node = controller.getMapService().getAvailableNodes().getFirst();
            controller.selectNode(node.id());
            if (controller.getPhase() == GamePhase.BATTLE) {
                controller.onLevelFinished(LevelResult.COMPLETED);
                if (node.type() == MapNodeType.BOSS) {
                    break;
                }
                controller.skipRewardCard();
            } else {
                controller.onLevelFinished(LevelResult.COMPLETED);
            }
        }

        assertEquals(GamePhase.REWARD, controller.getPhase());
        controller.skipRewardCard();
        assertEquals(GamePhase.VICTORY, controller.getPhase());
        assertFalse(runState.hasNextAct());
        assertTrue(runState.getPlayer().hasRelicById(RelicLibrary.TOWER_KEY));
    }

    @Test
    void bossShouldAdvanceToNextActAndFinalBossShouldFinishRun() {
        RunState runState = newRunState(2);
        GameController controller = controllerOnMap(runState, List.of());

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
        GameController controller = controllerOnMap(runState, REWARD_POOL);

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
        completeOpeningBlessing(controller);
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
                // 领完 Boss 奖励后可能已经切到新章节或最终胜利。
            }
        }
    }

    private static GameController controllerOnMap(RunState runState, List<Card> pool) {
        GameController controller = new GameController(runState, pool, line -> { });
        completeOpeningBlessing(controller);
        return controller;
    }

    private static void completeOpeningBlessing(GameController controller) {
        if (controller.getPhase() != GamePhase.BLESSING) {
            return;
        }
        BlessingOption option = controller.getCurrentBlessingOptions().stream()
                .filter(BlessingOption::available)
                .filter(choice -> choice.id().equals(BlessingType.GOLD.id()))
                .findFirst()
                .or(() -> controller.getCurrentBlessingOptions().stream()
                        .filter(choice -> choice.available() && !choice.requiresCard())
                        .filter(choice -> !choice.id().equals(BlessingType.DAMAGE_GOLD.id()))
                        .findFirst())
                .or(() -> controller.getCurrentBlessingOptions().stream()
                        .filter(choice -> choice.available() && !choice.requiresCard())
                        .findFirst())
                .orElseThrow();
        BlessingActionResult result = controller.chooseBlessing(option.id());
        assertTrue(result.succeeded(), result.message());
        assertEquals(GamePhase.MAP, controller.getPhase());
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
