package com.roguelike.dungeon.game.campfire;

/** 执行篝火操作后的状态。 */
public enum CampfireActionStatus {
    SUCCESS,
    ACTION_UNAVAILABLE,
    CARD_NOT_FOUND,
    CARD_NOT_UPGRADABLE,
    CAMPFIRE_ALREADY_USED
}
