package com.roguelike.dungeon.game.reward;

import com.roguelike.dungeon.flow.LevelResult;
import com.roguelike.dungeon.game.card.Card;
import com.roguelike.dungeon.game.card.CardInstance;
import com.roguelike.dungeon.game.run.RunState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.function.Supplier;

/** 生成并结算一场普通或精英战斗的奖励。 */
public final class RewardService {
    public static final int CARD_CHOICE_COUNT = 3;

    private final RunState runState;
    private final BattleReward reward;
    private final Supplier<String> instanceIdSupplier;
    private boolean resolved;

    /**
     * 创建一份待领取的战斗奖励。
     *
     * @param runState 当前单局共享状态
     * @param rewardPool 可作为奖励的卡牌池
     * @param rewardSeed 本次奖励随机种子
     * @param gold 金币奖励
     */
    public RewardService(
            RunState runState,
            List<Card> rewardPool,
            long rewardSeed,
            int gold) {
        this(runState, generateReward(rewardPool, rewardSeed, gold),
                () -> UUID.randomUUID().toString());
    }

    /** 供测试注入稳定卡牌实例编号。 */
    RewardService(
            RunState runState,
            BattleReward reward,
            Supplier<String> instanceIdSupplier) {
        this.runState = Objects.requireNonNull(runState, "单局状态不能为 null");
        this.reward = Objects.requireNonNull(reward, "战斗奖励不能为 null");
        this.instanceIdSupplier = Objects.requireNonNull(
                instanceIdSupplier, "卡牌实例编号生成器不能为 null");
    }

    /**
     * 从卡池中确定性地抽取最多三张不同定义的卡牌。
     * 同一卡池和随机种子始终得到相同选择。
     */
    public static BattleReward generateReward(
            List<Card> rewardPool,
            long rewardSeed,
            int gold) {
        Objects.requireNonNull(rewardPool, "奖励卡池不能为 null");

        Map<String, Card> uniqueCards = new LinkedHashMap<>();
        for (Card card : rewardPool) {
            Objects.requireNonNull(card, "奖励卡池不能包含 null");
            if (card.id() == null || card.id().isBlank()) {
                throw new IllegalArgumentException("奖励卡牌定义编号不能为空");
            }
            uniqueCards.putIfAbsent(card.id(), card);
        }

        List<Card> choices = new ArrayList<>(uniqueCards.values());
        Collections.shuffle(choices, new Random(rewardSeed));
        int choiceCount = Math.min(CARD_CHOICE_COUNT, choices.size());
        return new BattleReward(gold, choices.subList(0, choiceCount));
    }

    public BattleReward getReward() {
        return reward;
    }

    public boolean isResolved() {
        return resolved;
    }

    /**
     * 领取指定卡牌和金币，并完成奖励结算。
     *
     * @param cardDefinitionId 奖励候选中的卡牌定义编号
     * @return 固定返回 COMPLETED
     */
    public LevelResult claimCard(String cardDefinitionId) {
        ensureUnresolved();
        Card selectedCard = reward.cardChoices().stream()
                .filter(card -> card.id().equals(cardDefinitionId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "所选卡牌不在奖励列表中: " + cardDefinitionId));

        ensureGoldWillNotOverflow();
        String instanceId = Objects.requireNonNull(
                instanceIdSupplier.get(), "生成的卡牌实例编号不能为 null");
        if (instanceId.isBlank()) {
            throw new IllegalStateException("生成的卡牌实例编号不能为空");
        }

        runState.addCard(new CardInstance(instanceId, selectedCard));
        runState.addGold(reward.gold());
        resolved = true;
        return LevelResult.COMPLETED;
    }

    /** 跳过卡牌选择，只领取金币并完成奖励结算。 */
    public LevelResult skipCard() {
        ensureUnresolved();
        ensureGoldWillNotOverflow();
        runState.addGold(reward.gold());
        resolved = true;
        return LevelResult.COMPLETED;
    }

    private void ensureUnresolved() {
        if (resolved) {
            throw new IllegalStateException("战斗奖励已经结算");
        }
    }

    private void ensureGoldWillNotOverflow() {
        try {
            Math.addExact(runState.getGold(), reward.gold());
        } catch (ArithmeticException exception) {
            throw new IllegalStateException("领取奖励后金币超出整数范围", exception);
        }
    }
}
