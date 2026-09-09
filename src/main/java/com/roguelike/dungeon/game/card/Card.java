package com.roguelike.dungeon.game.card;

/**
 * 一张卡牌的不可变定义。
 *
 * <p>卡牌实例会在牌堆、手牌、弃牌堆之间移动，因此这里不保存任何战斗状态，
 * 实际效果由 {@link CardEffect} 在打出时执行。</p>
 *
 * <p><b>Java 21 语法：record</b><br>
 * record 是 JDK 14 引入（预览）、JDK 16 正式确定的一种特殊轻量级类，主要用来透明地承载不可变数据。
 * 你可以把它理解为“数据载体”：它的核心目标就是替代那些只有字段、构造方法、getter、equals、hashCode
 * 和 toString 的“纯粹数据类”（POJO/DTO/VO）。定义 record 后，编译器会自动生成：</p>
 * <ul>
 *   <li>与圆括号中组件一一对应的私有 final 字段；</li>
 *   <li>包含全部组件的构造方法；</li>
 *   <li>形如 {@code id()}、{@code name()} 的访问器；</li>
 *   <li>{@code equals(Object)}、{@code hashCode()}、{@code toString()}。</li>
 * </ul>
 *
 * <p>所以调用方读取字段时使用 {@code card.id()}，而不是 JavaBean 常见的 {@code card.getId()}。</p>
 */
public record Card(
        String id,        // 卡牌定义的唯一标识，例如 "strike"
        String name,      // 显示名称，例如 "打击"
        CardType type,    // 攻击、技能、能力等分类
        int cost,         // 打出时需要消耗的能量
        String description, // 界面展示的卡牌效果说明
        CardEffect effect,  // 实际战斗效果，出牌时执行
        boolean exhausts,   // true 表示使用后进入消耗堆，不进入弃牌堆
        boolean playable    // true 表示可以由玩家主动打出
) {

    /**
     * record 的紧凑构造器（compact constructor）。
     * <p>普通类的构造方法有参数列表     *和赋值语句；record 可以省略参数列表，
     * 在这里写校验逻辑。编译器会先自动把圆括号中的组件赋给字段，再执行这段校验。</p>
     */
    public Card {
        // 能量费用不能是负数，否则后续出牌判断会被破坏。
        if (cost < 0) {
            throw new IllegalArgumentException("卡牌费用不能为负数");
        }
    }

    /**
     * 按钮上显示的文字，例如「1费 打击」。
     *
     * @return 由费用和卡牌名组成的标签文本
     */
    public String label() {
        return cost + "费 " + name;
    }
}
