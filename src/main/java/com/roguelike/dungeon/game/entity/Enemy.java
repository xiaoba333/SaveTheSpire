package com.roguelike.dungeon.game.entity;

import java.util.EnumMap;
import java.util.Map;

/**
 * 敌人实体：拥有血量、护甲两种属性（无能量），并支持状态效果（易伤 / 虚弱 / 中毒）。
 * 通过实现 {@link IHealth} / {@link IArmor} / {@link IStatus} 三个接口拼装能力。
 * 敌人由 AI 每回合自动行动，不消耗能量。
 */
public class Enemy implements IHealth, IArmor, IStatus {

    private final int maxHealth;
    private int health;
    private int armor;

    private final Map<StatusEffect, Integer> statuses = new EnumMap<>(StatusEffect.class);

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

    // ---------- 战斗协作 ----------

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
