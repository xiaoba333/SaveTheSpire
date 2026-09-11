package com.roguelike.dungeon.game.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BattleEndHealRelicTest {

    @Test
    void battleEndHealsSixWithoutExceedingMaxHealth() {
        Player player = new Player(50, 3);
        player.setHealth(20);
        player.addRelic(new BattleEndHealRelic());

        player.onBattleEnd();

        assertEquals(26, player.getHealth());
    }

    @Test
    void battleEndHealDoesNotExceedMaxHealth() {
        Player player = new Player(50, 3);
        player.setHealth(48);
        player.addRelic(new BattleEndHealRelic());

        player.onBattleEnd();

        assertEquals(50, player.getHealth());
    }
}
