package com.roguelike.dungeon.game.event;

import java.util.Objects;

/** 事件选项的执行结果及供界面展示的结果说明。 */
public record EventChoiceResult(EventActionStatus status, String message) {

    public EventChoiceResult {
        status = Objects.requireNonNull(status, "事件操作状态不能为 null");
        message = Objects.requireNonNull(message, "事件结果说明不能为 null");
    }

    public boolean succeeded() {
        return status == EventActionStatus.SUCCESS;
    }
}
