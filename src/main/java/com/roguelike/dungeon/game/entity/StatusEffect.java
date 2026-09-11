package com.roguelike.dungeon.game.entity;

/**
 * 战斗状态效果（Buff / Debuff）枚举。
 * 状态以「层数」叠加，可作用于玩家或敌人任意战斗单位。
 */
public enum StatusEffect {

    /** 易伤：受到的伤害 +50%。 */
    VULNERABLE("易伤", "受到的伤害 +50%"),

    /** 虚弱：造成的伤害只有 75%。 */
    WEAK("虚弱", "造成的伤害只有 75%"),

    /** 中毒：回合结束受到等于层数的伤害，然后减少 1 层。 */
    POISON("中毒", "回合结束受到等于层数的伤害，然后减少 1 层"),

    /** 力量：造成的攻击伤害增加。 */
    STRENGTH("力量", "攻击伤害增加"),

    /** 血池：本回合免疫受到的伤害。 */
    BLOOD_POOL("血池", "本回合免疫受到的伤害"),

    /** 血畜：死亡时提高玩家最大生命值。 */
    BLOOD_HERD("血畜", "死亡时提高玩家最大生命值"),

    /** 死而复生：死亡时最大生命值减半并回满生命。 */
    REBORN("死而复生", "死亡时最大生命值减半并回满生命");

    private final String displayName;
    private final String description;

    StatusEffect(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    /** 中文名称，例如「易伤」。 */
    public String displayName() {
        return displayName;
    }

    /** 效果说明。 */
    public String description() {
        return description;
    }
}
