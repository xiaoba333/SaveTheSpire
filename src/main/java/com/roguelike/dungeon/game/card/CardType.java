package com.roguelike.dungeon.game.card;

/**
 * 卡牌大类，参考《杀戮尖塔》的卡牌分类。
 *
 * <p>枚举（enum）在 Java 5 已经存在。每个枚举常量都是当前枚举类型的唯一实例，
 * 因此这里用它们表示固定、有限的卡牌分类。</p>
 */
public enum CardType {
    /** 攻击牌，主要用于直接造成伤害。 */
    ATTACK("攻击"),
    /** 技能牌，用于护甲、抽牌、能量等辅助效果。 */
    SKILL("技能"),
    /** 能力牌，打出后通常在本场战斗中持续生效。 */
    POWER("能力"),
    /** 状态牌，通常由敌人塞入牌组，属于负面牌。 */
    STATUS("状态"),
    /** 诅咒牌，无法主动打出或需要付出代价才能处理。 */
    CURSE("诅咒");

    /** 枚举常量对应的中文界面显示名。 */
    private final String displayName;

    /**
     * 枚举构造方法。
     *
     * <p>枚举的构造方法不能通过 {@code new} 调用，只会由上方枚举常量自动调用。</p>
     *
     * @param displayName 中文显示名
     */
    CardType(String displayName) {
        this.displayName = displayName;
    }

    /**
     * 获取当前分类的中文显示名。
     *
     * @return 例如 "攻击"、"技能"
     */
    public String displayName() {
        return displayName;
    }
}
