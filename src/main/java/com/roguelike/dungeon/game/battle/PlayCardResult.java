package com.roguelike.dungeon.game.battle;

/**
 * 出牌结果。从 {@code Combat} 内嵌枚举提升为独立类型，
 * 供 {@link CardPlayService} 使用且不反向依赖 {@link Combat}。
 *
 * <p>常量名与原 {@code Combat.PlayCardResult} 完全一致。</p>
 */
public enum PlayCardResult {
    SUCCESS,
    NOT_PLAYER_TURN,
    INVALID_CARD,
    NOT_ENOUGH_ENERGY,
    CARD_NOT_PLAYABLE,
    BATTLE_FINISHED
}
