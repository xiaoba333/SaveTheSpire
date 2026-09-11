package com.roguelike.dungeon.http;

import com.roguelike.dungeon.flow.GameController;
import com.roguelike.dungeon.flow.GamePhase;
import com.roguelike.dungeon.flow.MenuController;
import com.roguelike.dungeon.flow.MenuPhase;
import com.roguelike.dungeon.game.battle.Combat;
import com.roguelike.dungeon.game.battle.PlayCardResult;
import com.roguelike.dungeon.game.campfire.CampfireActionResult;
import com.roguelike.dungeon.game.card.Card;
import com.roguelike.dungeon.game.card.CardLibrary;
import com.roguelike.dungeon.game.character.CharacterDefinition;
import com.roguelike.dungeon.game.character.GameCharacterCatalog;
import com.roguelike.dungeon.game.event.EventChoiceResult;
import com.roguelike.dungeon.game.run.RunState;
import com.roguelike.dungeon.game.shop.ShopActionResult;
import com.roguelike.dungeon.game.shop.ShopService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;

/**
 * 统一游戏 HTTP 服务器：单进程承载一整局权威状态（MenuController → RunState + GameController），
 * 把 http-api.md 的端点接到真实后端逻辑上，与前端 HTTP 客户端一一对应。
 *
 * <p>与 {@link BattleServer} 的手写 JSON + com.sun.net.httpserver 写法一致，用单线程
 * executor 串行化所有请求，保证非线程安全的 Combat / GameController 访问有序。</p>
 *
 * <p>地图推进语义：前端对任意节点先调 {@code map/advance} 进入节点（后端即
 * {@link GameController#selectNode}）；随后非战斗节点通过各自的结算端点离开，战斗节点
 * 通过 {@code POST /battles} 启动战斗。即「进入」与「结算」分离，结算统一回地图状态。</p>
 *
 * <p>角色选角：{@code GET /characters} 列出可选角色，{@code POST /game/start} 用选中的角色
 * 懒创建本局（未开局前其余端点返回 {@code NO_ACTIVE_RUN}）。</p>
 */
public final class GameServer {

    private static final String PREFIX_BATTLES = "/api/v1/battles";

    /** 本局随机种子与章节数（MVP 单章节）。 */
    private static final long RUN_SEED = 20260910L;
    private static final int TOTAL_ACTS = 1;

