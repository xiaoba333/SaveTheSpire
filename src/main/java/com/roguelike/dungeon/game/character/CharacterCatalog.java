package com.roguelike.dungeon.game.character;

import com.roguelike.dungeon.game.card.CardLibrary;

import java.util.List;
import java.util.Optional;

/** 当前可供选择的角色目录。 */
public final class CharacterCatalog {
    /** 战士的正式数值尚未确定，暂时沿用原 MVP 的玩家数据。 */
    public static final CharacterDefinition WARRIOR = new CharacterDefinition(
            "warrior",
            "战士（占位）",
            "基础角色，占位数据将在角色模块定稿后替换。",
            50,
            3,
            0,
            CardLibrary.startingDeck());

    private static final List<CharacterDefinition> CHARACTERS = List.of(WARRIOR);

    private CharacterCatalog() {
    }

    public static List<CharacterDefinition> all() {
        return CHARACTERS;
    }

    public static Optional<CharacterDefinition> findById(String characterId) {
        if (characterId == null || characterId.isBlank()) {
            return Optional.empty();
        }
        return CHARACTERS.stream()
                .filter(character -> character.id().equals(characterId))
                .findFirst();
    }
}
