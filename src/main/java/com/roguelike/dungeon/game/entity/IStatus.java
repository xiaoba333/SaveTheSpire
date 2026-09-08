package com.roguelike.dungeon.game.entity;

/**
 * 状态效果接口：管理单位身上的状态层数。
 * 玩家与敌人都实现该接口，状态可作用于任意战斗单位。
 */
public interface IStatus {

    /** 获取某状态当前层数（没有则为 0）。 */
    int getStacks(StatusEffect effect);

    /**
     * 增加层数：amount 可正可负，结果夹在 [0, +∞)。
     * 层数降到 0 时该状态自动移除。
     */
    void addStacks(StatusEffect effect, int amount);

    /** 移除某状态的全部层数。 */
    void removeStatus(StatusEffect effect);

    /** 清空所有状态。 */
    void clearStatuses();

    /** 是否带有某状态（层数 > 0）。 */
    boolean hasStatus(StatusEffect effect);

    /** 回合结束结算：中毒造成伤害并减 1 层，易伤 / 虚弱各减 1 层。 */
    void tickEndOfTurn();
}
