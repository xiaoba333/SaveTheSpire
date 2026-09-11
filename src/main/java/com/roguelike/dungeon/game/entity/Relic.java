package com.roguelike.dungeon.game.entity;

/**
 * 遗物接口：本局内永久生效的被动道具。
 * 具体遗物实现本接口，并按需覆写不同触发点（默认空实现）。
 * 遗物持有在 {@link Player} 上，随整局游戏一直生效。
 */
public interface Relic {

    /** 遗物中文名。 */
    String name();

    /** 遗物效果描述。 */
    String description();

    /**
     * 进入新关卡时触发。默认空实现，具体遗物按需覆写。
     *
     * @param player 本局唯一的玩家实体，遗物直接在其上修改状态
     */
    default void onEnterLevel(Player player) {
    }

    /**
     * 战斗胜利结束时触发。默认空实现，具体遗物按需覆写。
     *
     * @param player 本局唯一的玩家实体，遗物直接在其上修改状态
     */
    default void onBattleEnd(Player player) {
    }
}
