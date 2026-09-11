package com.roguelike.dungeon.http;

import com.roguelike.dungeon.game.campfire.CampfireAction;
import com.roguelike.dungeon.game.card.Card;
import com.roguelike.dungeon.game.card.CardInstance;
import com.roguelike.dungeon.game.character.CharacterDefinition;
import com.roguelike.dungeon.game.entity.Player;
import com.roguelike.dungeon.game.entity.Relic;
import com.roguelike.dungeon.game.event.EventChoice;
import com.roguelike.dungeon.game.event.GameEvent;
import com.roguelike.dungeon.game.map.MapNode;
import com.roguelike.dungeon.game.map.MapNodeState;
import com.roguelike.dungeon.game.map.MapService;
import com.roguelike.dungeon.game.reward.BattleReward;
import com.roguelike.dungeon.game.run.RunState;

import java.util.List;

/**
 * 把 {@link RunState} / {@link MapService} / {@link BattleReward} 等映射为
 * http-api.md 第 7 节定义的 JSON，字段命名 camelCase，与前端 {@code SaveTheSpire.Models} 一一对应。
 *
 * <p>后端权威：这里只读快照并序列化，不改变任何游戏状态。</p>
 */
public final class GameStateJson {

    private GameStateJson() {
    }

    // ---------- 角色 ----------

    public static String characterJson(String name, RunState run) {
        Player player = run.getPlayer();
        StringBuilder sb = new StringBuilder(256);
        sb.append("{\"name\":").append(Json.str(name));
        sb.append(",\"hp\":").append(player.getHealth());
        sb.append(",\"maxHp\":").append(player.getMaxHealth());
        sb.append(",\"gold\":").append(run.getGold());
        sb.append(",\"relics\":[");
        List<Relic> relics = player.getRelics();
        for (int i = 0; i < relics.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            Relic relic = relics.get(i);
            // 后端 Relic 没有独立 id，暂用 name 占位；icon 留空等美术资源。
            sb.append("{\"id\":").append(Json.str(relic.name()))
              .append(",\"name\":").append(Json.str(relic.name()))
              .append(",\"description\":").append(Json.str(relic.description()))
              .append(",\"icon\":null}");
        }
        sb.append("]}");
        return sb.toString();
    }

    /** 可选角色列表（选角界面用）。 */
    public static String charactersJson(List<CharacterDefinition> characters) {
        StringBuilder sb = new StringBuilder(256);
        sb.append("{\"characters\":[");
        for (int i = 0; i < characters.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            CharacterDefinition c = characters.get(i);
            sb.append("{\"id\":").append(Json.str(c.id()))
              .append(",\"name\":").append(Json.str(c.name()))
              .append(",\"description\":").append(Json.str(c.description()))
              .append(",\"maxHealth\":").append(c.maxHealth())
              .append(",\"maxEnergy\":").append(c.maxEnergy())
              .append(",\"startingGold\":").append(c.startingGold())
              .append('}');
        }
        sb.append("]}");
        return sb.toString();
    }

    // ---------- 牌组 ----------

    public static String deckJson(RunState run) {
        List<CardInstance> deck = run.getDeck();
        StringBuilder sb = new StringBuilder(deck.size() * 128 + 16);
        sb.append("{\"cards\":[");
        for (int i = 0; i < deck.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(cardInstanceJson(deck.get(i)));
        }
        sb.append("]}");
        return sb.toString();
    }

    // ---------- 地图 ----------

    public static String mapJson(MapService mapService) {
        List<MapNode> nodes = mapService.getNodes();
        StringBuilder sb = new StringBuilder(nodes.size() * 160 + 16);
        sb.append("{\"nodes\":[");
        for (int i = 0; i < nodes.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(nodeJson(mapService, nodes.get(i)));
        }
        sb.append("],\"currentNodeId\":");
        sb.append(mapService.getCurrentNode()
                .map(node -> Json.str(String.valueOf(node.id())))
                .orElse("null"));
        sb.append('}');
        return sb.toString();
    }

    private static String nodeJson(MapService mapService, MapNode node) {
        StringBuilder sb = new StringBuilder(160);
        sb.append("{\"id\":").append(Json.str(String.valueOf(node.id())))
          .append(",\"type\":").append(Json.str(node.type().name()))
          // 后端 floor（层，0 在下）→ 前端 column（竖排，越大越靠上，Boss 在上）
          .append(",\"column\":").append(node.floor())
          // 后端 column（列）→ 前端 row（同层内横向）
          .append(",\"row\":").append(node.column())
          .append(",\"state\":").append(Json.str(stateName(mapService.getNodeState(node.id()))))
          .append(",\"nextIds\":[");
        List<Integer> next = node.nextNodeIds();
        for (int i = 0; i < next.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(Json.str(String.valueOf(next.get(i))));
        }
        sb.append("]}");
        return sb.toString();
    }

    /** 后端 MapNodeState → 前端 state 字符串。 */
    private static String stateName(MapNodeState state) {
        return switch (state) {
            case AVAILABLE -> "SELECTABLE";
            case COMPLETED -> "PASSED";
            case LOCKED -> "LOCKED";
            case CURRENT -> "CURRENT";
        };
    }

    // ---------- 奖励 ----------

    public static String rewardJson(BattleReward reward) {
        StringBuilder sb = new StringBuilder(256);
        sb.append("{\"gold\":").append(reward.gold());
        sb.append(",\"cardChoices\":[");
        List<Card> choices = reward.cardChoices();
        for (int i = 0; i < choices.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(cardDefJson(choices.get(i), "COMMON"));
        }
        sb.append("]}");
        return sb.toString();
    }

