package com.roguelike.dungeon.game.event;

import com.roguelike.dungeon.flow.LevelResult;
import com.roguelike.dungeon.game.card.CardInstance;
import com.roguelike.dungeon.game.card.CardLibrary;
import com.roguelike.dungeon.game.entity.Player;
import com.roguelike.dungeon.game.run.RunState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventServiceTest {

    @Test
    void sameSeedShouldOpenSameEvent() {
        EventService first = EventCatalog.openEvent(newRunState(50, 0), 12345L, result -> { });
        EventService second = EventCatalog.openEvent(newRunState(50, 0), 12345L, result -> { });

        assertEquals(first.getEvent(), second.getEvent());
        assertEquals(first.getChoices(), second.getChoices());
    }

    @Test
    void chestChoiceShouldGrantGoldCompleteOnceAndRejectSecondChoice() {
        RunState state = newRunState(50, 5);
        AtomicInteger finishCount = new AtomicInteger();
        AtomicReference<LevelResult> levelResult = new AtomicReference<>();
        EventService service = eventById(
                "abandoned_chest",
                state,
                result -> {
                    finishCount.incrementAndGet();
                    levelResult.set(result);
                });

        EventChoiceResult first = service.choose("open");
        EventChoiceResult second = service.choose("leave");

        assertEquals(EventActionStatus.SUCCESS, first.status());
        assertEquals(35, state.getGold());
        assertTrue(service.isResolved());
        assertEquals(LevelResult.COMPLETED, levelResult.get());
        assertEquals(1, finishCount.get());
        assertEquals(EventActionStatus.EVENT_ALREADY_RESOLVED, second.status());
        assertEquals(35, state.getGold());
    }

    @Test
    void springShouldHealWithoutExceedingMaximumHealth() {
        RunState state = newRunState(50, 0);
        state.getPlayer().setHealth(45);
        EventService service = eventById("quiet_spring", state, result -> { });

        EventChoiceResult result = service.choose("drink");

        assertTrue(result.succeeded());
        assertEquals(50, state.getPlayer().getHealth());
        assertTrue(result.message().contains("5"));
    }

    @Test
    void bloodSacrificeShouldRequireEnoughHealthAndNotMutateOnFailure() {
        RunState state = newRunState(3, 7);
        AtomicInteger finishCount = new AtomicInteger();
        EventService service = eventById(
                "blood_altar", state, result -> finishCount.incrementAndGet());
        EventChoice sacrifice = service.getChoices().stream()
                .filter(choice -> choice.id().equals("sacrifice"))
                .findFirst()
                .orElseThrow();

        EventChoiceResult result = service.choose("sacrifice");

        assertFalse(sacrifice.available());
        assertEquals(EventActionStatus.CHOICE_UNAVAILABLE, result.status());
        assertEquals(3, state.getPlayer().getHealth());
        assertEquals(7, state.getGold());
        assertEquals(0, finishCount.get());
        assertFalse(service.isResolved());
    }

    @Test
    void bloodSacrificeShouldSpendHealthAndGrantGold() {
        RunState state = newRunState(10, 2);
        AtomicReference<LevelResult> levelResult = new AtomicReference<>();
        EventService service = eventById(
                "blood_altar", state, levelResult::set);

        EventChoiceResult result = service.choose("sacrifice");

        assertEquals(EventActionStatus.SUCCESS, result.status());
        assertEquals(7, state.getPlayer().getHealth());
        assertEquals(52, state.getGold());
        assertEquals(LevelResult.COMPLETED, levelResult.get());
    }

    @Test
    void unknownChoiceShouldNotMutateOrCompleteEvent() {
        RunState state = newRunState(50, 9);
        AtomicInteger finishCount = new AtomicInteger();
        EventService service = EventCatalog.openEvent(
                state, 1L, result -> finishCount.incrementAndGet());

        EventChoiceResult result = service.choose("missing");

        assertEquals(EventActionStatus.CHOICE_NOT_FOUND, result.status());
        assertEquals(50, state.getPlayer().getHealth());
        assertEquals(9, state.getGold());
        assertEquals(0, finishCount.get());
        assertFalse(service.isResolved());
    }

    private static EventService eventById(
            String eventId,
            RunState state,
            com.roguelike.dungeon.flow.LevelFinishHandler finishHandler) {
        for (long seed = 0; seed < 100; seed++) {
            EventService service = EventCatalog.openEvent(state, seed, finishHandler);
            if (service.getEvent().id().equals(eventId)) {
                return service;
            }
        }
        throw new AssertionError("无法找到事件: " + eventId);
    }

    private static RunState newRunState(int maxHealth, int gold) {
        return new RunState(
                new Player(maxHealth, 3),
                List.of(new CardInstance("strike-1", CardLibrary.STRIKE)),
                gold,
                12345L,
                1);
    }
}
