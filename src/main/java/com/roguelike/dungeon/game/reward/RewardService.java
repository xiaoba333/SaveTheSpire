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

/** 生成并结算一场普通、精英或 Boss 战斗的奖励。 */
public final class RewardService {
    public static final int CARD_CHOICE_COUNT = 3;
    /** Boss 遗物三选一的候选数量。 */
    public static final int BOSS_RELIC_CHOICE_COUNT = 3;

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

    /**
     * 创建一份待领取的战斗奖励，并附带指定遗物（例如 Boss 固定掉落高塔之匙）。
     *
     * @param guaranteedRelic 固定发放的遗物；为 null 时不附带遗物
     */
    public RewardService(
            RunState runState,
            List<Card> rewardPool,
            long rewardSeed,
            int gold,
            RelicService relicService,
            Relic guaranteedRelic) {
        this(runState,
                generateReward(rewardPool, rewardSeed, gold, guaranteedRelic),
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
        return generateReward(rewardPool, rewardSeed, gold, (RelicService) null);
    }

    /**
     * 从卡池中抽取卡牌，并附带一件指定遗物。
     */
    public static BattleReward generateReward(
            List<Card> rewardPool,
            long rewardSeed,
            int gold,
            Relic relic) {
        BattleReward cardsAndGold = generateReward(rewardPool, rewardSeed, gold);
        return new BattleReward(cardsAndGold.gold(), cardsAndGold.cardChoices(), relic);
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

    /**
     * 构造一份 <b>Boss 遗物三选一</b> 奖励。
     *
     * <p>与普通战斗奖励的三点差异，对齐杀戮尖塔的 Boss 宝箱：</p>
     * <ul>
     *   <li>不发金币（{@code gold = 0}）—— 宝箱只给遗物。</li>
     *   <li>没有卡牌候选 —— 卡牌奖励是普通战斗的事。</li>
     *   <li>遗物是「候选」而非「命中即得」，必须调用 {@link #selectRelic(String)} 才算领取。</li>
     * </ul>
     *
     * @param choices 候选遗物；由 {@code RelicLibrary.bossChoices} 负责去重与洗牌
     */
    public static RewardService forBossRelicChoice(
            RunState runState,
            List<Relic> choices,
            RelicService relicService) {
        Objects.requireNonNull(choices, "Boss 遗物候选不能为 null");
        if (choices.isEmpty()) {
            throw new IllegalArgumentException("Boss 遗物候选不能为空");
        }
        return new RewardService(
                runState,
                new BattleReward(0, List.of(), null, choices),
                () -> UUID.randomUUID().toString(),
                relicService);
    }

    /**
     * 领取 Boss 遗物三选一中的一件，并完成奖励结算。
     *
     * <p>未被选中的候选不产生任何副作用 —— 它们只是展示用的数据对象。</p>
     *
     * @param relicId 所选遗物编号，必须落在候选列表内
     * @return 固定返回 COMPLETED
     */
    public LevelResult selectRelic(String relicId) {
        ensureUnresolved();
        if (!reward.hasRelicChoices()) {
            throw new IllegalStateException("当前奖励不是 Boss 遗物三选一");
        }
        if (relicService == null) {
            throw new IllegalStateException("未接入遗物服务，无法发放 Boss 遗物");
        }
        Relic chosen = reward.findRelicChoice(relicId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "所选遗物不在 Boss 候选列表中: " + relicId));
        relicService.acquire(chosen);
        resolved = true;
        return LevelResult.COMPLETED;
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
        ensureCardReward();
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
        ensureCardReward();
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

    /**
     * Boss 遗物三选一没有卡牌可领，因此不能走领卡 / 跳过卡的路径。
     *
     * <p>不加这道闸的话，一次误调就会「什么都没发」却把奖励标记成已结算，
     * 玩家将永远拿不到 Boss 遗物 —— 是这个流程最容易踩的静默失败。</p>
     */
    private void ensureCardReward() {
        if (reward.hasRelicChoices()) {
            throw new IllegalStateException(
                    "Boss 遗物三选一必须先选定遗物才能结算");
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
