package com.roguelike.dungeon.game.entity;

/**
 * 遗物可见的战斗状态视图。
 *
 * <p>由 {@code BattleState} 实现，遗物通过 {@link RelicContext#battle()} 拿到它。
 * 之所以抽成接口，是为了让 {@code game.entity} 不依赖 {@code game.battle}，
 * 保持「实体层不反向依赖战斗层」的分层。</p>
 *
 * <p>战斗外的触发点（{@link RelicTrigger#OBTAIN}）下 {@code battle()} 可能为 null，
 * 遗物实现必须自行判空。</p>
 */
public interface BattleInfo {

    /** 怪物当前生命。 */
    int monsterHp();

    /** 怪物生命上限。 */
    int monsterMaxHp();

    /** 怪物当前护甲。 */
    int monsterBlock();

    /** 怪物下一次行动是否为攻击。 */
    boolean monsterWillAttack();

    /** 当前回合数，从 1 开始。 */
    int turnNumber();

    /** 本回合已打出的牌数。 */
    int cardsPlayedThisTurn();

    /** 本场战斗累计打出的牌数。 */
    int cardsPlayedThisBattle();

    /** 本场战斗累计打出的攻击牌数。 */
    int attacksPlayedThisBattle();

    /** 本场战斗累计打出的技能牌数。 */
    int skillsPlayedThisBattle();

    /** 怪物身上某种状态的层数。 */
    int monsterStacks(StatusEffect effect);

    /** 给怪物叠加状态层数。 */
    void addMonsterStacks(StatusEffect effect, int amount);

    /**
     * 直接扣除怪物生命，<b>无视护甲</b>，用于「手里剑」这类穿透伤害。
     *
     * <p>该方法不会再触发 {@link RelicTrigger#DAMAGE_DEALT}，
     * 避免遗物互相调用造成无限递归。</p>
     *
     * @return 实际扣除的生命
     */
    int dealDirectDamageToMonster(int amount);
}
