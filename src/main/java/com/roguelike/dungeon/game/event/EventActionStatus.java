package com.roguelike.dungeon.game.event;

/** 提交事件选项后的状态。 */
public enum EventActionStatus {
    SUCCESS,
    CHOICE_NOT_FOUND,
    CHOICE_UNAVAILABLE,
    EVENT_ALREADY_RESOLVED
}
