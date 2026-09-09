package com.roguelike.dungeon.game.card;

/**
 * 卡牌效果的执行入口。
 *
 * <p>新卡牌只需要实现这个接口，不必修改战斗主流程。</p>
 *
 * <p><b>Java 8 语法：@FunctionalInterface 与 lambda</b><br>
 * 一个接口如果只有一个抽象方法，就叫“函数式接口”。这里的 {@code CardEffect}
 * 只有一个 {@link #apply(CardEffectContext)}，因此可以写成：</p>
 * <pre>
 * CardEffect effect = context -> context.dealDamageToMonster(6);
 * </pre>
 * <p>它等价于一个只实现 {@code apply} 方法的匿名内部类。{@code @FunctionalInterface}
 * 是编译期提示，不会强制运行时行为；如果以后不小心在接口里再加一个抽象方法，
 * 编译器会直接报错。</p>
 */
@FunctionalInterface
public interface CardEffect {

    /**
     * 执行卡牌效果。
     *
     * @param context 战斗上下文，提供伤害、护甲、抽牌、能量等操作
     */
    void apply(CardEffectContext context);
}
