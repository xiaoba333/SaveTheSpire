package com.roguelike.dungeon.game.character;

import com.roguelike.dungeon.game.card.CardLibrary;

import java.util.List;

/**
 * 当前版本的角色目录实现，提供「血祭者」角色。
 */
public final class GameCharacterCatalog implements CharacterCatalog {

    /**
     * 「血祭者」：初始血量 10，起始遗物「血之代价」（遗物属于后续扩展，见契约第 10 节）。
     * 起始牌组：4 攻击 + 4 防御 + 献身打击 + 狂宴（共 10 张）。
     */
    public static final CharacterDefinition BLOOD_PRICE_CHARACTER = new CharacterDefinition(
            "blood",
            "血祭者",
            "以血为代价战斗的战士，初始血量极低。",
            10,
            3,
            0,
            List.of(
                    CardLibrary.BLOOD_ATTACK.id(),
                    CardLibrary.BLOOD_ATTACK.id(),
                    CardLibrary.BLOOD_ATTACK.id(),
                    CardLibrary.BLOOD_ATTACK.id(),
                    CardLibrary.BLOOD_DEFEND.id(),
                    CardLibrary.BLOOD_DEFEND.id(),
                    CardLibrary.BLOOD_DEFEND.id(),
                    CardLibrary.BLOOD_DEFEND.id(),
                    CardLibrary.BLOOD_DEVOTION_STRIKE.id(),
                    CardLibrary.BLOOD_FEAST.id()));

    private static final List<CharacterDefinition> CHARACTERS =
            List.of(BLOOD_PRICE_CHARACTER);

    @Override
    public List<CharacterDefinition> getAvailableCharacters() {
        return CHARACTERS;
    }

    @Override
    public CharacterDefinition getById(String characterId) {
        if (characterId == null) {
            throw new IllegalArgumentException("角色编号不能为 null");
        }
        return CHARACTERS.stream()
                .filter(character -> character.id().equals(characterId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "未知角色编号：" + characterId));
    }
}
