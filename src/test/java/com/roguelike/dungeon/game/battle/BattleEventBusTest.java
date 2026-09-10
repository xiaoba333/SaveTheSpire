package com.roguelike.dungeon.game.battle;

import com.roguelike.dungeon.flow.LevelResult;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleEventBusTest {

    @Test
    void publishFinishedShouldNotifySubscribersOnce() {
        BattleEventBus bus = new BattleEventBus();
        AtomicReference<LevelResult> result = new AtomicReference<>();
        AtomicInteger count = new AtomicInteger();
        bus.subscribeFinished(levelResult -> {
            result.set(levelResult);
            count.incrementAndGet();
        });

        bus.publishFinished(LevelResult.COMPLETED);
        bus.publishFinished(LevelResult.DEFEATED);

        assertEquals(LevelResult.COMPLETED, result.get());
        assertEquals(1, count.get());
        assertTrue(bus.isFinishPublished());
    }
}
