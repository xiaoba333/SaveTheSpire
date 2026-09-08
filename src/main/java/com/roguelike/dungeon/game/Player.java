package com.roguelike.dungeon.game;

/**
 * 玩家实体：拥有血量、护甲、能量三种属性。
 * 通过实现 {@link IHealth} / {@link IArmor} / {@link IEnergy} 三个接口来拼装能力。
 */
public class Player implements IHealth, IArmor, IEnergy {

    private final int maxHealth;
    private int health;
    private int armor;

    private final int maxEnergy;
    private int energy;

    /**
     * @param maxHealth 玩家最大血量
     * @param maxEnergy 玩家每回合能量上限
     */
    public Player(int maxHealth, int maxEnergy) {
        this.maxHealth = maxHealth;
        this.health = maxHealth;
        this.armor = 0;
        this.maxEnergy = maxEnergy;
        this.energy = maxEnergy;
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

    // ---------- IEnergy ----------

    @Override
    public int getEnergy() {
        return energy;
    }

    @Override
    public int getMaxEnergy() {
        return maxEnergy;
    }

    @Override
    public void setEnergy(int energy) {
        this.energy = clamp(energy, 0, maxEnergy);
    }

    @Override
    public void addEnergy(int amount) {
        if (amount <= 0) {
            return;
        }
        energy = clamp(energy + amount, 0, maxEnergy);
    }

    @Override
    public boolean consume(int cost) {
        if (cost <= 0) {
            return true;
        }
        if (energy < cost) {
            return false;
        }
        energy -= cost;
        return true;
    }

    @Override
    public boolean canAfford(int cost) {
        return cost <= 0 || energy >= cost;
    }

    @Override
    public void refresh() {
        energy = maxEnergy;
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
