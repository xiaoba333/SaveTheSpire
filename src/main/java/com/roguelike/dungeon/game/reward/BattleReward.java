package com.roguelike.dungeon.game.reward;

import com.roguelike.dungeon.game.card.Card;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 一场战斗结束时展示给玩家的不可变奖励数据。
 *
 * @param gold 金币奖励
 * @param cardChoices 可选择的卡牌定义，最多三张
 */
public record BattleReward(int gold, List<Card> cardChoices) {

    public BattleReward {
        if (gold < 0) {
            throw new IllegalArgumentException("奖励金币不能为负数");
        }
        cardChoices = List.copyOf(Objects.requireNonNull(
                cardChoices, "奖励卡牌列表不能为 null"));
        if (cardChoices.size() > RewardService.CARD_CHOICE_COUNT) {
            throw new IllegalArgumentException("奖励卡牌不能超过三张");
        }

        Set<String> definitionIds = new HashSet<>();
        for (Card card : cardChoices) {
            Objects.requireNonNull(card, "奖励卡牌不能为 null");
            if (card.id() == null || card.id().isBlank()) {
                throw new IllegalArgumentException("奖励卡牌定义编号不能为空");
            }
            if (!definitionIds.add(card.id())) {
                throw new IllegalArgumentException("奖励卡牌定义重复: " + card.id());
            }
        }
    }
}
