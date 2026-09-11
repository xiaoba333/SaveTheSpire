package com.roguelike.dungeon.game.entity;

import java.util.Objects;

/** 按编号创建遗物实例。 */
public final class RelicLibrary {
    public static final String BURNING_BLOOD = "burning_blood";
    public static final String BLOOD_PRICE = "blood_price";

    private RelicLibrary() {
    }

    public static Relic create(String relicId) {
        Objects.requireNonNull(relicId, "遗物编号不能为 null");
        return switch (relicId) {
            case BURNING_BLOOD -> new BattleEndHealRelic();
            case BLOOD_PRICE -> new FullHealMaxHpDownRelic();
            default -> throw new IllegalArgumentException("未知遗物编号：" + relicId);
        };
    }
}
