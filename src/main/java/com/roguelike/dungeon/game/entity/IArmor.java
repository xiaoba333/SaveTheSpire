package com.roguelike.dungeon.game.entity;

/**
 * 护甲接口：管理护甲的叠甲、伤害吸收与清空。
 * 护甲是「先于血量」结算的临时防护层，回合开始时清空。
 * 玩家与敌人都实现该接口。
 */
public interface IArmor {

    /** 当前护甲值，范围 [0, +∞)。 */
    int getArmor();

    /** 叠甲：增加 amount 点护甲；amount <= 0 时忽略。 */
    void addArmor(int amount);

    /**
     * 护甲优先吸收伤害。
     * 护甲同步减少被吸收的部分，返回未被吸收的剩余伤害。
     * @return 剩余伤害（>= 0），需要继续由血量结算
     */
    int absorb(int damage);

    /** 清空护甲为 0（每回合开始时调用）。 */
    void clearArmor();
}
