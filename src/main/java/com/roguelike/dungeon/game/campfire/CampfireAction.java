package com.roguelike.dungeon.game.campfire;

import java.util.Objects;

/** 给界面展示的一个篝火操作。 */
public record CampfireAction(
        String id,
        String label,
        String description,
        boolean available,
        String unavailableReason) {

    public CampfireAction {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("篝火操作编号不能为空");
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("篝火操作名称不能为空");
        }
        description = Objects.requireNonNull(description, "篝火操作说明不能为 null");
        unavailableReason = Objects.requireNonNull(
                unavailableReason, "不可用原因不能为 null");
    }
}
