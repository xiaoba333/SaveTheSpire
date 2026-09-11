package com.roguelike.dungeon.game.event;

import com.roguelike.dungeon.flow.LevelFinishHandler;
import com.roguelike.dungeon.flow.LevelResult;
import com.roguelike.dungeon.game.run.RunState;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.function.Function;
import java.util.function.Predicate;

/** 管理当前事件的选项查询、效果执行和关卡完成通知。 */
public final class EventService {
    private final RunState runState;
    private final EventDefinition definition;
    private final LevelFinishHandler finishHandler;

    private boolean resolved;

    EventService(
            RunState runState,
            EventDefinition definition,
            LevelFinishHandler finishHandler) {
        this.runState = Objects.requireNonNull(runState, "单局状态不能为 null");
        this.definition = Objects.requireNonNull(definition, "事件定义不能为 null");
        this.finishHandler = Objects.requireNonNull(
                finishHandler, "关卡结束处理器不能为 null");
    }

    public GameEvent getEvent() {
        return definition.event();
    }

    /** 返回根据当前玩家状态计算出的选项快照。 */
    public List<EventChoice> getChoices() {
        return definition.choices().stream()
                .map(choice -> choice.toView(runState))
                .toList();
    }

    public boolean isResolved() {
        return resolved;
    }

    /**
     * 执行一个选项。只有成功执行才会完成事件节点；失败不会修改本局状态。
     */
    public EventChoiceResult choose(String choiceId) {
        if (resolved) {
            return new EventChoiceResult(
                    EventActionStatus.EVENT_ALREADY_RESOLVED,
                    "当前事件已经结束。");
        }

        EventChoiceDefinition choice = definition.choices().stream()
                .filter(candidate -> candidate.id().equals(choiceId))
                .findFirst()
                .orElse(null);
        if (choice == null) {
            return new EventChoiceResult(
                    EventActionStatus.CHOICE_NOT_FOUND,
                    "事件选项不存在：" + choiceId);
        }
        if (!choice.availableWhen().test(runState)) {
            return new EventChoiceResult(
                    EventActionStatus.CHOICE_UNAVAILABLE,
                    choice.unavailableReason());
        }

        String message = choice.effect().apply(runState);
        resolved = true;
        LevelResult result = runState.getPlayer().isDead()
                ? LevelResult.DEFEATED
                : LevelResult.COMPLETED;
        finishHandler.onLevelFinished(result);
        return new EventChoiceResult(EventActionStatus.SUCCESS, message);
    }
}

/** 事件内部定义，不向 UI 暴露效果函数。 */
record EventDefinition(GameEvent event, List<EventChoiceDefinition> choices) {
    EventDefinition {
        event = Objects.requireNonNull(event, "事件信息不能为 null");
        choices = List.copyOf(Objects.requireNonNull(choices, "事件选项不能为 null"));
        if (choices.isEmpty()) {
            throw new IllegalArgumentException("事件至少需要一个选项");
        }
        choices.forEach(choice -> Objects.requireNonNull(
                choice, "事件选项不能包含 null"));
        Set<String> choiceIds = choices.stream()
                .map(EventChoiceDefinition::id)
                .collect(Collectors.toSet());
        if (choiceIds.size() != choices.size()) {
            throw new IllegalArgumentException("同一事件不能包含重复的选项编号");
        }
    }
}

/** 事件内部选项定义。 */
record EventChoiceDefinition(
        String id,
        String label,
        String description,
        Predicate<RunState> availableWhen,
        String unavailableReason,
        Function<RunState, String> effect) {

    EventChoiceDefinition {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("事件选项编号不能为空");
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("事件选项名称不能为空");
        }
        description = Objects.requireNonNull(description, "事件选项说明不能为 null");
        availableWhen = Objects.requireNonNull(availableWhen, "事件选项条件不能为 null");
        unavailableReason = Objects.requireNonNull(
                unavailableReason, "不可选原因不能为 null");
        effect = Objects.requireNonNull(effect, "事件选项效果不能为 null");
    }

    EventChoice toView(RunState runState) {
        boolean available = availableWhen.test(runState);
        return new EventChoice(
                id,
                label,
                description,
                available,
                available ? "" : unavailableReason);
    }
}
