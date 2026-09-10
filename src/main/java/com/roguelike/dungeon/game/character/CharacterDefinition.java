package com.roguelike.dungeon.game.character;

import com.roguelike.dungeon.game.card.Card;
import com.roguelike.dungeon.game.card.CardInstance;
import com.roguelike.dungeon.game.entity.Player;
import com.roguelike.dungeon.game.run.RunState;

import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * 可选角色的初始数据。界面只负责展示这些数据并把角色 id 传回流程层。
 */
public record CharacterDefinition(
        String id,
        String name,
        String description,
        int maxHealth,
        int maxEnergy,
        int startingGold,
        List<Card> startingDeck) {

    public CharacterDefinition {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("角色编号不能为空");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("角色名称不能为空");
        }
        description = Objects.requireNonNull(description, "角色说明不能为 null");
        if (maxHealth <= 0) {
            throw new IllegalArgumentException("角色最大生命必须大于 0");
        }
        if (maxEnergy <= 0) {
            throw new IllegalArgumentException("角色最大能量必须大于 0");
        }
        if (startingGold < 0) {
            throw new IllegalArgumentException("角色初始金币不能为负数");
        }
        startingDeck = List.copyOf(Objects.requireNonNull(
                startingDeck, "角色初始牌组不能为 null"));
        startingDeck.forEach(card -> Objects.requireNonNull(
                card, "角色初始牌组不能包含 null"));
    }

    /** 根据本角色数据创建一局完全独立的新游戏状态。 */
    public RunState createRunState(long runSeed, int totalActs) {
        List<CardInstance> deckInstances = IntStream.range(0, startingDeck.size())
                .mapToObj(index -> new CardInstance(
                        id + "-starter-" + (index + 1), startingDeck.get(index)))
                .toList();
        return new RunState(
                new Player(maxHealth, maxEnergy),
                deckInstances,
                startingGold,
                runSeed,
                totalActs);
    }
}
