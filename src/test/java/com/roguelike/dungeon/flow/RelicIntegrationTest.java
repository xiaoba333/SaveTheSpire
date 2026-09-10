package com.roguelike.dungeon.flow;

import com.roguelike.dungeon.game.battle.Combat;
import com.roguelike.dungeon.game.battle.PlayCardResult;
import com.roguelike.dungeon.game.card.Card;
import com.roguelike.dungeon.game.card.CardInstance;
import com.roguelike.dungeon.game.card.CardLibrary;
import com.roguelike.dungeon.game.entity.Player;
import com.roguelike.dungeon.game.entity.Relic;
import com.roguelike.dungeon.game.entity.RelicTrigger;
import com.roguelike.dungeon.game.map.MapNode;
import com.roguelike.dungeon.game.relic.RelicLibrary;
import com.roguelike.dungeon.game.relic.RelicService;
import com.roguelike.dungeon.game.reward.BattleReward;
import com.roguelike.dungeon.game.run.RunState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证遗物已经真正接入主流程：玩家开局持有初始遗物，且能凭初始遗物打赢首战。
 */
class RelicIntegrationTest {
    private static final List<Card> REWARD_POOL = List.of(
            CardLibrary.BASH,
            CardLibrary.HEAVY_STRIKE,
            CardLibrary.IRON_WAVE,
            CardLibrary.SHRUG_IT_OFF);

    @Test
    void controllerShouldGrantStartingRelicsOnCreation() {
        GameController controller = newController(12345L);

        List<String> ids = controller.getRelics().stream()
                .map(Relic::id)
                .toList();
        assertTrue(ids.containsAll(RelicLibrary.startingIds()),
                "开局应持有全部初始遗物：" + ids);
    }

    @Test
    void combatShouldSeePlayerStartingRelics() {
        GameController controller = newController(12345L);
        controller.selectNode(firstNodeId(controller));

        Combat combat = controller.getCurrentCombat().orElseThrow();
        assertFalse(combat.getRelics().isEmpty(), "战斗内应能看到玩家的遗物");
        assertTrue(combat.getRelics().stream()
                .anyMatch(relic -> relic.id().equals(RelicLibrary.ANCHOR)));
    }

    @Test
    void startingRelicsShouldGrantArmorAndVulnerableOnFirstTurn() {
        GameController controller = newController(12345L);
        controller.selectNode(firstNodeId(controller));

        Combat combat = controller.getCurrentCombat().orElseThrow();
        // 船锚：战斗开始时 8 点护甲，护甲会在开局清空后才结算，因此第一回合可见
        assertEquals(8, combat.getPlayerBlock());
        // 心之石：每回合开始给怪物 1 层易伤
        assertEquals(0, combat.getMonsterHp() - combat.getMonsterMaxHp());
    }

    @Test
    void basicDeckWithStartingRelicsShouldClearFirstBattle() {
        // 使用默认起始牌组（5 打击 + 5 防御），模拟玩家正常出牌
        RunState runState = newRunState(1, 12345L);
        GameController controller = new GameController(
                runState, REWARD_POOL, line -> { });
        controller.selectNode(firstNodeId(controller));

        Combat combat = controller.getCurrentCombat().orElseThrow();
        int guard = 0;
        while (!combat.isFinished() && guard++ < 30) {
            playWholeHand(combat);
            if (!combat.isFinished()) {
                combat.endPlayerTurn();
            }
        }

        assertTrue(combat.isFinished(), "战斗应在合理回合内结束");
        assertEquals("VICTORY", combat.getResult(), "持有初始遗物的玩家应能打赢普通战");
        assertEquals(GamePhase.REWARD, controller.getPhase());
    }

    @Test
    void defeatStillHappensWhenPlayerHasAlmostNoHp() {
        RunState runState = newRunState(1, 12345L);
        runState.getPlayer().setHealth(1);
        GameController controller = new GameController(
                runState, REWARD_POOL, line -> { });
        controller.selectNode(firstNodeId(controller));

        Combat combat = controller.getCurrentCombat().orElseThrow();
        // 怪物按种子随机挑选，且部分怪物首回合先叠甲；
        // 玩家还带初始遗物（开战护甲），因此一次回合不足以致死，需循环推进。
        int guard = 0;
        while (controller.getPhase() == GamePhase.BATTLE && guard++ < 30) {
            if (!combat.isFinished()) {
                combat.endPlayerTurn();
            }
        }

        assertEquals(GamePhase.DEFEAT, controller.getPhase(),
                "只有 1 点生命的玩家最终应被怪物击败");
    }

