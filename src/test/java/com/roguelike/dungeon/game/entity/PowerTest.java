package com.roguelike.dungeon.game.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PowerTest {

    @Test
    void gainPowerAndTriggerTurnStart() {
        Player player = new Player(50, 3);
        player.gainPower(new MetallicizePower(3));

        player.triggerTurnStart();

        assertEquals(3, player.getArmor());
        assertEquals(1, player.getPowers().size());
    }

    @Test
    void resetForBattleClearsPowers() {
        Player player = new Player(50, 3);
        player.gainPower(new MetallicizePower(3));

        player.resetForBattle();

        assertTrue(player.getPowers().isEmpty());
    }

    @Test
    void increaseMaxHealthRaisesCapWithoutChangingCurrentHp() {
        Player player = new Player(10, 3);

        player.increaseMaxHealth(2);

        assertEquals(12, player.getMaxHealth());
        assertEquals(10, player.getHealth());
    }
}
