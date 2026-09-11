package com.roguelike.dungeon.game.relic;

import com.roguelike.dungeon.game.card.CardInstance;
import com.roguelike.dungeon.game.entity.BattleInfo;
import com.roguelike.dungeon.game.entity.Player;
import com.roguelike.dungeon.game.entity.Relic;
import com.roguelike.dungeon.game.entity.RelicContext;
import com.roguelike.dungeon.game.entity.RelicTrigger;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 遗物分发器：索引触发点、按获得顺序分发、防止递归失控。
 *
 * <p>生命周期是<b>一局一个</b>（由 {@code GameController} 创建），
 * 战斗开始时通过 {@link #bindBattle} 绑定当前战斗视图。这样遗物即使在
 * 战斗之间获得（精英奖励、商店），也能立即生效。</p>
 *
 * <p>分发顺序固定为「获得先后」，保证同一局内结果可复现。</p>
 */
public final class RelicService {

    /**
     * 单次事件的递归深度上限。
     *
     * <p>理论上遗物之间可能互相触发（例如「手里剑」的穿透伤害如果再触发
     * 「造成伤害」类遗物）。超过该深度直接跳过并写日志，避免栈溢出。</p>
     */
    public static final int MAX_DEPTH = 4;

    private final Player player;
    private final Consumer<String> logger;

    /** 触发点 → 关心它的遗物，保持获得顺序。 */
    private final Map<RelicTrigger, List<Relic>> index = new EnumMap<>(RelicTrigger.class);

    private BattleInfo battle;
    private int depth;

    public RelicService(Player player, Consumer<String> logger) {
        this.player = Objects.requireNonNull(player, "玩家不能为 null");
        this.logger = Objects.requireNonNull(logger, "日志处理器不能为 null");
    }

    /** 绑定当前战斗视图，战斗开始时由 {@code Combat} 调用。 */
    public void bindBattle(BattleInfo battle) {
        this.battle = battle;
    }

    /** 解绑战斗视图，战斗结束后调用；之后战斗内触发点不再生效。 */
    public void unbindBattle() {
        this.battle = null;
    }

    /**
     * 获得一个遗物：登记触发点索引，并立刻触发一次 {@link RelicTrigger#OBTAIN}。
     *
     * @return 重复持有同一 id 的遗物时返回 false，不重复叠加
     */
    public boolean acquire(Relic relic) {
        Objects.requireNonNull(relic, "遗物不能为 null");
        if (player.hasRelicById(relic.id())) {
            logger.accept("已持有遗物「" + relic.name() + "」，跳过重复获取。");
            return false;
        }
        player.addRelic(relic);
        for (RelicTrigger trigger : relic.triggers()) {
            if (trigger == RelicTrigger.OBTAIN) {
                continue;   // 获得时直接派发，不进索引，避免重复触发
            }
            index.computeIfAbsent(trigger, key -> new ArrayList<>()).add(relic);
        }
        if (relic.triggers().contains(RelicTrigger.OBTAIN)) {
            relic.onTrigger(
                    RelicTrigger.OBTAIN,
                    newContext(RelicTrigger.OBTAIN, 0, null));
        }
        return true;
    }

    /** 当前持有的遗物，按获得顺序。 */
    public List<Relic> relics() {
        return player.getRelics();
    }

    /** 本局唯一的玩家实体；掉落池需要用它排除已持有的遗物。 */
    public Player player() {
        return player;
    }

    /** 是否已持有指定编号的遗物。 */
    public boolean has(String relicId) {
        return player.hasRelicById(relicId);
    }

    /** 分发一次触发，不涉及具体卡牌。 */
    public int fire(RelicTrigger trigger, int value) {
        return fire(trigger, value, null);
    }

    /**
     * 分发一次触发。
     *
     * @param trigger 触发点
     * @param value 初始数值（伤害量 / 护甲量 / 抽牌数，含义随触发点而定）
     * @param card 本次打出的牌；仅 {@link RelicTrigger#CARD_PLAYED} 需要
     * @return 所有遗物修正后的最终数值
     */
    public int fire(RelicTrigger trigger, int value, CardInstance card) {
        Objects.requireNonNull(trigger, "触发点不能为 null");
        int safeValue = Math.max(0, value);
        List<Relic> listeners = index.get(trigger);
        if (listeners == null || listeners.isEmpty()) {
            return safeValue;
        }
        if (depth >= MAX_DEPTH) {
            logger.accept("遗物触发层级过深（" + trigger.displayName()
                    + "），已跳过本次结算。");
            return safeValue;
        }
        RelicContext ctx = newContext(trigger, safeValue, card);
        depth++;
        try {
            for (Relic relic : List.copyOf(listeners)) {
                relic.onTrigger(trigger, ctx);
            }
        } finally {
            depth--;
        }
        return ctx.isCancelled() ? 0 : ctx.value();
    }

    /**
     * 询问遗物是否要修改一张牌的费用。
     *
     * <p>读取的是 {@link CardInstance#effectiveCost()}（已含升级减费），
     * 再逐个交给遗物修正，最终下限为 0。</p>
     *
     * @param instance 要打出的牌
     * @return 实际需要消耗的能量
     */
    public int modifyCost(CardInstance instance) {
        Objects.requireNonNull(instance, "卡牌实例不能为 null");
        int cost = instance.effectiveCost();
        for (Relic relic : player.getRelics()) {
            cost = Math.max(0, relic.modifyCost(instance, cost));
        }
        return cost;
    }

    private RelicContext newContext(RelicTrigger trigger, int value, CardInstance card) {
        return new RelicContext(player, battle, trigger, value, card, depth, logger);
    }
}
