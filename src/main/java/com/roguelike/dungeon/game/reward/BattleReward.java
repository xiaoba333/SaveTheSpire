package com.roguelike.dungeon.game.reward;

import com.roguelike.dungeon.game.card.Card;
import com.roguelike.dungeon.game.entity.Relic;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 一场战斗结束时展示给玩家的不可变奖励数据。
 *
 * <p>遗物奖励有两种形态，<b>互斥</b>：</p>
 *
 * <ul>
 *   <li><b>普通掉落</b>：{@link #relic} 单件，「命中即得」，玩家没有选择权
 *       （对齐杀戮尖塔的普通战斗遗物掉落）。</li>
 *   <li><b>Boss 三选一</b>：{@link #relicChoices} 三件，玩家必须选一件
 *       （对齐杀戮尖塔的 Boss 宝箱）。</li>
 * </ul>
 *
 * <p>之所以保留两个字段而不是统一成一个列表，是因为两者的结算语义不同：
 * 前者在领取卡牌时自动发放，后者需要玩家显式选择 —— 混在一起会让
 * {@code RewardService} 里到处出现「列表长度为 3 才需要选择」这类隐式判断。</p>
 *
 * @param gold 金币奖励
 * @param cardChoices 可选择的卡牌定义，最多三张
 * @param relic 普通掉落的遗物；为 null 表示本次不掉落
 * @param relicChoices Boss 遗物候选（三选一）；为空表示不是 Boss 遗物奖励
 */
public record BattleReward(
        int gold,
        List<Card> cardChoices,
        Relic relic,
        List<Relic> relicChoices) {

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

        relicChoices = List.copyOf(Objects.requireNonNull(
                relicChoices, "Boss 遗物候选列表不能为 null"));
        if (relicChoices.size() > RewardService.BOSS_RELIC_CHOICE_COUNT) {
            throw new IllegalArgumentException("Boss 遗物候选不能超过三件");
        }
        if (relic != null && !relicChoices.isEmpty()) {
            throw new IllegalArgumentException(
                    "普通遗物掉落与 Boss 三选一不能同时存在");
        }
        Set<String> relicIds = new HashSet<>();
        for (Relic choice : relicChoices) {
            Objects.requireNonNull(choice, "Boss 遗物候选不能为 null");
            if (!relicIds.add(choice.id())) {
                throw new IllegalArgumentException("Boss 遗物候选重复: " + choice.id());
            }
        }
    }

    /** 兼容旧调用：只有金币与卡牌、没有遗物的奖励。 */
    public BattleReward(int gold, List<Card> cardChoices) {
        this(gold, cardChoices, null, List.of());
    }

    /** 兼容旧调用：普通遗物掉落（单件，命中即得）。 */
    public BattleReward(int gold, List<Card> cardChoices, Relic relic) {
        this(gold, cardChoices, relic, List.of());
    }

    /** 本次奖励是否包含普通遗物掉落。 */
    public boolean hasRelic() {
        return relic != null;
    }

    /** 本次奖励是否为 Boss 遗物三选一。 */
    public boolean hasRelicChoices() {
        return !relicChoices.isEmpty();
    }

    /**
     * 按编号在 Boss 遗物候选中查找。
     *
     * @return 命中则返回该遗物，否则为空 —— 调用方据此拒绝非法选择
     */
    public Optional<Relic> findRelicChoice(String relicId) {
        if (relicId == null || relicId.isBlank()) {
            return Optional.empty();
        }
        return relicChoices.stream()
                .filter(choice -> relicId.equals(choice.id()))
                .findFirst();
    }
}
