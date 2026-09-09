package com.roguelike.dungeon.game.card;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * CardLibrary 的单元测试。
 *
 * <p>JUnit 5 使用 {@code @Test} 标记测试方法，方法名通常直接描述预期行为。
 * 下方 import 的 {@code assertEquals}、{@code assertTrue}、{@code assertFalse}
 * 都是断言方法：条件不满足时测试会失败。</p>
 */
class CardLibraryTest {

    /**
     * 验证起始牌组包含 5 张攻击牌和 5 张技能牌，且都是 1 费、不会消耗。
     */
    @Test
    void startingDeckHasTenBasicCards() {
        List<Card> deck = CardLibrary.startingDeck();

        // 起始牌组固定是 10 张：5 打击 + 5 防御。
        assertEquals(10, deck.size());
        // stream().filter(card -> ...) 表示只保留 type 为 ATTACK 的元素再计数。
        assertEquals(5, deck.stream().filter(card -> card.type() == CardType.ATTACK).count());
        // 同理统计技能牌数量。
        assertEquals(5, deck.stream().filter(card -> card.type() == CardType.SKILL).count());
        // allMatch 表示“所有元素都满足条件”，这里是检查所有牌费用都为 1。
        assertTrue(deck.stream().allMatch(card -> card.cost() == 1));
        // anyMatch 表示“任意一个元素满足条件”；Card::exhausts 是方法引用，
        // 等价于 card -> card.exhausts()。这里检查没有任何会消耗的牌。
        assertFalse(deck.stream().anyMatch(Card::exhausts));
    }

    /**
     * 验证按 id 查询到的卡牌规则数据正确。
     */
    @Test
    void cardDefinitionsExposePlayRules() {
        Card bloodletting = CardLibrary.byId("bloodletting");

        // record 的字段通过同名访问器读取，例如 cost() 而不是 getCost()。
        assertEquals(0, bloodletting.cost());
        assertTrue(bloodletting.playable());
        assertFalse(bloodletting.exhausts());
    }
}
