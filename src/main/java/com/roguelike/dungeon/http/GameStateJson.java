package com.roguelike.dungeon.http;

import com.roguelike.dungeon.game.blessing.BlessingOption;
import com.roguelike.dungeon.game.campfire.CampfireAction;
import com.roguelike.dungeon.game.card.Card;
import com.roguelike.dungeon.game.card.CardInstance;
import com.roguelike.dungeon.game.character.CharacterDefinition;
import com.roguelike.dungeon.game.entity.Player;
import com.roguelike.dungeon.game.entity.Relic;
import com.roguelike.dungeon.game.event.EventChoice;
import com.roguelike.dungeon.game.event.EventChoiceResult;
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

    public static String charactersJson(List<CharacterDefinition> characters) {
        StringBuilder sb = new StringBuilder(256);
        sb.append("{\"characters\":[");
        for (int i = 0; i < characters.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            CharacterDefinition character = characters.get(i);
            sb.append("{\"id\":").append(Json.str(character.id()))
                    .append(",\"name\":").append(Json.str(character.name()))
                    .append(",\"description\":").append(Json.str(character.description()))
                    .append(",\"maxHealth\":").append(character.maxHealth())
                    .append(",\"maxEnergy\":").append(character.maxEnergy())
                    .append(",\"startingGold\":").append(character.startingGold())
                    .append('}');
        }
        sb.append("]}");
        return sb.toString();
    }

    public static String runJson(String phase, String name, RunState run) {
        return "{\"phase\":" + Json.str(phase)
                + ",\"character\":" + characterJson(name, run)
                + "}";
    }

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
            sb.append("{\"id\":").append(Json.str(relic.id()))
              .append(",\"name\":").append(Json.str(relic.name()))
              .append(",\"description\":").append(Json.str(relic.description()))
              .append(",\"icon\":null}");
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

    // ---------- 开局房间 ----------

    public static String blessingJson(
            String title,
            String description,
            boolean awaitingCard,
            boolean resolved,
            String chosenOptionId,
            String resultMessage,
            List<BlessingOption> options,
            List<CardInstance> targetCards) {
        StringBuilder sb = new StringBuilder(256);
        sb.append("{\"title\":").append(Json.str(title));
        sb.append(",\"description\":").append(Json.str(description));
        sb.append(",\"awaitingCard\":").append(awaitingCard);
        // 领取之后房间仍可回看（BlessingService 不再被丢弃），所以「还有三个选项」不
        // 等于「还没领」。这三个字段就是给前端区分用的：resolved=true 时把选项渲染成
        // 不可点，并显示当时选了什么、结果如何。没有它们，前端只能靠猜。
        sb.append(",\"resolved\":").append(resolved);
        sb.append(",\"chosenOptionId\":").append(Json.str(chosenOptionId));
        sb.append(",\"resultMessage\":").append(Json.str(resultMessage));
        sb.append(",\"options\":[");
        for (int i = 0; i < options.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            BlessingOption option = options.get(i);
            sb.append("{\"id\":").append(Json.str(option.id()))
                    .append(",\"label\":").append(Json.str(option.label()))
                    .append(",\"description\":").append(Json.str(option.description()))
                    .append(",\"requiresCard\":").append(option.requiresCard())
                    .append(",\"available\":").append(option.available())
                    .append(",\"unavailableReason\":")
                    .append(Json.str(option.unavailableReason()))
                    .append('}');
        }
        sb.append("],\"targetCards\":[");
        for (int i = 0; i < targetCards.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(cardInstanceJson(targetCards.get(i)));
        }
        sb.append("]}");
        return sb.toString();
    }

    public static String emptyBlessingJson() {
        return blessingJson("", "", false, false, "", "", List.of(), List.of());
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
        sb.append("]");
        if (reward.hasRelic()) {
            Relic relic = reward.relic();
            sb.append(",\"relic\":{\"id\":").append(Json.str(relic.id()))
                    .append(",\"name\":").append(Json.str(relic.name()))
                    .append(",\"description\":").append(Json.str(relic.description()))
                    .append(",\"icon\":null}");
        } else {
            sb.append(",\"relic\":null");
        }
        sb.append('}');
        return sb.toString();
    }

    /** 无待领取奖励（或已领取完）时的空奖励。 */
    public static String emptyRewardJson() {
        return "{\"gold\":0,\"cardChoices\":[],\"relic\":null}";
    }

    // ---------- 商店 ----------

    /**
     * 真实商店快照：金币、未售出的商品、可删卡列表、删卡状态与价格。
     * availableItems 来自 ShopService.getAvailableItems()（只含未售出），故 sold 恒 false。
     *
     * <p>商品现在有<b>两种</b>：卡牌（内嵌一份 {@code card}，与奖励候选卡同构，前端据此
     * 渲染卡面）和遗物（{@code card} 为 null，{@code kind} 为 RELIC）。ShopService 每间商店
     * 都会塞一件遗物，所以这里的两个分支是常态路径，不是兜底。</p>
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
            if (item.isRelic()) {
                // 内嵌一份 relic，形状与 characterJson 里的 relics 一致 —— 前端要拿
                // relic.id 去 RelicTheme 取图标。商品自己的 id（shop-relic-1）是购买时
                // 回传用的槽位号，不是遗物 id，两者不能混。
                sb.append("{\"id\":").append(Json.str(item.id()))
                  .append(",\"name\":").append(Json.str(item.relic().name()))
                  .append(",\"kind\":\"RELIC\"")
                  .append(",\"price\":").append(item.price())
                  .append(",\"description\":").append(Json.str(item.relic().description()))
                  .append(",\"rarity\":").append(Json.str(item.relic().rarity().name()))
                  .append(",\"sold\":false")
                  .append(",\"card\":null")
                  .append(",\"relic\":{\"id\":").append(Json.str(item.relic().id()))
                  .append(",\"name\":").append(Json.str(item.relic().name()))
                  .append(",\"description\":").append(Json.str(item.relic().description()))
                  .append(",\"icon\":null}")
                  .append('}');
                continue;
            }
            String rarity = item.card().rarity().name();
            sb.append("{\"id\":").append(Json.str(item.id()))
              .append(",\"name\":").append(Json.str(item.card().name()))
              .append(",\"kind\":\"CARD\"")
              .append(",\"price\":").append(item.price())
              .append(",\"description\":").append(Json.str(item.card().description()))
              .append(",\"rarity\":").append(Json.str(rarity))
              .append(",\"sold\":false")
              .append(",\"card\":").append(cardDefJson(item.card(), rarity))
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
              .append(",\"unavailableReason\":").append(Json.str(choice.unavailableReason()))
              .append('}');
        }
        sb.append("]}");
        return sb.toString();
    }

    /** 事件选项结算成功后的结果（success/status/message），供前端展示结果页。 */
    public static String eventChoiceResultJson(EventChoiceResult result) {
        return "{\"success\":" + result.succeeded()
                + ",\"status\":" + Json.str(result.status().name())
                + ",\"message\":" + Json.str(result.message())
                + "}";
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
