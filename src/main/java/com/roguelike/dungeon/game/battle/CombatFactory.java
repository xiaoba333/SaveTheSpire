package com.roguelike.dungeon.game.battle;

import com.roguelike.dungeon.flow.LevelFinishHandler;
import com.roguelike.dungeon.game.card.CardInstance;
import com.roguelike.dungeon.game.card.CardLibrary;
import com.roguelike.dungeon.game.entity.Player;
import com.roguelike.dungeon.game.map.MapNodeType;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 战斗装配工厂。
 *
 * <p>兼容旧 Demo / 单测的 {@code new Combat(logger)}，
 * 并为地图节点（含 Boss）选择对应的 {@link MonsterAiService}。</p>
 */
public final class CombatFactory {

    private CombatFactory() {
    }

    /** 旧 Demo、HTTP 独立战斗、单元测试用的默认战斗。 */
    public static Combat createDemo(Consumer<String> logger) {
        return new Combat(logger);
    }

    /**
     * HTTP 独立战斗使用的演示战斗。
     *
     * <p>与普通 Demo 不同，这里在起始牌组中加入锻造牌，方便 Unity 前端直接
     * 联调“选择锻造牌 -> 选择目标手牌 -> 发送 targetCardId”的完整交互。</p>
     */
    public static Combat createHttpDemo(Consumer<String> logger) {
        return new Combat(
                new Player(Combat.PLAYER_MAX_HP, Combat.PLAYER_MAX_ENERGY),
                forgeDemoDeck(),
                logger,
                result -> { });
    }

    /** 与本局 RunState 共享玩家和牌组的战斗。 */
    public static Combat create(
            Player player,
            List<CardInstance> battleDeck,
            Consumer<String> logger,
            LevelFinishHandler finishHandler) {
        return new Combat(player, battleDeck, logger, finishHandler);
    }

    /** 共享玩家、牌组，并同步永久牌组升级。 */
    public static Combat create(
            Player player,
            List<CardInstance> battleDeck,
            Consumer<String> logger,
            LevelFinishHandler finishHandler,
            Consumer<CardInstance> cardUpgradeHandler) {
        return new Combat(
                player, battleDeck, logger, finishHandler, cardUpgradeHandler);
    }

    /**
     * 按地图节点类型装配战斗。Boss 走 {@link MonsterAiService#boss()}，
     * 当前数值与普通怪相同，战斗结果不变。
     */
    public static Combat createForNode(
            MapNodeType nodeType,
            Player player,
            List<CardInstance> battleDeck,
            Consumer<String> logger,
            LevelFinishHandler finishHandler,
            Consumer<CardInstance> cardUpgradeHandler) {
        MonsterAiService monsterAi = nodeType == MapNodeType.BOSS
                ? MonsterAiService.boss()
                : MonsterAiService.regular();
        return new Combat(
                player,
                battleDeck,
                logger,
                finishHandler,
                cardUpgradeHandler,
                monsterAi);
    }

    /** 起始牌组：5 打击 + 5 防御，每张独立实例 id。 */
    public static List<CardInstance> defaultDeck() {
        return CardLibrary.startingDeck().stream()
                .map(card -> new CardInstance(UUID.randomUUID().toString(), card))
                .toList();
    }

    private static List<CardInstance> forgeDemoDeck() {
        return List.of(
                new CardInstance(UUID.randomUUID().toString(), CardLibrary.FORGE),
                new CardInstance(UUID.randomUUID().toString(), CardLibrary.STRIKE),
                new CardInstance(UUID.randomUUID().toString(), CardLibrary.DEFEND));
    }
}
