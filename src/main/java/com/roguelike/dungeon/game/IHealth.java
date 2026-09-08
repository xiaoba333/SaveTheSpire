package com.roguelike.dungeon.game;

/**
 * 血量接口：管理生命值的增减与死亡判定。
 * 玩家与敌人都实现该接口。
 */
public interface IHealth {

    /** 当前血量。范围 [0, maxHealth]。 */
    int getHealth();

    /** 最大血量（血量上限，受 Buff 等影响可变动）。 */
    int getMaxHealth();

    /** 直接设置血量，内部自动夹在 [0, maxHealth] 区间。 */
    void setHealth(int health);

    /** 恢复 amount 点血量，不超过 maxHealth；amount <= 0 时忽略。 */
    void heal(int amount);

    /**
     * 扣除 damage 点血量（纯扣血，不含护甲结算）。
     * @return 实际扣除的血量（0 <= 返回值 <= damage）
     */
    int takeDamage(int damage);

    /** 是否死亡，即当前血量 <= 0。 */
    boolean isDead();
}
