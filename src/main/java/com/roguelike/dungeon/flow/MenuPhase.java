package com.roguelike.dungeon.flow;

/**
 * 一局游戏开始前的菜单阶段。
 *
 * <p>与 {@link GamePhase}（地图 / 战斗 / 奖励等局内阶段）区分开：
 * 选角流程运行在菜单阶段，正式开局后才进入局内阶段。</p>
 */
public enum MenuPhase {

    /** 主菜单：尚未进入选角。 */
    MAIN_MENU,

    /** 正在选择角色。 */
    CHARACTER_SELECT,

    /** 已开局，进入游戏（由 {@link GameController} 接管）。 */
    IN_RUN,

    /** 已退出游戏。 */
    EXITED
}
