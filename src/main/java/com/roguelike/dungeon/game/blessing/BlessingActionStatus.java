package com.roguelike.dungeon.game.blessing;

/** 执行开局祝福后的状态。 */
public enum BlessingActionStatus {
    SUCCESS,
    NEEDS_CARD,
    CHOICE_NOT_FOUND,
    CHOICE_UNAVAILABLE,
    CARD_NOT_FOUND,
    CARD_NOT_UPGRADABLE,
    NOT_WAITING_FOR_CARD,
    ALREADY_RESOLVED
}
