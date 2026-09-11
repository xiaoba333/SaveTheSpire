package com.roguelike.dungeon.game.entity;

/**
 * 遗物稀有度，决定掉落权重与获取来源。
 *
 * <p>普通遗物常规战斗就可能掉落；罕见、稀有来自精英战与商店；
 * Boss 遗物一局最多获得一个，通常带有明确的代价。</p>
 */
public enum RelicRarity {

    COMMON("普通"),
    UNCOMMON("罕见"),
    RARE("稀有"),
    BOSS("Boss");

    private final String displayName;

    RelicRarity(String displayName) {
        this.displayName = displayName;
    }

    /** 中文名称，例如「普通」。 */
    public String displayName() {
        return displayName;
    }
}
