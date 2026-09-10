package com.roguelike.dungeon.game.campfire;

import java.util.Objects;

/** 篝火操作的执行结果及供界面展示的说明。 */
public record CampfireActionResult(CampfireActionStatus status, String message) {

    public CampfireActionResult {
        status = Objects.requireNonNull(status, "篝火操作状态不能为 null");
        message = Objects.requireNonNull(message, "篝火结果说明不能为 null");
    }

    public boolean succeeded() {
        return status == CampfireActionStatus.SUCCESS;
    }
}
