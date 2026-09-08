package com.roguelike.dungeon.game.entity;

/**
 * 能量接口：管理每回合出牌消耗的能量。
 * 仅玩家实现该接口；敌人由 AI 自动行动，不消耗能量。
 */
public interface IEnergy {

    /** 当前剩余能量，范围 [0, maxEnergy]。 */
    int getEnergy();

    /** 每回合能量上限。 */
    int getMaxEnergy();

    /** 直接设置能量，内部夹在 [0, maxEnergy] 区间。 */
    void setEnergy(int energy);

    /** 增加 amount 点能量，不超过 maxEnergy；amount <= 0 时忽略。 */
    void addEnergy(int amount);

    /**
     * 尝试消耗 cost 点能量。
     * @return true 表示消耗成功；false 表示能量不足（不扣任何能量）
     */
    boolean consume(int cost);

    /** 是否足以支付 cost 点能量（只判断、不扣除）。 */
    boolean canAfford(int cost);

    /** 刷新能量到满值（每回合开始时调用）。 */
    void refresh();
}
