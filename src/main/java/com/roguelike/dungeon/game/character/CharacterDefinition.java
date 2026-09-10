package com.roguelike.dungeon.game.character;

import java.util.List;
import java.util.Objects;

/**
 * 一个可选角色的不可变数据定义（《主菜单与角色选择接口约定》v1.0 第 4 节）。
 *
 * <p>只承载角色数据，不负责创建 {@code Player}、卡牌实例或 {@code RunState}——
 * 那是菜单 / 选角流程（Mg）的职责。角色负责人（王佳一）负责提供本数据。</p>
 */
public record CharacterDefinition(
        String id,
        String name,
        String description,
        int maxHealth,
        int maxEnergy,
        int startingGold,
        List<String> startingCardIds) {

    public CharacterDefinition {
        Objects.requireNonNull(id, "角色编号不能为 null");
        if (id.isBlank()) {
            throw new IllegalArgumentException("角色编号不能为空");
        }
        Objects.requireNonNull(name, "角色名不能为 null");
        Objects.requireNonNull(description, "角色简介不能为 null");
        if (maxHealth <= 0) {
            throw new IllegalArgumentException("初始血量必须大于 0");
        }
        if (maxEnergy <= 0) {
            throw new IllegalArgumentException("初始能量必须大于 0");
        }
        if (startingGold < 0) {
            throw new IllegalArgumentException("初始金币不能为负数");
        }
        startingCardIds = List.copyOf(Objects.requireNonNull(
                startingCardIds, "起始牌组不能为 null"));
        if (startingCardIds.isEmpty()) {
            throw new IllegalArgumentException("起始牌组不能为空");
        }
        for (String cardId : startingCardIds) {
            if (cardId == null || cardId.isBlank()) {
                throw new IllegalArgumentException("起始牌组不能包含空的卡牌 ID");
            }
        }
    }
}
