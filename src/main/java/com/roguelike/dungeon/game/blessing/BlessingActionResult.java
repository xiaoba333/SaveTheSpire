package com.roguelike.dungeon.game.blessing;

import java.util.Objects;

/** 开局祝福的执行结果及供界面展示的说明。 */
public record BlessingActionResult(BlessingActionStatus status, String message) {

    public BlessingActionResult {
        status = Objects.requireNonNull(status, "祝福操作状态不能为 null");
        message = Objects.requireNonNull(message, "祝福结果说明不能为 null");
    }

    public boolean succeeded() {
        return status == BlessingActionStatus.SUCCESS;
    }

    public boolean needsCard() {
        return status == BlessingActionStatus.NEEDS_CARD;
    }
}
