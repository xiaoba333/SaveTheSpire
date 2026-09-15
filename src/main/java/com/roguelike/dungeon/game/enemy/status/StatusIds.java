package com.roguelike.dungeon.game.enemy.status;

/**
 * 状态 ID 常量表。
 *
 * <p>全项目统一用这里的字符串常量引用状态，禁止在各处硬编码中文字符串。
 * 新增状态时同时要：① 在这里加常量；② 在 {@link StatusRegistry} 里注册。</p>
 */
public final class StatusIds {

    private StatusIds() {
    }

    // ==================== 第一层怪物专属状态（设计案） ====================

    /** 蠕动：不会随回合数减少，首次受到攻击时伤害减半，受击后消失。 */
    public static final String CREEPING = "creeping";

    /** 骨质疏松：受到伤害时额外受到 2 点伤害，且每次行动都会受到 2 点伤害。 */
    public static final String OSTEOPOROSIS = "osteoporosis";

    /** 无灵：无法被易伤、虚弱、中毒。 */
    public static final String SOULLESS = "soulless";

    /** 复苏：所有行动判定两次。 */
    public static final String REVIVAL = "revival";

    /** 蜕变：当蛋孵化时，每有一层蜕变，凯洛斯增加一点力量。 */
    public static final String METAMORPHOSIS = "metamorphosis";

    // ==================== 施加给玩家的通用状态 ====================

    /** 易伤：受到伤害提高（具体倍率由玩家状态模块结算）。 */
    public static final String VULNERABLE = "vulnerable";

    /** 虚弱：造成的伤害降低（具体倍率由玩家状态模块结算）。 */
    public static final String WEAK = "weak";

    /** 中毒：回合结束按层数掉血（具体结算由玩家状态模块实现）。 */
    public static final String POISON = "poison";
}
