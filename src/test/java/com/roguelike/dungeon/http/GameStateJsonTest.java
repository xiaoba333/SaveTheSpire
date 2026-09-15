package com.roguelike.dungeon.http;

import com.roguelike.dungeon.game.event.EventActionStatus;
import com.roguelike.dungeon.game.event.EventChoice;
import com.roguelike.dungeon.game.event.EventChoiceResult;
import com.roguelike.dungeon.game.event.GameEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameStateJsonTest {

    @Test
    void eventJsonShouldSerializeUnavailableReasonAndDisabled() {
        GameEvent event = new GameEvent("blood_altar", "血色祭坛", "以鲜血交换财富。");
        List<EventChoice> choices = List.of(
                new EventChoice("sacrifice", "献上鲜血", "失去3点生命获得50金币。", false,
                        "当前生命不足，献血后必须至少保留 1 点生命。"),
                new EventChoice("leave", "离开", "什么也不做。", true, ""));

        String json = GameStateJson.eventJson(event, choices);

        assertTrue(json.contains("\"id\":\"blood_altar\""));
        assertTrue(json.contains("\"disabled\":true"));
        assertTrue(json.contains(
                "\"unavailableReason\":\"当前生命不足，献血后必须至少保留 1 点生命。\""));
        assertTrue(json.contains("\"disabled\":false"));
        assertTrue(json.contains("\"unavailableReason\":\"\""));
    }

    @Test
    void eventChoiceResultJsonShouldContainSuccessStatusAndMessage() {
        String json = GameStateJson.eventChoiceResultJson(
                new EventChoiceResult(EventActionStatus.SUCCESS, "你在宝箱中找到了 30 金币。"));

        assertEquals(
                "{\"success\":true,\"status\":\"SUCCESS\",\"message\":\"你在宝箱中找到了 30 金币。\"}",
                json);
    }
}
