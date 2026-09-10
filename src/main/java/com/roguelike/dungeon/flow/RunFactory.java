package com.roguelike.dungeon.flow;

import com.roguelike.dungeon.game.card.CardInstance;
import com.roguelike.dungeon.game.card.CardLibrary;
import com.roguelike.dungeon.game.character.CharacterCatalog;
import com.roguelike.dungeon.game.character.CharacterDefinition;
import com.roguelike.dungeon.game.entity.Player;
import com.roguelike.dungeon.game.run.RunState;

import java.util.List;
import java.util.UUID;

/**
 * 开局工厂：把一个选中的角色变成一局游戏。
 *
 * <p>顺序固定：查角色 → 校验 → 按卡牌定义 ID 建实例 → 建 Player → 建 RunState。
 * 一局只有一个权威 {@link RunState}。</p>
 */
public final class RunFactory {

    private RunFactory() {
    }

    /**
     * 按角色创建一局新游戏。
     *
     * @param catalog 角色目录
     * @param characterId 选中的角色编号
     * @param runSeed 本局随机种子
     * @param totalActs 总章节数
     * @return 本局权威 RunState
     * @throws IllegalArgumentException 角色编号不存在
     */
    public static RunState createRun(
            CharacterCatalog catalog,
            String characterId,
            long runSeed,
            int totalActs) {
        CharacterDefinition character = catalog.getById(characterId);
        Player player = new Player(character.maxHealth(), character.maxEnergy());
        List<CardInstance> deck = character.startingCardIds().stream()
                .map(CardLibrary::byId)
                .map(card -> new CardInstance(UUID.randomUUID().toString(), card))
                .toList();
        return new RunState(
                player,
                deck,
                character.startingGold(),
                runSeed,
                totalActs);
    }
}
