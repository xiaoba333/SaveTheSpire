package com.roguelike.dungeon.game.blessing;

import java.util.Objects;

/** 开局房间展示给玩家的一个选项。 */
public record BlessingOption(
        String id,
        String label,
        String description,
        boolean requiresCard,
        boolean available,
        String unavailableReason) {

    public BlessingOption {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("祝福选项编号不能为空");
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("祝福选项名称不能为空");
        }
        description = Objects.requireNonNull(description, "祝福选项说明不能为 null");
        unavailableReason = Objects.requireNonNull(
                unavailableReason, "不可选原因不能为 null");
    }
}
