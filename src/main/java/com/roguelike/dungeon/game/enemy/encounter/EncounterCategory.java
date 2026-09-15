package com.roguelike.dungeon.game.enemy.encounter;

/**
 * 遭遇类别：一场战斗的「难度档位」。
 *
 * <p>刻意没有直接复用地图模块的 {@code MapNodeType}，原因是本模块要能脱离地图独立编译与测试。
 * 战斗模块在装配时做一次显式映射即可：</p>
 * <pre>
 * MapNodeType.BATTLE → EncounterCategory.NORMAL
 * MapNodeType.ELITE  → EncounterCategory.ELITE
 * MapNodeType.BOSS   → EncounterCategory.BOSS
 * </pre>
 */
public enum EncounterCategory {

    /** 普通怪：地牢外围的小怪组合。 */
    NORMAL("普通"),

    /** 精英怪：必掉遗物级别的高强度单体。 */
    ELITE("精英"),

    /** Boss：当前层的守关。 */
    BOSS("Boss");

    private final String chineseName;

    EncounterCategory(String chineseName) {
        this.chineseName = chineseName;
    }

    public String chineseName() {
        return chineseName;
    }
}
