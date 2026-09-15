package com.roguelike.dungeon.game.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PlayerTest {

    @Test
    void gainBonusEnergyAllowsExceedingMaxEnergy() {
        Player player = new Player(50, 3);   // 能量上限 3，回合开始已回满到 3

        player.gainBonusEnergy(2);

        assertEquals(5, player.getEnergy());
        assertTrue(player.getEnergy() > player.getMaxEnergy());
    }

    @Test
    void gainBonusEnergyIgnoresNonPositiveAmount() {
        Player player = new Player(50, 3);

        player.gainBonusEnergy(0);
        player.gainBonusEnergy(-2);

        assertEquals(3, player.getEnergy());
    }

    @Test
    void bonusEnergyCanBeSpentNormally() {
        Player player = new Player(50, 3);
        player.gainBonusEnergy(2);   // 3 + 2 = 5

        assertTrue(player.canAfford(4));
        assertTrue(player.consume(4));

        assertEquals(1, player.getEnergy());
    }

    @Test
    void refreshDiscardsBonusEnergy() {
        Player player = new Player(50, 3);
        player.gainBonusEnergy(2);   // 5，超出上限

        player.refresh();            // 回合开始，回到上限

        assertEquals(3, player.getEnergy());
    }

    @Test
    void addEnergyStillClampsToMaxEnergy() {
        Player player = new Player(50, 3);
        player.consume(2);           // 剩 1

        player.addEnergy(5);         // 普通加能量仍被夹到上限

        assertEquals(3, player.getEnergy());
        assertFalse(player.getEnergy() > player.getMaxEnergy());
    }
}