    /** 无待领取奖励（或已领取完）时的空奖励。 */
    public static String emptyRewardJson() {
        return "{\"gold\":0,\"cardChoices\":[]}";
    }

    // ---------- 商店 ----------

    /**
     * 真实商店快照：金币、未售出的卡牌商品、可删卡列表、删卡状态与价格。
     * availableItems 来自 ShopService.getAvailableItems()（只含未售出），故 sold 恒 false。
     */
    public static String shopJson(
            int gold,
            List<com.roguelike.dungeon.game.shop.ShopItem> availableItems,
            List<CardInstance> removableCards,
            boolean cardRemovalUsed,
            int cardRemovalPrice) {
        StringBuilder sb = new StringBuilder(512);
        sb.append("{\"gold\":").append(gold).append(",\"items\":[");
        for (int i = 0; i < availableItems.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            com.roguelike.dungeon.game.shop.ShopItem item = availableItems.get(i);
            sb.append("{\"id\":").append(Json.str(item.id()))
              .append(",\"name\":").append(Json.str(item.card().name()))
              .append(",\"kind\":\"CARD\"")
              .append(",\"price\":").append(item.price())
              .append(",\"description\":").append(Json.str(item.card().description()))
              .append(",\"rarity\":\"COMMON\"")
              .append(",\"sold\":false")
              .append('}');
        }
        sb.append("],\"removableCards\":[");
        for (int i = 0; i < removableCards.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(cardInstanceJson(removableCards.get(i)));
        }
        sb.append("],\"cardRemovalUsed\":").append(cardRemovalUsed)
          .append(",\"cardRemovalPrice\":").append(cardRemovalPrice)
          .append('}');
        return sb.toString();
    }

    /** 不在商店阶段时的空商店（避免前端解析到 null）。 */
    public static String emptyShopJson() {
        return "{\"gold\":0,\"items\":[],\"removableCards\":[],"
                + "\"cardRemovalUsed\":false,\"cardRemovalPrice\":0}";
    }

    // ---------- 篝火 ----------

    /** 当前篝火操作与可锻造的永久牌组卡牌。 */
    public static String campfireJson(
            List<CampfireAction> actions,
            List<CardInstance> upgradeableCards) {
        StringBuilder sb = new StringBuilder(256);
        sb.append("{\"actions\":[");
        for (int i = 0; i < actions.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            CampfireAction action = actions.get(i);
            sb.append("{\"id\":").append(Json.str(action.id()))
              .append(",\"label\":").append(Json.str(action.label()))
              .append(",\"description\":").append(Json.str(action.description()))
              .append(",\"available\":").append(action.available())
              .append(",\"unavailableReason\":").append(Json.str(action.unavailableReason()))
              .append('}');
        }
        sb.append("],\"upgradeableCards\":[");
        for (int i = 0; i < upgradeableCards.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(cardInstanceJson(upgradeableCards.get(i)));
        }
        sb.append("]}");
        return sb.toString();
    }

    // ---------- 事件 ----------

    /** 当前真实事件及其选项快照（由 EventCatalog / EventService 计算）。 */
    public static String eventJson(GameEvent event, List<EventChoice> choices) {
        if (event == null) {
            return "{\"id\":\"\",\"title\":\"\",\"description\":\"\",\"choices\":[]}";
        }
        StringBuilder sb = new StringBuilder(256);
        sb.append("{\"id\":").append(Json.str(event.id()))
          .append(",\"title\":").append(Json.str(event.title()))
          .append(",\"description\":").append(Json.str(event.description()))
          .append(",\"choices\":[");
        for (int i = 0; i < choices.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            EventChoice choice = choices.get(i);
            sb.append("{\"id\":").append(Json.str(choice.id()))
              .append(",\"label\":").append(Json.str(choice.label()))
              .append(",\"description\":").append(Json.str(choice.description()))
              .append(",\"disabled\":").append(!choice.available())
              .append('}');
        }
        sb.append("]}");
        return sb.toString();
    }

    // ---------- 卡牌序列化助手 ----------

    /**
     * 牌组里的一张具体牌实例：id 用实例唯一 id，升级牌展示升级后的名称 / 说明 / 费用。
     * 稀有度后端暂未提供，统一按 BASIC；颜色暂按 red（前端 CardTheme 的默认色）。
     */
    private static String cardInstanceJson(CardInstance instance) {
        Card card = instance.card();
        return "{\"id\":" + Json.str(instance.id())
            + ",\"definitionId\":" + Json.str(card.id())
            + ",\"name\":" + Json.str(instance.displayName())
            + ",\"type\":" + Json.str(card.type().name())
            + ",\"cost\":" + instance.effectiveCost()
            + ",\"description\":" + Json.str(instance.displayDescription())
            + ",\"exhausts\":" + card.exhausts()
            + ",\"playable\":" + card.playable()
            + ",\"rarity\":\"BASIC\""
            + ",\"color\":\"red\"}";
    }

    /**
     * 奖励候选卡（卡牌定义，无实例）：id 用定义 id，前端选卡时回传该 id 给后端匹配。
     */
    private static String cardDefJson(Card card, String rarity) {
        return "{\"id\":" + Json.str(card.id())
            + ",\"definitionId\":" + Json.str(card.id())
            + ",\"name\":" + Json.str(card.name())
            + ",\"type\":" + Json.str(card.type().name())
            + ",\"cost\":" + card.cost()
            + ",\"description\":" + Json.str(card.description())
            + ",\"exhausts\":" + card.exhausts()
            + ",\"playable\":" + card.playable()
            + ",\"rarity\":" + Json.str(rarity)
            + ",\"color\":\"red\"}";
    }
}
