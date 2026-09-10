package com.roguelike.dungeon.game.entity;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 玩家实体：拥有血量、护甲、能量三种属性，并支持状态效果（易伤 / 虚弱 / 中毒）。
 * 通过实现 {@link IHealth} / {@link IArmor} / {@link IEnergy} / {@link IStatus} 四个接口拼装能力。
 */
public class Player implements IHealth, IArmor, IEnergy, IStatus {

    private int maxHealth;
    private int health;
    private int armor;

    private final int maxEnergy;
    private int energy;

    private final Map<StatusEffect, Integer> statuses = new EnumMap<>(StatusEffect.class);

    private final List<Relic> relics = new ArrayList<>();

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

    // ---------- IStatus ----------

    @Override
    public int getStacks(StatusEffect effect) {
        return statuses.getOrDefault(effect, 0);
    }

    @Override
    public void addStacks(StatusEffect effect, int amount) {
        if (effect == null) {
            return;
        }
        int next = Math.max(0, getStacks(effect) + amount);
        if (next == 0) {
            statuses.remove(effect);
        } else {
            statuses.put(effect, next);
        }
    }

    @Override
    public void removeStatus(StatusEffect effect) {
        statuses.remove(effect);
    }

    @Override
    public void clearStatuses() {
        statuses.clear();
    }

    @Override
    public boolean hasStatus(StatusEffect effect) {
        return getStacks(effect) > 0;
    }

    @Override
    public void tickEndOfTurn() {
        int poison = getStacks(StatusEffect.POISON);
        if (poison > 0) {
            takeDamage(poison);   // 中毒直接扣血（无视护甲）
            addStacks(StatusEffect.POISON, -1);
        }
        addStacks(StatusEffect.VULNERABLE, -1);
        addStacks(StatusEffect.WEAK, -1);
    }

    // ---------- 遗物 ----------

    /** 获得一个遗物。 */
    public void addRelic(Relic relic) {
        if (relic != null) {
            relics.add(relic);
        }
    }

    /** 是否持有某遗物（按实例比较）。 */
    public boolean hasRelic(Relic relic) {
        return relics.contains(relic);
    }

    /** 当前持有的全部遗物。 */
    public List<Relic> getRelics() {
        return List.copyOf(relics);
    }

    /**
     * 进入新关卡时结算所有遗物效果（由 GameController 调用）。
     */
    public void onEnterLevel() {
        for (Relic relic : relics) {
            relic.onEnterLevel(this);
        }
    }

    /** 降低最大生命值（下限 1 点），并把当前生命夹到新上限内。 */
    public void reduceMaxHealth(int amount) {
        if (amount <= 0) {
            return;
        }
        maxHealth = Math.max(1, maxHealth - amount);
        health = Math.min(health, maxHealth);
    }

    /** 提高最大生命值；当前生命不随之恢复。 */
    public void increaseMaxHealth(int amount) {
        if (amount <= 0) {
            return;
        }
        maxHealth += amount;
    }

    /** 回满生命到当前最大生命值。 */
    public void healToFull() {
        health = maxHealth;
    }

    // ---------- 战斗协作 ----------

    /**
     * 开始新一场战斗：保留当前生命值，重置能量、护甲和状态。
     * 由 Combat 在每场战斗开始时调用（玩家跨战斗复用，血量不重置）。
     */
    public void resetForBattle() {
        refresh();          // 能量回满
        clearArmor();       // 护甲清零
        clearStatuses();    // 清空本场 Buff/Debuff
    }

    /**
     * 统一受击入口：先结算易伤，再护甲吸收，剩余伤害由血量承担。
     * @return 实际扣除的血量
     */
    public int receiveDamage(int damage) {
        if (damage <= 0) {
            return 0;
        }
        if (hasStatus(StatusEffect.VULNERABLE)) {
            damage = damage * 3 / 2;  // 易伤：受到的伤害 +50%
        }
        int remaining = absorb(damage);
        return takeDamage(remaining);
    }

    /**
     * 计算本次实际造成的伤害（应用虚弱：只造成 75%）。
     * 攻击方调用此方法后，再把结果交给目标的 {@link #receiveDamage(int)}。
     */
    public int calcDealtDamage(int baseDamage) {
        if (baseDamage <= 0) {
            return 0;
        }
        if (hasStatus(StatusEffect.WEAK)) {
            return baseDamage * 3 / 4;  // 虚弱：造成的伤害只有 75%
        }
        return baseDamage;
    }

    /** 把 value 限制在 [min, max] 区间。 */
    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