    /** 战斗奖励卡池（含锻造牌，供战斗中途升级手牌）。 */
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
            CardLibrary.SACRIFICE_STRIKE,
            CardLibrary.FORGE);

    private final HttpServer server;
    private final MenuController menuController;

    /** 本局权威状态；{@code POST /game/start} 之前为 null。 */
    private RunState runState;
    private GameController controller;
    private String characterName;

    /** 当前进行中的战斗编号；只在 POST /battles 时生成，随战斗结束失效。 */
    private String battleId;

    public GameServer(int port) throws IOException {
        this.menuController = new MenuController(new GameCharacterCatalog());

        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/v1/characters", this::handleCharacters);
        server.createContext("/api/v1/game/start", this::handleStart);
        server.createContext("/api/v1/character", this::handleCharacter);
        server.createContext("/api/v1/deck", this::handleDeck);
        server.createContext("/api/v1/map", this::handleMap);
        server.createContext("/api/v1/reward", this::handleReward);
        server.createContext("/api/v1/shop", this::handleShop);
        server.createContext("/api/v1/event", this::handleEvent);
        server.createContext("/api/v1/campfire", this::handleCampfire);
        server.createContext("/api/v1/battles", this::handleBattle);
        server.setExecutor(Executors.newSingleThreadExecutor());
    }

    public void start() {
        server.start();
    }

    // ============ 选角 / 开局 ============

    private void handleCharacters(HttpExchange ex) throws IOException {
        try {
            if (!"GET".equals(ex.getRequestMethod())) {
                sendError(ex, 404, "NOT_FOUND", "接口不存在");
                return;
            }
            if (menuController.getPhase() == MenuPhase.MAIN_MENU) {
                menuController.beginCharacterSelect();
            }
            sendJson(ex, 200, GameStateJson.charactersJson(
                    menuController.getAvailableCharacters()));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(ex, 500, "INTERNAL_ERROR", "服务器内部错误");
        }
    }

    private void handleStart(HttpExchange ex) throws IOException {
        try {
            if (!"POST".equals(ex.getRequestMethod())) {
                sendError(ex, 404, "NOT_FOUND", "接口不存在");
                return;
            }
            String characterId = Json.field(readBody(ex), "characterId");
            if (characterId == null) {
                sendError(ex, 400, "INVALID_CHARACTER", "缺少 characterId");
                return;
            }
            try {
                if (menuController.getPhase() == MenuPhase.MAIN_MENU) {
                    menuController.beginCharacterSelect();
                }
                CharacterDefinition def = menuController.selectCharacter(characterId);
                runState = menuController.createRun(characterId, RUN_SEED, TOTAL_ACTS);
                characterName = def.name();
                controller = new GameController(runState, REWARD_POOL, System.out::println);
                sendJson(ex, 200, GameStateJson.characterJson(characterName, runState));
            } catch (IllegalArgumentException | IllegalStateException e) {
                sendError(ex, 400, "INVALID_CHARACTER", e.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendError(ex, 500, "INTERNAL_ERROR", "服务器内部错误");
        }
    }

    // ============ 角色 / 牌组 ============

    private void handleCharacter(HttpExchange ex) throws IOException {
        try {
            if (!"GET".equals(ex.getRequestMethod())) {
                sendError(ex, 404, "NOT_FOUND", "接口不存在");
                return;
            }
            if (requireController(ex) == null) {
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
            if (requireController(ex) == null) {
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
                if (requireController(ex) == null) {
                    return;
                }
                sendJson(ex, 200, mapStateJson());
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

    /** 进入节点：对任意节点类型都只做「进入」，结算交给各自端点。 */
    private void handleAdvance(HttpExchange ex) throws IOException {
        if (requireController(ex) == null) {
            return;
        }
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
            controller.selectNode(nodeId);
            sendJson(ex, 200, mapStateJson());
        } catch (IllegalArgumentException | IllegalStateException e) {
            sendError(ex, 400, "INVALID_NODE", e.getMessage());
        }
    }

    // ============ 奖励 ============

    private void handleReward(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            if ("/api/v1/reward".equals(path) && "GET".equals(ex.getRequestMethod())) {
                if (requireController(ex) == null) {
                    return;
                }
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
        if (requireController(ex) == null) {
            return;
        }
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
                if (requireController(ex) == null) {
                    return;
                }
                sendJson(ex, 200, shopStateJson());
                return;
            }
            if ("/api/v1/shop/buy".equals(path) && "POST".equals(ex.getRequestMethod())) {
                handleBuy(ex);
                return;
            }
            if ("/api/v1/shop/remove".equals(path) && "POST".equals(ex.getRequestMethod())) {
                handleRemove(ex);
                return;
            }
            if ("/api/v1/shop/leave".equals(path) && "POST".equals(ex.getRequestMethod())) {
                handleLeaveShop(ex);
                return;
            }
            sendError(ex, 404, "NOT_FOUND", "接口不存在");
        } catch (Exception e) {
            e.printStackTrace();
            sendError(ex, 500, "INTERNAL_ERROR", "服务器内部错误");
        }
    }

    private String shopStateJson() {
        if (controller.getPhase() != GamePhase.SHOP) {
            return GameStateJson.emptyShopJson();
        }
        return GameStateJson.shopJson(
                runState.getGold(),
                controller.getCurrentShopItems(),
                controller.getShopRemovableCards(),
                controller.isShopCardRemovalUsed(),
                ShopService.CARD_REMOVAL_PRICE);
    }

    private void handleBuy(HttpExchange ex) throws IOException {
        if (requireController(ex) == null) {
            return;
        }
        String itemId = Json.field(readBody(ex), "itemId");
        if (itemId == null) {
            sendError(ex, 400, "INVALID_ITEM", "缺少 itemId");
            return;
        }
        ShopActionResult result;
        try {
            result = controller.buyShopItem(itemId);
        } catch (IllegalStateException e) {
            sendError(ex, 400, "INVALID_ITEM", e.getMessage());
            return;
        }
        if (result == ShopActionResult.SUCCESS) {
            sendJson(ex, 200, shopStateJson());
        } else {
            sendError(ex, 400, result.name(), messageFor(result));
        }
    }

    private void handleRemove(HttpExchange ex) throws IOException {
        if (requireController(ex) == null) {
            return;
        }
        String cardId = Json.field(readBody(ex), "cardId");
        if (cardId == null) {
            sendError(ex, 400, "INVALID_CARD", "缺少 cardId");
            return;
        }
        ShopActionResult result;
        try {
            result = controller.removeCardAtShop(cardId);
        } catch (IllegalStateException e) {
            sendError(ex, 400, "INVALID_CARD", e.getMessage());
            return;
        }
        if (result == ShopActionResult.SUCCESS) {
            sendJson(ex, 200, shopStateJson());
        } else {
            sendError(ex, 400, result.name(), messageFor(result));
        }
    }

    private void handleLeaveShop(HttpExchange ex) throws IOException {
        if (requireController(ex) == null) {
            return;
        }
        try {
            controller.leaveShop();
        } catch (IllegalStateException e) {
            sendError(ex, 400, "INVALID_ACTION", e.getMessage());
            return;
        }
        sendJson(ex, 200, mapStateJson());
    }

    // ============ 事件 ============

    private void handleEvent(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            if ("/api/v1/event".equals(path) && "GET".equals(ex.getRequestMethod())) {
                if (requireController(ex) == null) {
                    return;
                }
                sendJson(ex, 200, GameStateJson.eventJson(
                        controller.getCurrentEvent().orElse(null),
                        controller.getCurrentEventChoices()));
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
        if (requireController(ex) == null) {
            return;
        }
        String choiceId = Json.field(readBody(ex), "choiceId");
        if (choiceId == null) {
            choiceId = "leave"; // 兼容前端「离开」按钮发 null；事件目录统一用 leave 作为离开选项
        }
        EventChoiceResult result;
        try {
            result = controller.chooseEventChoice(choiceId);
        } catch (IllegalStateException e) {
            sendError(ex, 400, "INVALID_CHOICE", e.getMessage());
            return;
        }
        if (result.succeeded()) {
            sendJson(ex, 200, mapStateJson());
        } else {
            sendError(ex, 400, result.status().name(), result.message());
        }
    }

    // ============ 篝火 ============

    private void handleCampfire(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            if ("/api/v1/campfire".equals(path) && "GET".equals(ex.getRequestMethod())) {
                if (requireController(ex) == null) {
                    return;
                }
                sendJson(ex, 200, GameStateJson.campfireJson(
                        controller.getCurrentCampfireActions(),
                        controller.getCampfireUpgradeableCards()));
                return;
            }
            if ("/api/v1/campfire/act".equals(path) && "POST".equals(ex.getRequestMethod())) {
                handleCampfireAct(ex);
                return;
            }
            sendError(ex, 404, "NOT_FOUND", "接口不存在");
        } catch (Exception e) {
            e.printStackTrace();
            sendError(ex, 500, "INTERNAL_ERROR", "服务器内部错误");
        }
    }

    private void handleCampfireAct(HttpExchange ex) throws IOException {
        if (requireController(ex) == null) {
            return;
        }
        String body = readBody(ex);
        String actionId = Json.field(body, "actionId");
        String cardId = Json.field(body, "cardId");
        CampfireActionResult result;
        try {
            result = switch (actionId == null ? "" : actionId) {
                case "rest" -> controller.restAtCampfire();
                case "smith" -> controller.smithAtCampfire(cardId);
                case "leave" -> controller.leaveCampfire();
                default -> throw new IllegalArgumentException("未知的篝火操作：" + actionId);
            };
        } catch (IllegalArgumentException | IllegalStateException e) {
            sendError(ex, 400, "INVALID_ACTION", e.getMessage());
            return;
        }
        if (result.succeeded()) {
            sendJson(ex, 200, mapStateJson());
        } else {
            sendError(ex, 400, result.status().name(), result.message());
        }
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
        if (requireController(ex) == null) {
            return;
        }
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
        if (controller == null || battleId == null || !battleId.equals(id)) {
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

    // ============ 状态助手 ============

    /** 未开局时返回 null 并已写出 400；否则返回当前控制器。 */
    private GameController requireController(HttpExchange ex) throws IOException {
        if (controller == null) {
            sendError(ex, 400, "NO_ACTIVE_RUN", "尚未开始游戏，请先 POST /api/v1/game/start");
            return null;
        }
        return controller;
    }

    private String mapStateJson() {
        return GameStateJson.mapJson(controller.getMapService());
    }

    // ============ 错误信息 ============

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

    private static String messageFor(ShopActionResult result) {
        return switch (result) {
            case ITEM_NOT_FOUND -> "商品不存在";
            case ITEM_ALREADY_SOLD -> "商品已售出";
            case CARD_NOT_FOUND -> "牌组中不存在该卡牌";
            case INSUFFICIENT_GOLD -> "金币不足";
            case CARD_REMOVAL_ALREADY_USED -> "本商店已删除过卡牌";
            case SHOP_CLOSED -> "商店已关闭";
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
