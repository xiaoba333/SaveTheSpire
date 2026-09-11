package com.roguelike.dungeon.game.entity;

import com.roguelike.dungeon.game.card.CardInstance;

import java.util.Set;

/**
 * 遗物接口：本局内永久生效的被动道具。
 *
 * <p>遗物通过 {@link #triggers()} 声明自己关心哪些触发点，逻辑统一写在
 * {@link #onTrigger} 里。这样做的原因是：触发点有近十个，如果每个都做成
 * default 方法，每写一个遗物都要在 IDE 里翻十几个空方法；用枚举注册则
 * 分发器可以建立索引，也更容易在界面上标注遗物的生效时机。</p>
 *
 * <p>遗物持有在 {@link Player} 上，随整局游戏一直生效。
 * 无状态遗物可以是单例，带有累计计数的遗物必须每次创建新实例
 * （见 {@code RelicLibrary}）。</p>
 */
public interface Relic {

    /** 遗物唯一编号，用于存档、前端查图与防止重复获取，例如 {@code "anchor"}。 */
    String id();

    /** 遗物中文名。 */
    String name();

    /** 遗物效果描述。 */
    String description();

    /** 稀有度，决定掉落来源。 */
    RelicRarity rarity();

    /**
     * 声明本遗物需要接收哪些触发点。
     * 返回空集合表示只在获得时生效。默认空实现。
     */
    default Set<RelicTrigger> triggers() {
        return Set.of();
    }

    /**
     * 统一的触发入口。只有 {@link #triggers()} 里声明过的时机才会被调用。
     *
     * @param trigger 当前触发点
     * @param ctx 事件上下文，遗物通过修改 ctx 上的数值来影响结算
     */
    default void onTrigger(RelicTrigger trigger, RelicContext ctx) {
    }

    /**
     * 修改一张牌的费用。
     *
     * <p>{@link CardInstance#effectiveCost()} 是只依赖「卡牌模板 + 是否升级」的纯函数，
     * 无法承载遗物修正，因此费用统一由分发器逐层询问遗物。</p>
     *
     * @param instance 要打出的牌
     * @param currentCost 当前费用
     * @return 修正后的费用，默认原样返回
     */
    default int modifyCost(CardInstance instance, int currentCost) {
        return currentCost;
    }

    /**
     * 进入新关卡时触发。默认空实现，具体遗物按需覆写。
     *
     * @param player 本局唯一的玩家实体，遗物直接在其上修改状态
     */
    default void onEnterLevel(Player player) {
    }

    /**
     * 战斗胜利结束时触发。默认空实现，具体遗物按需覆写。
     *
     * @param player 本局唯一的玩家实体，遗物直接在其上修改状态
     */
    default void onBattleEnd(Player player) {
    }
}
