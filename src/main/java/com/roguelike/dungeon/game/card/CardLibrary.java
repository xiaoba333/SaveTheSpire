package com.roguelike.dungeon.game.card;

import java.util.List;
import java.util.Map;

/**
 * 当前版本卡牌定义与起始牌组。
 *
 * <p>这里用 Java 代码定义卡牌，后续如果需要做卡牌数据库、升级、稀有度，
 * 可以再抽成 JSON / 数据库配置。</p>
 *
 * <p><b>Java 9 语法：不可变集合工厂方法</b><br>
 * 本类使用了 {@code List.of(...)}、{@code Map.ofEntries(...)} 和 {@code Map.entry(...)}。
 * 它们创建的是不可变集合，适合存储启动后不会变化的卡牌模板。</p>
 *
 * <p><b>Java 8 语法：lambda 表达式</b><br>
 * {@link CardEffect} 是函数式接口，所以每张卡牌的 {@code effect} 参数都写成
 * {@code context -> ...} 或 {@code context -> { ... }}。前者是单表达式 lambda，
 * 后者是带多条语句的 lambda。</p>
 */
public final class CardLibrary {

    /**
     * 打击：基础攻击牌。
     *
     * <p>这里的 {@code context -> context.dealDamageToMonster(6)} 是 lambda 表达式。
     * 它等价于创建匿名内部类：
     * <pre>
     * new CardEffect() {
     *     @Override
     *     public void apply(CardEffectContext context) {
     *         context.dealDamageToMonster(6);
     *     }
     * }
     * </pre>
     * lambda 左侧的 {@code context} 是参数名，右侧是方法体。</p>
     */
    public static final Card STRIKE = new Card(
            "strike",
            "打击",
            CardType.ATTACK,
            1,
            "造成 6 点伤害。",
            context -> context.dealDamageToMonster(6), // 单表达式 lambda
            false,
            true);

    /** 防御：获得 5 点护甲。 */
    public static final Card DEFEND = new Card(
            "defend",
            "防御",
            CardType.SKILL,
            1,
            "获得 5 点护甲。",
            context -> context.addPlayerBlock(5),
            false,
            true);

    /** 痛击：2 费造成 8 点伤害。 */
    public static final Card BASH = new Card(
            "bash",
            "痛击",
            CardType.ATTACK,
            2,
            "造成 8 点伤害。",
            context -> context.dealDamageToMonster(8),
            false,
            true);

    /** 快斩：0 费造成 3 点伤害。 */
    public static final Card QUICK_SLASH = new Card(
            "quick_slash",
            "快斩",
            CardType.ATTACK,
            0,
            "造成 3 点伤害。",
            context -> context.dealDamageToMonster(3),
            false,
            true);

    /** 重击：2 费造成 12 点伤害。 */
    public static final Card HEAVY_STRIKE = new Card(
            "heavy_strike",
            "重击",
            CardType.ATTACK,
            2,
            "造成 12 点伤害。",
            context -> context.dealDamageToMonster(12),
            false,
            true);

    /** 铁斩波：造成 5 点伤害并同时获得 5 点护甲。 */
    public static final Card IRON_WAVE = new Card(
            "iron_wave",
            "铁斩波",
            CardType.ATTACK,
            1,
            "造成 5 点伤害，获得 5 点护甲。",
            context -> {
                context.dealDamageToMonster(5);
                context.addPlayerBlock(5);
            },
            false,
            true);

    /** 耸肩：获得 8 点护甲并抽 1 张牌。 */
    public static final Card SHRUG_IT_OFF = new Card(
            "shrug_it_off",
            "耸肩",
            CardType.SKILL,
            1,
            "获得 8 点护甲，抽 1 张牌。",
            context -> {
                context.addPlayerBlock(8);
                context.drawCards(1);
            },
            false,
            true);

    /** 放血：失去 3 点生命，换取 2 点能量。 */
    public static final Card BLOODLETTING = new Card(
            "bloodletting",
            "放血",
            CardType.SKILL,
            0,
            "失去 3 点生命，获得 2 点能量。",
            context -> {
                context.dealDamageToPlayer(3);
                context.addPlayerEnergy(2);
            },
            false,
            true);

    /**
     * 按卡牌 id 保存所有卡牌模板。
     *
     * <p>{@code Map.ofEntries} 适用于元素较多的情况，每个键值对用
     * {@code Map.entry(key, value)} 创建。这样得到的 Map 不可修改，
     * 能避免程序运行中意外改写卡牌库。</p>
     */
    private static final Map<String, Card> CARDS = Map.ofEntries(
            Map.entry(STRIKE.id(), STRIKE),
            Map.entry(DEFEND.id(), DEFEND),
            Map.entry(BASH.id(), BASH),
            Map.entry(QUICK_SLASH.id(), QUICK_SLASH),
            Map.entry(HEAVY_STRIKE.id(), HEAVY_STRIKE),
            Map.entry(IRON_WAVE.id(), IRON_WAVE),
            Map.entry(SHRUG_IT_OFF.id(), SHRUG_IT_OFF),
            Map.entry(BLOODLETTING.id(), BLOODLETTING));

    /**
     * 工具类不应被实例化。
     *
     * <p>所有成员都是静态常量或静态方法，因此把构造方法设为 private。</p>
     */
    private CardLibrary() {
    }

    /**
     * 创建基础起始牌组：5 张打击 + 5 张防御。
     *
     * <p>{@code List.of} 创建一个不可变列表。由于 {@code STRIKE} 和
     * {@code DEFEND} 本身就是不可变 record，起始牌组也适合保持不变。</p>
     *
     * @return 包含 10 张卡牌模板的新列表
     */
    public static List<Card> startingDeck() {
        return List.of(
                STRIKE, STRIKE, STRIKE, STRIKE, STRIKE,
                DEFEND, DEFEND, DEFEND, DEFEND, DEFEND);
    }

    /**
     * 按 id 查询卡牌定义。
     *
     * @param id 卡牌 id，例如 "strike"
     * @return 对应的卡牌模板
     * @throws IllegalArgumentException 当 id 不存在时
     */
    public static Card byId(String id) {
        Card card = CARDS.get(id);
        if (card == null) {
            throw new IllegalArgumentException("未知卡牌 id：" + id);
        }
        return card;
    }
}
