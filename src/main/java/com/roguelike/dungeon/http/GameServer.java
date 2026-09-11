package com.roguelike.dungeon.http;

import com.roguelike.dungeon.flow.GameController;
import com.roguelike.dungeon.flow.GamePhase;
import com.roguelike.dungeon.flow.LevelResult;
import com.roguelike.dungeon.flow.RunFactory;
import com.roguelike.dungeon.game.battle.Combat;
import com.roguelike.dungeon.game.battle.PlayCardResult;
import com.roguelike.dungeon.game.card.Card;
import com.roguelike.dungeon.game.card.CardInstance;
import com.roguelike.dungeon.game.card.CardLibrary;
import com.roguelike.dungeon.game.character.GameCharacterCatalog;
import com.roguelike.dungeon.game.entity.Player;
import com.roguelike.dungeon.game.map.MapNode;
import com.roguelike.dungeon.game.map.MapNodeType;
import com.roguelike.dungeon.game.run.RunState;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;

/**
 * 统一游戏 HTTP 服务器：单进程承载一整局权威状态（RunState + GameController），
 * 把 http-api.md 的 14 个端点接到真实后端逻辑上，与前端 HTTP 客户端一一对应。
 *
 * <p>与 {@link BattleServer} 的手写 JSON + com.sun.net.httpserver 写法一致，用单线程
 * executor 串行化所有请求，保证非线程安全的 Combat / GameController 访问有序。</p>
 *
 * <p>地图推进语义：前端对战斗类节点（BATTLE/ELITE/BOSS）在开战前调 {@code map/advance}
 * 进入节点（后端即 {@link GameController#selectNode} 并启动战斗）；对事件/商店/休息节点
 * 则只在结算后调 {@code map/advance} 一次，后端按「进入 + 完成」一次性结算。</p>
 */
public final class GameServer {

    private static final String PREFIX_BATTLES = "/api/v1/battles";

    /** 本局随机种子与章节数（MVP 单章节）。 */
    private static final long RUN_SEED = 20260910L;
    private static final int TOTAL_ACTS = 1;

    /** 战斗奖励卡池（与 GameFlowDebugMain 的 REWARD_POOL 一致）。 */
    private static final List<Card> REWARD_POOL = List.of(
            CardLibrary.BASH,
            CardLibrary.QUICK_SLASH,
            CardLibrary.HEAVY_STRIKE,
            CardLibrary.IRON_WAVE,
            CardLibrary.SHRUG_IT_OFF,
            CardLibrary.BLOODLETTING,
            CardLibrary.BLOOD_BURST,
            CardLibrary.BLOOD_LORD,
            CardLibrary.BLOOD_SACRIFICE,
            CardLibrary.BLOOD_TRANSFUSION,
            CardLibrary.FEAST,
            CardLibrary.SACRIFICE_STRIKE);

    /** 固定商店库存（MVP）。 */
    private static final List<ShopItem> SHOP_ITEMS = List.of(
            new ShopItem("c_strike", "打击", "CARD", 45,
                    "造成 6 点伤害。", "COMMON", CardLibrary.STRIKE),
            new ShopItem("c_heavy_strike", "重击", "CARD", 90,
                    "造成 12 点伤害。", "RARE", CardLibrary.HEAVY_STRIKE),
            new ShopItem("p_heal", "治疗药水", "POTION", 60,
                    "恢复 10 点生命。", null, null),
            new ShopItem("s_remove", "移除一张卡", "SERVICE", 75,
                    "从牌组中移除一张卡。", null, null));

    private final HttpServer server;
    private final RunState runState;
    private final GameController controller;
    private final String characterName;
    private final Set<String> soldItems = new HashSet<>();

    /** 当前进行中的战斗编号；只在 POST /battles 时生成，随战斗结束失效。 */
    private String battleId;

    public GameServer(int port) throws IOException {
        this.runState = RunFactory.createRun(
                new GameCharacterCatalog(), "blood", RUN_SEED, TOTAL_ACTS);
        this.characterName = new GameCharacterCatalog().getById("blood").name();
        this.controller = new GameController(runState, REWARD_POOL, System.out::println);

        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/v1/character", this::handleCharacter);
        server.createContext("/api/v1/deck", this::handleDeck);
        server.createContext("/api/v1/map", this::handleMap);
        server.createContext("/api/v1/reward", this::handleReward);
        server.createContext("/api/v1/shop", this::handleShop);
        server.createContext("/api/v1/event", this::handleEvent);
        server.createContext("/api/v1/battles", this::handleBattle);
        server.setExecutor(Executors.newSingleThreadExecutor());
    }

