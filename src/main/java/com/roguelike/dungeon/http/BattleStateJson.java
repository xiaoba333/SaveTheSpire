package com.roguelike.dungeon.http;

import com.roguelike.dungeon.game.battle.Combat;
import com.roguelike.dungeon.game.card.Card;
import com.roguelike.dungeon.game.card.CardInstance;

import java.util.List;

/**
 * 把 {@link Combat} 当前状态映射为 http-api.md 定义的 BattleState JSON。
 *
 * <p>字段命名 camelCase，与前端 {@code SaveTheSpire.Models} 的字段一一对应。
 * 后端权威：这里只做「读 Combat 的当前快照 → 序列化」，不改变任何战斗状态。</p>
 */
public final class BattleStateJson {

    /** 后端 {@link Card} 目前没有稀有度字段，起始牌统一按 BASIC 处理（占位，后续接入真值）。 */
    private static final String DEFAULT_RARITY = "BASIC";

    private BattleStateJson() {
    }

    public static String toJson(String battleId, Combat combat, List<String> newLogs) {
        StringBuilder sb = new StringBuilder(512);
        sb.append('{');
        sb.append("\"battleId\":").append(Json.str(battleId)).append(',');
        sb.append("\"turnNumber\":").append(combat.getTurnNumber()).append(',');
        sb.append("\"phase\":").append(Json.str(combat.getPhase())).append(',');
        sb.append("\"player\":").append(playerJson(combat)).append(',');
        sb.append("\"enemies\":[").append(enemyJson(combat)).append("],");
        sb.append("\"hand\":[").append(handJson(combat)).append("],");
        sb.append("\"piles\":").append(pilesJson(combat)).append(',');
        sb.append("\"result\":").append(Json.str(combat.getResult())).append(',');
        sb.append("\"newLogs\":").append(logsJson(newLogs));
        sb.append('}');
        return sb.toString();
    }

    private static String playerJson(Combat c) {
        return "{\"hp\":" + c.getPlayerHp()
                + ",\"maxHp\":" + c.getPlayerMaxHp()
                + ",\"armor\":" + c.getPlayerBlock()
                + ",\"energy\":" + c.getEnergy()
                + ",\"maxEnergy\":" + c.getPlayerMaxEnergy()
                + ",\"intent\":null"
                + ",\"buffs\":[]}";
    }

    private static String enemyJson(Combat c) {
        Combat.Intent intent = c.getMonsterIntentInfo();
        String intentJson = intent == null
                ? "null"
                : "{\"type\":" + Json.str(intent.type())
                        + ",\"value\":" + intent.value() + "}";
        return "{\"hp\":" + c.getMonsterHp()
                + ",\"maxHp\":" + c.getMonsterMaxHp()
                + ",\"armor\":" + c.getMonsterBlock()
                + ",\"energy\":0"
                + ",\"maxEnergy\":0"
                + ",\"intent\":" + intentJson
                + ",\"buffs\":[]}";
    }

    private static String handJson(Combat c) {
        List<CardInstance> hand = c.getHand();
        StringBuilder sb = new StringBuilder(hand.size() * 64);
        for (int i = 0; i < hand.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(cardJson(hand.get(i)));
        }
        return sb.toString();
    }

    private static String cardJson(CardInstance instance) {
        Card card = instance.card();
        return "{\"id\":" + Json.str(instance.id())
                + ",\"definitionId\":" + Json.str(card.id())
                + ",\"name\":" + Json.str(card.name())
                + ",\"type\":" + Json.str(card.type().name())
                + ",\"cost\":" + card.cost()
                + ",\"description\":" + Json.str(card.description())
                + ",\"exhausts\":" + card.exhausts()
                + ",\"playable\":" + card.playable()
                + ",\"rarity\":" + Json.str(DEFAULT_RARITY)
                + "}";
    }

    private static String pilesJson(Combat c) {
        return "{\"draw\":" + c.getDrawPileSize()
                + ",\"discard\":" + c.getDiscardPileSize()
                + ",\"exhaust\":" + c.getExhaustPileSize()
                + "}";
    }

    private static String logsJson(List<String> logs) {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < logs.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(Json.str(logs.get(i)));
        }
        sb.append(']');
        return sb.toString();
    }
}
