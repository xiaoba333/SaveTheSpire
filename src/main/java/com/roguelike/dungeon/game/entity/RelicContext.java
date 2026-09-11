package com.roguelike.dungeon.game.entity;

import com.roguelike.dungeon.game.card.CardInstance;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * 遗物事件上下文：一次触发中所有可被遗物读写的数值。
 *
 * <p>遗物<b>不直接改</b> {@code BattleState}，而是改这里的字段；主流程在分发结束后
 * 统一读取结果。这样多个遗物的修正可以自然叠加，也便于单元测试
 * （构造一个上下文，断言最终数值即可）。</p>
 */
public final class RelicContext {

    private final Player player;
    private final BattleInfo battle;
    private final RelicTrigger trigger;
    private final CardInstance card;
    private final int depth;
    private final Consumer<String> logger;

    private int value;
    private boolean cancelled;

    /**
     * @param player 本局唯一的玩家实体
     * @param battle 当前战斗视图；战斗外触发时为 null
     * @param trigger 当前触发点
     * @param value 初始数值，含义随触发点变化
     * @param card 本次打出的牌；仅 {@link RelicTrigger#CARD_PLAYED} 时非 null
     * @param depth 嵌套深度，0 表示最外层触发
     * @param logger 战斗日志输出
     */
    public RelicContext(
            Player player,
            BattleInfo battle,
            RelicTrigger trigger,
            int value,
            CardInstance card,
            int depth,
            Consumer<String> logger) {
        this.player = Objects.requireNonNull(player, "玩家不能为 null");
        this.battle = battle;
        this.trigger = Objects.requireNonNull(trigger, "触发点不能为 null");
        this.value = Math.max(0, value);
        this.card = card;
        this.depth = depth;
        this.logger = Objects.requireNonNull(logger, "日志处理器不能为 null");
    }

    public Player player() {
        return player;
    }

    /** 当前战斗视图；{@link RelicTrigger#OBTAIN} 等战斗外触发点可能为 null。 */
    public BattleInfo battle() {
        return battle;
    }

    public RelicTrigger trigger() {
        return trigger;
    }

    /** 本次打出的牌；非 {@link RelicTrigger#CARD_PLAYED} 时为 null。 */
    public CardInstance card() {
        return card;
    }

    /** 嵌套深度，0 表示最外层触发。 */
    public int depth() {
        return depth;
    }

    /** 读取当前数值，遗物据此做条件判断。 */
    public int value() {
        return value;
    }

    /** 加法修正，例如「+3 点伤害」。结果不会被压到负数以下。 */
    public void addValue(int delta) {
        this.value = Math.max(0, this.value + delta);
    }

    /** 乘法修正，例如「+50%」传 1.5。结果四舍五入且不为负。 */
    public void multiplyValue(double factor) {
        this.value = Math.max(0, (int) Math.round(this.value * factor));
    }

    /** 直接设置数值，谨慎使用。 */
    public void setValue(int value) {
        this.value = Math.max(0, value);
    }

    /** 本次结算是否已被遗物取消。 */
    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * 取消本次结算。分发器会把最终数值当作 0 返回，
     * 例如「不死鸟之羽」用它在致死伤害前免疫一次。
     *
     * <p>取消只能置位、不能撤销，且一旦取消后续遗物仍会收到回调。</p>
     */
    public void cancel() {
        this.cancelled = true;
    }

    /** 向战斗日志追加一行文本。 */
    public void log(String line) {
        logger.accept(line);
    }
}
