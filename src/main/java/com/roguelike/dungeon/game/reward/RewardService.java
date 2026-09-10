package com.roguelike.dungeon.game.reward;

import com.roguelike.dungeon.flow.LevelResult;
import com.roguelike.dungeon.game.card.Card;
import com.roguelike.dungeon.game.card.CardInstance;
import com.roguelike.dungeon.game.entity.Relic;
import com.roguelike.dungeon.game.entity.RelicRarity;
import com.roguelike.dungeon.game.relic.RelicLibrary;
import com.roguelike.dungeon.game.relic.RelicService;
import com.roguelike.dungeon.game.run.RunState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.function.Supplier;

/** 生成并结算一场普通或精英战斗的奖励。 */
public final class RewardService {
    public static final int CARD_CHOICE_COUNT = 3;

    private final RunState runState;
    private final BattleReward reward;
    private final Supplier<String> instanceIdSupplier;
    /** 遗物分发器；为 null 时遗物奖励只展示不发放（用于隔离测试）。 */
    private final RelicService relicService;
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
                () -> UUID.randomUUID().toString(), null);
    }

    /**
     * 创建一份待领取的战斗奖励，并接入遗物。
     *
     * <p>传入 {@code relicService} 后会按稀有度掉落池尝试生成一个遗物，
     * 玩家领取时通过 {@link RelicService#acquire(Relic)} 正式生效。</p>
     *
     * @param relicService 本局共享的遗物分发器；传 null 表示本场不产出遗物
     * @param relicRarities 允许掉落的遗物稀有度；为空表示按档位默认
     */
    public RewardService(
            RunState runState,
            List<Card> rewardPool,
            long rewardSeed,
            int gold,
            RelicService relicService,
            RelicRarity... relicRarities) {
        this(runState,
                generateReward(rewardPool, rewardSeed, gold, relicService,
                        relicRarities),
                () -> UUID.randomUUID().toString(),
                relicService);
    }

    /** 供测试注入稳定卡牌实例编号。 */
    RewardService(
            RunState runState,
            BattleReward reward,
            Supplier<String> instanceIdSupplier) {
        this(runState, reward, instanceIdSupplier, null);
    }

    /** 供测试注入稳定卡牌实例编号与遗物分发器。 */
    RewardService(
            RunState runState,
            BattleReward reward,
            Supplier<String> instanceIdSupplier,
            RelicService relicService) {
        this.runState = Objects.requireNonNull(runState, "单局状态不能为 null");
        this.reward = Objects.requireNonNull(reward, "战斗奖励不能为 null");
        this.instanceIdSupplier = Objects.requireNonNull(
                instanceIdSupplier, "卡牌实例编号生成器不能为 null");
        this.relicService = relicService;
    }

    /**
     * 从卡池中确定性地抽取最多三张不同定义的卡牌。
     * 同一卡池和随机种子始终得到相同选择。
     */
    public static BattleReward generateReward(
            List<Card> rewardPool,
            long rewardSeed,
            int gold) {
        return generateReward(rewardPool, rewardSeed, gold, null);
    }

    /**
     * 从卡池中确定性地抽取最多三张不同定义的卡牌，并可选地附带一个遗物。
     *
     * <p>遗物掉落同样由 {@code rewardSeed} 决定，保证同一局面可复现。</p>
     */
    public static BattleReward generateReward(
            List<Card> rewardPool,
            long rewardSeed,
            int gold,
            RelicService relicService,
            RelicRarity... relicRarities) {
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

        Relic relic = null;
        if (relicService != null) {
            relic = RelicLibrary.randomReward(
                            relicSeed(rewardSeed),
                            relicService.player(),
                            relicRarities)
                    .orElse(null);
        }
        return new BattleReward(gold, choices.subList(0, choiceCount), relic);
    }

    /** 遗物掉落使用与卡牌不同的种子偏移，避免两者总是同步变化。 */
    private static long relicSeed(long rewardSeed) {
        return rewardSeed * 31 + 0x9E3779B97F4A7C15L;
    }

    public BattleReward getReward() {
        return reward;
    }

    public boolean isResolved() {
        return resolved;
    }

    /**
     * 领取指定卡牌和金币，并完成奖励结算。若奖励中有遗物，会一并发放。
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
        grantRelic();
        resolved = true;
        return LevelResult.COMPLETED;
    }

    /** 跳过卡牌选择，只领取金币（如有遗物一并领取）并完成奖励结算。 */
    public LevelResult skipCard() {
        ensureUnresolved();
        ensureGoldWillNotOverflow();
        runState.addGold(reward.gold());
        grantRelic();
        resolved = true;
        return LevelResult.COMPLETED;
    }

    /** 发放奖励中的遗物；没有遗物或未接入遗物服务时忽略。 */
    private void grantRelic() {
        if (relicService != null && reward.hasRelic()) {
            relicService.acquire(reward.relic());
        }
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
