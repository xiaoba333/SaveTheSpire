package com.roguelike.dungeon.game.battle;

/**
 * 怪物 AI：决定怪物每回合的意图与行动。
 *
 * <p>{@link Combat} 在玩家结束回合时把「怪物回合」委托给这里，
 * 不再把攻击 / 叠甲逻辑写死在 Combat 里。每种怪物对应一个实现，
 * 用不同的攻击 / 叠甲 / 成长节奏区分。</p>
 *
 * <p>说明：目前怪物的血量 / 护甲仍由 {@link Combat} 以 int 字段持有，
 * AI 只负责「意图」和「行动」。后续接入 Enemy 实体后，AI 应改为
 * 面向 BattleState 操作，而不是持有 Combat。</p>
 */
public interface MonsterAi {

    /** 怪物中文名。 */
    String name();

    /** 怪物最大生命值。 */
    int maxHp();

    /** 当前声明的下回合意图（用于界面 / HTTP 展示）。 */
    Combat.Intent nextIntent();

    /** 战斗开始时重置 AI 内部状态。 */
    void startFight();

    /**
     * 执行本回合怪物行动（攻击玩家 / 给自己叠甲 / 成长等）。
     *
     * @param combat     当前战斗，AI 通过它的 package-private 方法施加效果
     * @param turnNumber 当前回合数（从 1 开始）
     */
    void takeTurn(Combat combat, int turnNumber);
}
