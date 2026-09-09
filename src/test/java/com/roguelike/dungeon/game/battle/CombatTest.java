package com.roguelike.dungeon.game.battle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Combat 核心规则测试。
 *
 * <p>这些测试不依赖 JavaFX 界面，只验证战斗状态机。每个测试都创建独立的
 * {@code Combat} 对象，避免测试之间互相影响。</p>
 *
 * <p>{@code new Combat(logs::add)} 中的 {@code logs::add} 是方法引用，
 * 它把 {@code List.add(String)} 作为 {@code Consumer<String>} 传给战斗对象。</p>
 */
class CombatTest {

    /**
     * 出牌应消耗 1 点能量，并把该牌从手牌移动到弃牌堆。
     */
    @Test
    void playingCardConsumesEnergyAndMovesCardToDiscard() {
        List<String> logs = new ArrayList<>();
        Combat combat = new Combat(logs::add);

        // 新战斗初始状态：玩家 3 能量、5 张手牌，抽牌堆还剩 5 张。
        assertEquals(3, combat.getEnergy());
        assertEquals(5, combat.getHand().size());
        assertEquals(5, combat.getDrawPileSize());
        assertEquals(0, combat.getDiscardPileSize());
        assertEquals(1, combat.getTurnNumber());
        assertEquals("PLAYER_TURN", combat.getPhase());
        assertNull(combat.getResult());
        assertNotNull(combat.getMonsterIntentInfo());
        // record 组件通过 type() 访问器读取，而不是 getType()。
        assertEquals("ATTACK", combat.getMonsterIntentInfo().type());
        // 新战斗会产生起始日志，因此取走增量日志后不应为空。
        assertTrue(!combat.drainNewLogs().isEmpty());

        // 打出第一张手牌；起始牌组全是 1 费，所以应该成功。
        assertEquals(Combat.PlayCardResult.SUCCESS, combat.playCard(0));

        // 能量从 3 变 2，手牌从 5 变 4，弃牌堆从 0 变 1。
        assertEquals(2, combat.getEnergy());
        assertEquals(4, combat.getHand().size());
        assertEquals(1, combat.getDiscardPileSize());
        // stream().anyMatch(...) 判断日志列表中是否出现过“消耗 1 点能量”。
        assertTrue(logs.stream().anyMatch(line -> line.contains("消耗 1 点能量")));
    }

    /**
     * HTTP 风格接口应能通过牌实例 id 找到并打出手牌。
     */
    @Test
    void canPlayCardByInstanceId() {
        List<String> logs = new ArrayList<>();
        Combat combat = new Combat(logs::add);
        // 获取第一张手牌的实例 id。
        String instanceId = combat.getHand().get(0).id();

        assertEquals(Combat.PlayCardResult.SUCCESS, combat.playCard(instanceId));

        // 打出后，手牌中不应再存在相同实例 id 的牌。
        assertFalse(combat.getHand().stream().anyMatch(instance -> instance.id().equals(instanceId)));
        assertEquals(4, combat.getHand().size());
        assertEquals(1, combat.getDiscardPileSize());
    }

    /**
     * 能量不足时不应移牌或弃牌，并应返回 NOT_ENOUGH_ENERGY。
     */
    @Test
    void cardCannotBePlayedWithoutEnoughEnergy() {
        List<String> logs = new ArrayList<>();
        Combat combat = new Combat(logs::add);

        // 连续打出 3 张 1 费牌后，能量刚好降到 0。
        combat.playCard(0);
        combat.playCard(0);
        combat.playCard(0);

        // 保存第 4 次尝试之前的状态，稍后验证失败操作没有产生副作用。
        int handSize = combat.getHand().size();
        int discardSize = combat.getDiscardPileSize();
        int energy = combat.getEnergy();

        combat.playCard(0);

        // 能量、手牌、弃牌堆都应该保持不变。
        assertEquals(0, energy);
        assertEquals(handSize, combat.getHand().size());
        assertEquals(discardSize, combat.getDiscardPileSize());
        // 日志中应出现能量不足的提示。
        assertTrue(logs.stream().anyMatch(line -> line.contains("能量不足")));
    }

    /**
     * 结束回合会弃掉手牌，并在怪物行动后恢复能量、补满手牌、进入下一回合。
     */
    @Test
    void endingTurnDiscardsHandAndRefreshesEnergy() {
        List<String> logs = new ArrayList<>();
        Combat combat = new Combat(logs::add);

        // 先打出 3 张牌，使当前能量为 0。
        combat.playCard(0);
        combat.playCard(0);
        combat.playCard(0);
        combat.endPlayerTurn();

        // 新回合开始时，能量重置为 3，手牌重新抽到 5 张。
        assertEquals(3, combat.getEnergy());
        assertEquals(5, combat.getHand().size());
        assertTrue(combat.isPlayerTurn());
        assertEquals(2, combat.getTurnNumber());
        assertEquals("PLAYER_TURN", combat.getPhase());
        assertTrue(!combat.drainNewLogs().isEmpty());
    }
}
