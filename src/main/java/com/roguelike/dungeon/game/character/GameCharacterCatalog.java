package com.roguelike.dungeon.game.character;

import com.roguelike.dungeon.game.card.CardLibrary;

import java.util.List;

/**
 * 当前版本的角色目录实现，提供「血祭者」角色。
 */
public final class GameCharacterCatalog implements CharacterCatalog {

    /**
     * 「血祭者」：初始血量 10，起始遗物「血之代价」（遗物属于后续扩展，见契约第 10 节）。
     * 起始牌组：4 攻击 + 4 防御 + 御血术 + 狂宴（共 10 张）。
     */
    public static final CharacterDefinition BLOOD_PRICE_CHARACTER = new CharacterDefinition(
            "blood",
            "血祭者",
            "以血为代价战斗的战士，初始血量极低。",
            10,
            3,
            0,
            List.of(
                    CardLibrary.STRIKE.id(),
                    CardLibrary.STRIKE.id(),
                    CardLibrary.STRIKE.id(),
                    CardLibrary.STRIKE.id(),
                    CardLibrary.DEFEND.id(),
                    CardLibrary.DEFEND.id(),
                    CardLibrary.DEFEND.id(),
                    CardLibrary.DEFEND.id(),
                    CardLibrary.SACRIFICE_STRIKE.id(),
                    CardLibrary.FEAST.id()));

    /**
     * 测试用隐藏角色：不出现在可选列表，选角时输入 {@code god} 解锁。
     * 起始牌组只有一张「降神」。
     */
    public static final CharacterDefinition GOD_CHARACTER = new CharacterDefinition(
            "god",
            "god",
            "测试角色，牌组只有一张降神。",
            50,
            3,
            0,
            List.of(CardLibrary.DESCEND.id()));

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
        if (GOD_CHARACTER.id().equals(characterId)) {
            return GOD_CHARACTER;
        }
        return CHARACTERS.stream()
                .filter(character -> character.id().equals(characterId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "未知角色编号：" + characterId));
    }
}