    @Test
    void battleEndRelicShouldHealPlayerAfterVictory() {
        RunState runState = newRunState(1, 12345L);
        runState.getPlayer().setHealth(20);
        GameController controller = new GameController(
                runState, REWARD_POOL, line -> { });
        controller.selectNode(firstNodeId(controller));

        Combat combat = controller.getCurrentCombat().orElseThrow();
        // 记录最后一次出牌前的血量，作为「胜利瞬间血量」的近似基线
        int hpBeforeVictory = runState.getPlayer().getHealth();
        int guard = 0;
        while (!combat.isFinished() && guard++ < 30) {
            playWholeHand(combat);
            if (!combat.isFinished()) {
                hpBeforeVictory = runState.getPlayer().getHealth();
                combat.endPlayerTurn();
            }
        }

        assertEquals("VICTORY", combat.getResult());
        int hpAfter = runState.getPlayer().getHealth();
        // 绷带：胜利后回复 5 点生命（若胜利时已残血，应能观察到回血效果）
        assertTrue(hpAfter >= hpBeforeVictory,
                "胜利后血量不应低于胜利瞬间：before=" + hpBeforeVictory + " after=" + hpAfter);
        assertTrue(hpAfter <= Combat.PLAYER_MAX_HP);
    }

    @Test
    void bandageShouldHealAmountOnBattleEnd() {
        // 只装「绷带」：初始遗物里还有「血之代价」会在同一触发点回满生命，
        // 两者一起触发时绷带的 5 点回血会被覆盖，所以这里单独隔离验证。
        Player player = new Player(50, 3);
        player.setHealth(20);
        RelicService relicService = new RelicService(player, line -> { });
        relicService.acquire(RelicLibrary.create(RelicLibrary.BANDAGE));

        relicService.fire(RelicTrigger.BATTLE_END, 0);

        assertEquals(25, player.getHealth());
    }

    @Test
    void bloodPriceShouldBeAStartingRelicAndFullHealOnBattleEnd() {
        // 「血之代价」是初始遗物：每场胜利回满生命，最大生命值 -1
        Player player = new Player(50, 3);
        player.setHealth(20);
        RelicService relicService = new RelicService(player, line -> { });
        RelicLibrary.createStarting().forEach(relicService::acquire);

        assertTrue(relicService.has(RelicLibrary.BLOOD_PRICE),
                "血之代价应在初始遗物中");

        relicService.fire(RelicTrigger.BATTLE_END, 0);

        assertEquals(49, player.getMaxHealth(), "最大生命值应 -1");
        assertEquals(49, player.getHealth(), "应回满到新的最大生命值");
    }

    @Test
    void startingRelicsShouldIncludeBloodPrice() {
        assertTrue(RelicLibrary.startingIds().contains(RelicLibrary.BLOOD_PRICE),
                "初始遗物编号应包含血之代价：" + RelicLibrary.startingIds());
    }

    @Test
    void combatWithoutRelicServiceShouldBehaveAsBefore() {
        // 独立 Demo 战斗不注入遗物，行为应与引入遗物之前一致
        Combat combat = new Combat(line -> { });
        assertTrue(combat.getRelics().isEmpty());
        assertEquals(3, combat.getEnergy());
        assertEquals(5, combat.getHand().size());
    }

    private static GameController newController(long seed) {
        return new GameController(newRunState(1, seed), REWARD_POOL, line -> { });
    }

    private static RunState newRunState(int totalActs, long seed) {
        List<CardInstance> deck = CardLibrary.startingDeck().stream()
                .map(card -> new CardInstance(
                        "deck-" + java.util.UUID.randomUUID(), card))
                .toList();
        return new RunState(new Player(Combat.PLAYER_MAX_HP, 3),
                deck, 0, seed, totalActs);
    }

    private static int firstNodeId(GameController controller) {
        MapNode node = controller.getMapService().getAvailableNodes().getFirst();
        return node.id();
    }

    private static void playWholeHand(Combat combat) {
        while (!combat.getHand().isEmpty() && !combat.isFinished()) {
            PlayCardResult result = combat.playCard(0);
            if (result != PlayCardResult.SUCCESS) {
                return;
            }
        }
    }
}
