package com.roguelike.dungeon.game.event;

import com.roguelike.dungeon.flow.LevelFinishHandler;
import com.roguelike.dungeon.game.run.RunState;

import java.util.List;
import java.util.Objects;
import java.util.Random;

/** 当前游戏可出现的事件及其规则。 */
public final class EventCatalog {
    private static final int CHEST_GOLD = 30;
    private static final int SPRING_HEAL = 10;
    private static final int ALTAR_HEALTH_COST = 3;
    private static final int ALTAR_GOLD = 50;

    private static final List<EventDefinition> EVENTS = List.of(
            abandonedChest(),
            quietSpring(),
            bloodAltar());

    private EventCatalog() {
    }

    /** 根据种子确定性地打开一个事件。 */
    public static EventService openEvent(
            RunState runState,
            long eventSeed,
            LevelFinishHandler finishHandler) {
        Objects.requireNonNull(runState, "单局状态不能为 null");
        Objects.requireNonNull(finishHandler, "关卡结束处理器不能为 null");
        EventDefinition definition = EVENTS.get(new Random(eventSeed).nextInt(EVENTS.size()));
        return new EventService(runState, definition, finishHandler);
    }

    private static EventDefinition abandonedChest() {
        return new EventDefinition(
                new GameEvent(
                        "abandoned_chest",
                        "废弃的宝箱",
                        "道路旁躺着一只布满灰尘的宝箱，锁已经损坏。"),
                List.of(
                        alwaysAvailableChoice(
                                "open",
                                "打开宝箱",
                                "获得 " + CHEST_GOLD + " 金币。",
                                state -> {
                                    state.addGold(CHEST_GOLD);
                                    return "你在宝箱中找到了 " + CHEST_GOLD + " 金币。";
                                }),
                        leaveChoice()));
    }

    private static EventDefinition quietSpring() {
        return new EventDefinition(
                new GameEvent(
                        "quiet_spring",
                        "宁静泉水",
                        "清澈的泉水散发着柔和的光芒。"),
                List.of(
                        alwaysAvailableChoice(
                                "drink",
                                "饮用泉水",
                                "恢复最多 " + SPRING_HEAL + " 点生命。",
                                state -> {
                                    int before = state.getPlayer().getHealth();
                                    state.getPlayer().heal(SPRING_HEAL);
                                    int healed = state.getPlayer().getHealth() - before;
                                    return "你恢复了 " + healed + " 点生命。";
                                }),
                        leaveChoice()));
    }

    private static EventDefinition bloodAltar() {
        return new EventDefinition(
                new GameEvent(
                        "blood_altar",
                        "血色祭坛",
                        "祭坛上的古老文字承诺以鲜血交换财富。"),
                List.of(
                        new EventChoiceDefinition(
                                "sacrifice",
                                "献上鲜血",
                                "失去 " + ALTAR_HEALTH_COST + " 点生命，获得 "
                                        + ALTAR_GOLD + " 金币。",
                                state -> state.getPlayer().getHealth() > ALTAR_HEALTH_COST,
                                "当前生命不足，献血后必须至少保留 1 点生命。",
                                state -> {
                                    state.getPlayer().takeDamage(ALTAR_HEALTH_COST);
                                    state.addGold(ALTAR_GOLD);
                                    return "你失去了 " + ALTAR_HEALTH_COST
                                            + " 点生命，并获得 " + ALTAR_GOLD + " 金币。";
                                }),
                        leaveChoice()));
    }

    private static EventChoiceDefinition alwaysAvailableChoice(
            String id,
            String label,
            String description,
            java.util.function.Function<RunState, String> effect) {
        return new EventChoiceDefinition(
                id, label, description, state -> true, "", effect);
    }

    private static EventChoiceDefinition leaveChoice() {
        return alwaysAvailableChoice(
                "leave",
                "离开",
                "什么也不做。",
                state -> "你谨慎地离开了这里。");
    }
}
