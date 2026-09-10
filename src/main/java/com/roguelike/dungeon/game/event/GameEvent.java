package com.roguelike.dungeon.game.event;

import java.util.Objects;

/** 给界面展示的事件基础信息。 */
public record GameEvent(String id, String title, String description) {

    public GameEvent {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("事件编号不能为空");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("事件标题不能为空");
        }
        description = Objects.requireNonNull(description, "事件说明不能为 null");
    }
}