    public void start() {
        server.start();
    }

    // ============ 角色 / 牌组 ============

    private void handleCharacter(HttpExchange ex) throws IOException {
        try {
            if (!"GET".equals(ex.getRequestMethod())) {
                sendError(ex, 404, "NOT_FOUND", "接口不存在");
                return;
            }
            sendJson(ex, 200, GameStateJson.characterJson(characterName, runState));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(ex, 500, "INTERNAL_ERROR", "服务器内部错误");
        }
    }

    private void handleDeck(HttpExchange ex) throws IOException {
        try {
            if (!"GET".equals(ex.getRequestMethod())) {
                sendError(ex, 404, "NOT_FOUND", "接口不存在");
                return;
            }
            sendJson(ex, 200, GameStateJson.deckJson(runState));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(ex, 500, "INTERNAL_ERROR", "服务器内部错误");
        }
    }

    // ============ 地图 ============

    private void handleMap(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            if ("/api/v1/map".equals(path) && "GET".equals(ex.getRequestMethod())) {
                sendJson(ex, 200, GameStateJson.mapJson(controller.getMapService()));
                return;
            }
            if ("/api/v1/map/advance".equals(path) && "POST".equals(ex.getRequestMethod())) {
                handleAdvance(ex);
                return;
            }
            sendError(ex, 404, "NOT_FOUND", "接口不存在");
        } catch (Exception e) {
            e.printStackTrace();
            sendError(ex, 500, "INTERNAL_ERROR", "服务器内部错误");
        }
    }

    private void handleAdvance(HttpExchange ex) throws IOException {
        String nodeIdRaw = Json.field(readBody(ex), "nodeId");
        if (nodeIdRaw == null) {
            sendError(ex, 400, "INVALID_NODE", "缺少 nodeId");
            return;
        }
        int nodeId;
        try {
            nodeId = Integer.parseInt(nodeIdRaw);
        } catch (NumberFormatException e) {
            sendError(ex, 400, "INVALID_NODE", "nodeId 必须是数字");
            return;
        }

        try {
            MapNode node = controller.selectNode(nodeId);
            boolean battleNode = node.type() == MapNodeType.BATTLE
                    || node.type() == MapNodeType.ELITE
                    || node.type() == MapNodeType.BOSS;
            if (!battleNode) {
                if (node.type() == MapNodeType.REST) {
                    // 休息：恢复 30% 最大生命（至少 1 点）
                    Player player = runState.getPlayer();
                    player.heal(Math.max(1, player.getMaxHealth() * 30 / 100));
                }
                // 事件 / 商店 / 休息：一次性「进入 + 结算」，回到地图阶段
                controller.onLevelFinished(LevelResult.COMPLETED);
            }
            sendJson(ex, 200, GameStateJson.mapJson(controller.getMapService()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            sendError(ex, 400, "INVALID_NODE", e.getMessage());
        }
    }

    // ============ 奖励 ============

    private void handleReward(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            if ("/api/v1/reward".equals(path) && "GET".equals(ex.getRequestMethod())) {
                sendJson(ex, 200, rewardStateJson());
                return;
            }
            if ("/api/v1/reward/select".equals(path) && "POST".equals(ex.getRequestMethod())) {
                handleSelectReward(ex);
                return;
            }
            sendError(ex, 404, "NOT_FOUND", "接口不存在");
        } catch (Exception e) {
            e.printStackTrace();
            sendError(ex, 500, "INTERNAL_ERROR", "服务器内部错误");
        }
    }

    private String rewardStateJson() {
        if (controller.getPhase() == GamePhase.REWARD) {
            return GameStateJson.rewardJson(controller.getCurrentReward().orElseThrow());
        }
        return GameStateJson.emptyRewardJson();
    }

    private void handleSelectReward(HttpExchange ex) throws IOException {
        String cardId = Json.field(readBody(ex), "cardId"); // null = 跳过
        if (controller.getPhase() != GamePhase.REWARD) {
            // 无待领取奖励（如 Boss 战后）：无操作，返回空奖励
            sendJson(ex, 200, GameStateJson.emptyRewardJson());
            return;
        }
        try {
            if (cardId == null) {
                controller.skipRewardCard();
            } else {
                controller.claimRewardCard(cardId);
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            sendError(ex, 400, "INVALID_CARD", e.getMessage());
            return;
        }
        sendJson(ex, 200, GameStateJson.emptyRewardJson());
    }

    // ============ 商店 ============

    private void handleShop(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            if ("/api/v1/shop".equals(path) && "GET".equals(ex.getRequestMethod())) {
                sendJson(ex, 200, shopStateJson());
                return;
            }
            if ("/api/v1/shop/buy".equals(path) && "POST".equals(ex.getRequestMethod())) {
                handleBuy(ex);
                return;
            }
            sendError(ex, 404, "NOT_FOUND", "接口不存在");
        } catch (Exception e) {
            e.printStackTrace();
            sendError(ex, 500, "INTERNAL_ERROR", "服务器内部错误");
        }
    }

    private String shopStateJson() {
        return GameStateJson.shopJson(runState.getGold(), SHOP_ITEMS, soldItems);
    }

    private void handleBuy(HttpExchange ex) throws IOException {
        String itemId = Json.field(readBody(ex), "itemId");
        ShopItem item = SHOP_ITEMS.stream()
                .filter(it -> it.id().equals(itemId))
                .findFirst()
                .orElse(null);
        if (item == null) {
            sendError(ex, 400, "INVALID_ITEM", "商品不存在");
            return;
        }
        if (soldItems.contains(item.id())) {
            sendError(ex, 400, "ITEM_SOLD", "商品已售出");
            return;
        }
        if (!runState.spendGold(item.price())) {
            sendError(ex, 400, "NOT_ENOUGH_GOLD", "金币不足");
            return;
        }

        switch (item.kind()) {
            case "CARD" -> runState.addCard(
                    new CardInstance(UUID.randomUUID().toString(), item.card()));
            case "POTION" -> runState.getPlayer().heal(10); // 治疗药水：恢复 10 点生命
            case "SERVICE" -> { /* 移除卡需要前端选卡，MVP 暂不结算 */ }
            default -> { }
        }
        soldItems.add(item.id());
        sendJson(ex, 200, shopStateJson());
    }

    // ============ 事件 ============

    private void handleEvent(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            if ("/api/v1/event".equals(path) && "GET".equals(ex.getRequestMethod())) {
                sendJson(ex, 200, GameStateJson.eventJson());
                return;
            }
            if ("/api/v1/event/choose".equals(path) && "POST".equals(ex.getRequestMethod())) {
                handleChoose(ex);
                return;
            }
            sendError(ex, 404, "NOT_FOUND", "接口不存在");
        } catch (Exception e) {
            e.printStackTrace();
            sendError(ex, 500, "INTERNAL_ERROR", "服务器内部错误");
        }
    }

    private void handleChoose(HttpExchange ex) throws IOException {
        String choiceId = Json.field(readBody(ex), "choiceId"); // null = 离开
        if (choiceId == null) {
            sendJson(ex, 200, "{}");
            return;
        }
        Player player = runState.getPlayer();
        switch (choiceId) {
            case "offer" -> runState.addCard(
                    new CardInstance(UUID.randomUUID().toString(), CardLibrary.STRIKE));
            case "pray" -> player.heal(5);
            case "shatter" -> {
                player.takeDamage(5);
                if (player.isDead()) {
                    player.setHealth(1); // 至少保留 1 点生命
                }
                runState.addGold(50);
            }
            case "touch" -> { /* 条件不足，前端已禁用 */ }
            default -> { }
        }
        sendJson(ex, 200, "{}");
    }

    // ============ 战斗 ============

    private void handleBattle(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            String method = ex.getRequestMethod();

            if (PREFIX_BATTLES.equals(path) && "POST".equals(method)) {
                handleStartBattle(ex);
                return;
            }

            String prefixWithSlash = PREFIX_BATTLES + "/";
            if (path.startsWith(prefixWithSlash)) {
                String rest = path.substring(prefixWithSlash.length());
                int slash = rest.indexOf('/');
                if (slash < 0) {
                    // /api/v1/battles/{id}
                    if ("GET".equals(method)) {
                        handleGetState(ex, rest);
                    } else {
                        sendError(ex, 404, "NOT_FOUND", "接口不存在");
                    }
                } else {
                    String id = rest.substring(0, slash);
                    String action = rest.substring(slash + 1);
                    if ("POST".equals(method) && "play".equals(action)) {
                        handlePlay(ex, id);
                    } else if ("POST".equals(method) && "end-turn".equals(action)) {
                        handleEndTurn(ex, id);
                    } else {
                        sendError(ex, 404, "NOT_FOUND", "接口不存在");
                    }
                }
                return;
            }

            sendError(ex, 404, "NOT_FOUND", "接口不存在");
        } catch (Exception e) {
            e.printStackTrace();
            sendError(ex, 500, "INTERNAL_ERROR", "服务器内部错误");
        }
    }

    private void handleStartBattle(HttpExchange ex) throws IOException {
        Combat combat = controller.getCurrentCombat().orElse(null);
        if (combat == null) {
            sendError(ex, 400, "NO_ACTIVE_BATTLE", "当前没有进行中的战斗，请先在地图选择战斗节点");
            return;
        }
        battleId = UUID.randomUUID().toString();
        sendState(ex, combat);
    }

    private void handleGetState(HttpExchange ex, String id) throws IOException {
        Combat combat = requireCombat(ex, id);
        if (combat == null) {
            return;
        }
        // 查询不产生操作，newLogs 固定为空，也不 drain，避免吃掉下一次操作的增量日志。
        sendJson(ex, 200, BattleStateJson.toJson(battleId, combat, List.of()));
    }

    private void handlePlay(HttpExchange ex, String id) throws IOException {
        Combat combat = requireCombat(ex, id);
        if (combat == null) {
            return;
        }
        String body = readBody(ex);
        String cardId = Json.field(body, "cardId");
        String targetCardId = Json.field(body, "targetCardId");
        PlayCardResult result = combat.playCard(cardId, targetCardId);
        if (result == PlayCardResult.SUCCESS) {
            sendState(ex, combat);
        } else {
            // 失败操作不返回状态，丢弃其日志，避免串到下一次成功响应的 newLogs 里。
            combat.drainNewLogs();
            sendError(ex, 400, result.name(), messageFor(result));
        }
    }

    private void handleEndTurn(HttpExchange ex, String id) throws IOException {
        Combat combat = requireCombat(ex, id);
        if (combat == null) {
            return;
        }
        combat.endPlayerTurn();
        sendState(ex, combat);
    }

    /** 按 battleId 校验并取当前战斗；无效时返回 null 并已写出 404。 */
    private Combat requireCombat(HttpExchange ex, String id) throws IOException {
        if (battleId == null || !battleId.equals(id)) {
            sendError(ex, 404, "BATTLE_NOT_FOUND", "战斗不存在或已过期");
            return null;
        }
        Combat combat = controller.getCurrentCombat().orElse(null);
        if (combat == null) {
            sendError(ex, 404, "BATTLE_NOT_FOUND", "战斗不存在或已过期");
            return null;
        }
        return combat;
    }

    private void sendState(HttpExchange ex, Combat combat) throws IOException {
        List<String> logs = combat.drainNewLogs();
        sendJson(ex, 200, BattleStateJson.toJson(battleId, combat, logs));
    }

    private static String messageFor(PlayCardResult result) {
        return switch (result) {
            case NOT_PLAYER_TURN -> "当前不是玩家回合";
            case INVALID_CARD -> "手牌中不存在该 cardId";
            case NOT_ENOUGH_ENERGY -> "能量不足";
            case CARD_NOT_PLAYABLE -> "该牌不可打出";
            case BATTLE_FINISHED -> "战斗已结束";
            case SUCCESS -> "成功";
        };
    }

    // ============ HTTP 基础 ============

    private static String readBody(HttpExchange ex) throws IOException {
        return new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static void sendJson(HttpExchange ex, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }

    private static void sendError(HttpExchange ex, int status, String code, String message)
            throws IOException {
        sendJson(ex, status, "{\"code\":" + Json.str(code)
                + ",\"message\":" + Json.str(message) + "}");
    }
}
