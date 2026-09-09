package com.roguelike.dungeon.flow;

/** 一局游戏当前所处的主流程阶段。 */
public enum GamePhase {
    MAP,
    BATTLE,
    REWARD,
    EVENT,
    SHOP,
    REST,
    VICTORY,
    DEFEAT
}
