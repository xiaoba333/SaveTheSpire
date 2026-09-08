package com.roguelike.dungeon.game;

/**
 * 敌人实体：拥有血量、护甲两种属性（无能量）。
 * 通过实现 {@link IHealth} / {@link IArmor} 两个接口来拼装能力。
 * 敌人由 AI 每回合自动行动，不消耗能量。
 */
public class Enemy implements IHealth, IArmor {

    private final int maxHealth;
    private int health;
    private int armor;

    /**
     * @param maxHealth 敌人最大血量
     */
    public Enemy(int maxHealth) {
        this.maxHealth = maxHealth;
        this.health = maxHealth;
        this.armor = 0;
    }

    // ---------- IHealth ----------

    @Override
    public int getHealth() {
        return health;
    }

    @Override
    public int getMaxHealth() {
        return maxHealth;
    }

    @Override
    public void setHealth(int health) {
        this.health = clamp(health, 0, maxHealth);
    }

    @Override
    public void heal(int amount) {
        if (amount <= 0) {
            return;
        }
        health = clamp(health + amount, 0, maxHealth);
    }

    @Override
    public int takeDamage(int damage) {
        if (damage <= 0) {
            return 0;
        }
        int before = health;
        health = clamp(health - damage, 0, maxHealth);
        return before - health;
    }

    @Override
    public boolean isDead() {
        return health <= 0;
    }

    // ---------- IArmor ----------

    @Override
    public int getArmor() {
        return armor;
    }

    @Override
    public void addArmor(int amount) {
        if (amount <= 0) {
            return;
        }
        armor += amount;
    }

    @Override
    public int absorb(int damage) {
        if (damage <= 0) {
            return 0;
        }
        int absorbed = Math.min(armor, damage);
        armor -= absorbed;
        return damage - absorbed;
    }

    @Override
    public void clearArmor() {
        armor = 0;
    }

    // ---------- 战斗协作 ----------

    /**
     * 统一受击入口：护甲先吸收，剩余伤害由血量承担。
     * @return 实际扣除的血量
     */
    public int receiveDamage(int damage) {
        int remaining = absorb(damage);
        return takeDamage(remaining);
    }

    /** 把 value 限制在 [min, max] 区间。 */
    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
