package com.roguelike.dungeon.game.event;

import java.util.Objects;

/** 给界面展示的一个事件选项。 */
public record EventChoice(
        String id,
        String label,
        String description,
        boolean available,
        String unavailableReason) {

    public EventChoice {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("事件选项编号不能为空");
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("事件选项名称不能为空");
        }
        description = Objects.requireNonNull(description, "事件选项说明不能为 null");
        unavailableReason = Objects.requireNonNull(
                unavailableReason, "不可选原因不能为 null");
    }
}
