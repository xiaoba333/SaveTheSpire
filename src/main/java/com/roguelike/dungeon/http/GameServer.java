package com.roguelike.dungeon.http;

import com.roguelike.dungeon.flow.GameController;
import com.roguelike.dungeon.flow.GamePhase;
import com.roguelike.dungeon.flow.RunFactory;
import com.roguelike.dungeon.game.battle.Combat;
import com.roguelike.dungeon.game.battle.PlayCardResult;
import com.roguelike.dungeon.game.blessing.BlessingActionResult;
import com.roguelike.dungeon.game.blessing.BlessingService;
import com.roguelike.dungeon.game.campfire.CampfireActionResult;
import com.roguelike.dungeon.game.card.CardInstance;
import com.roguelike.dungeon.game.card.CardLibrary;
import com.roguelike.dungeon.game.character.CharacterDefinition;
import com.roguelike.dungeon.game.character.GameCharacterCatalog;
import com.roguelike.dungeon.game.event.EventChoiceResult;
import com.roguelike.dungeon.game.run.RunState;
import com.roguelike.dungeon.game.shop.ShopActionResult;
import com.roguelike.dungeon.game.shop.ShopService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 统一游戏 HTTP 服务器：单进程承载一整局权威状态（RunState + GameController），
 * 把 http-api.md 的端点接到真实后端逻辑上，与前端 HTTP 客户端一一对应。
 *
 * <p>与 {@link BattleServer} 的手写 JSON + com.sun.net.httpserver 写法一致，用单线程
 * executor 串行化所有请求，保证非线程安全的 Combat / GameController 访问有序。</p>
 *
 * <p>地图推进语义：前端对任意节点先调 {@code map/advance} 进入节点（后端即
 * {@link GameController#selectNode}，由它按节点类型切到战斗 / 事件 / 商店 / 休息阶段）；
 * 随后非战斗节点通过各自的结算端点离开（{@code event/choose}、{@code shop/leave}、
 * {@code campfire/act}），战斗节点通过 {@code POST /battles} 启动战斗。即「进入」与
 * 「结算」分离，结算统一回地图状态。</p>
 *
 * <p>开局：{@code GET /characters} 列出可选角色，随后可用两个等价的入口开局——
 * {@code POST /game/start}（前端选角界面用，只传 characterId）与 {@code POST /runs}
 * （协议文档入口，可额外传 seed / actCount 以便复现）。未开局前其余端点返回
 * {@code NO_ACTIVE_RUN}。</p>
 */
public final class GameServer {

    private static final String PREFIX_BATTLES = "/api/v1/battles";

    /** 未显式指定 actCount 时的章节数；至少 2 章，才能在打完第一章 Boss 后进入下一层。 */
    private static final int DEFAULT_ACT_COUNT = 2;

    // 固定商店库存表（SHOP_ITEMS）已随 dev 一起移除：#92 之后商店由
    // game/shop/ShopService 动态生成（含 #100 加的遗物商品），HTTP 层只做透传。
    private final HttpServer server;
    private final GameCharacterCatalog catalog = new GameCharacterCatalog();

    /** 本局权威状态；开局之前为 null。 */
    private RunState runState;
    private GameController controller;
    private String characterName;

    /** 当前进行中的战斗编号；只在 POST /battles 时生成，随战斗结束失效。 */
    private String battleId;

    public GameServer(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/v1/characters", withCors(this::handleCharacters));
        server.createContext("/api/v1/game/start", withCors(this::handleStart));
        server.createContext("/api/v1/runs", withCors(this::handleRuns));
        server.createContext("/api/v1/character", withCors(this::handleCharacter));
        server.createContext("/api/v1/deck", withCors(this::handleDeck));
        server.createContext("/api/v1/map", withCors(this::handleMap));
        server.createContext("/api/v1/reward", withCors(this::handleReward));
        server.createContext("/api/v1/blessing", withCors(this::handleBlessing));
        server.createContext("/api/v1/shop", withCors(this::handleShop));
        server.createContext("/api/v1/event", withCors(this::handleEvent));
        server.createContext("/api/v1/campfire", withCors(this::handleCampfire));
        server.createContext("/api/v1/battles", withCors(this::handleBattle));
        server.setExecutor(Executors.newSingleThreadExecutor());
    }

    public void start() {
        server.start();
    }

    public int getPort() {
        return server.getAddress().getPort();
    }

    public void stop() {
        server.stop(0);
    }

    private HttpHandler withCors(HttpHandler next) {
        return exchange -> {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }
            next.handle(exchange);
        };
    }

    /** 未开局时返回 null 并已写出 400；否则返回当前控制器。 */
    private GameController requireController(HttpExchange ex) throws IOException {
        if (controller == null || runState == null) {
            sendError(ex, 400, "NO_ACTIVE_RUN",
                    "尚未开始游戏，请先 POST /api/v1/game/start 或 /api/v1/runs");
            return null;
        }
        return controller;
    }

    // ============ 选角 / 开局 ============

    private void handleCharacters(HttpExchange ex) throws IOException {
        try {
            if (!"GET".equals(ex.getRequestMethod())) {
                sendError(ex, 404, "NOT_FOUND", "接口不存在");
                return;
            }
            sendJson(ex, 200, GameStateJson.charactersJson(catalog.getAvailableCharacters()));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(ex, 500, "INTERNAL_ERROR", "服务器内部错误");
        }
    }

    /** {@code POST /api/v1/game/start}：前端选角界面入口，只传 characterId。 */
    private void handleStart(HttpExchange ex) throws IOException {
        try {
            if (!"POST".equals(ex.getRequestMethod())) {
                sendError(ex, 404, "NOT_FOUND", "接口不存在");
                return;
            }
            CharacterDefinition character = startRun(ex, readBody(ex));
            if (character == null) {
                return;
            }
            sendJson(ex, 200, GameStateJson.characterJson(characterName, runState));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(ex, 500, "INTERNAL_ERROR", "服务器内部错误");
        }
    }

    /** {@code POST /api/v1/runs}：协议文档入口，可传 seed / actCount 复现同一局。 */
    private void handleRuns(HttpExchange ex) throws IOException {
        try {
            if ("GET".equals(ex.getRequestMethod())) {
                if (requireController(ex) == null) {
                    return;
                }
                sendJson(ex, 200, GameStateJson.runJson(
                        controller.getPhase().name(), characterName, runState));
                return;
            }
            if (!"POST".equals(ex.getRequestMethod())) {
                sendError(ex, 404, "NOT_FOUND", "接口不存在");
                return;
            }
            CharacterDefinition character = startRun(ex, readBody(ex));
            if (character == null) {
                return;
            }
            sendJson(ex, 200, GameStateJson.runJson(
                    controller.getPhase().name(), characterName, runState));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(ex, 500, "INTERNAL_ERROR", "服务器内部错误");
        }
    }

    /**
     * 按请求体创建（或重开）本局，供 {@code /game/start} 与 {@code /runs} 共用。
     *
     * <p>可重复调用以重开：重新构造 RunState + GameController，丢弃上一局。</p>
     *
     * @return 选中角色；失败时已写出错误响应并返回 {@code null}
     */
    private CharacterDefinition startRun(HttpExchange ex, String body) throws IOException {
        String characterId = Json.field(body, "characterId");
        if (characterId == null || characterId.isBlank()) {
            sendError(ex, 400, "INVALID_CHARACTER", "缺少 characterId");
            return null;
        }

        CharacterDefinition character;
        try {
            character = catalog.getById(characterId);
        } catch (IllegalArgumentException exception) {
            sendError(ex, 400, "INVALID_CHARACTER", exception.getMessage());
            return null;
        }

        Long seedValue = Json.longField(body, "seed");
        long seed = seedValue == null ? ThreadLocalRandom.current().nextLong() : seedValue;
        Long actCountValue = Json.longField(body, "actCount");
        int actCount = actCountValue == null ? DEFAULT_ACT_COUNT : actCountValue.intValue();
        if (actCount <= 0) {
            sendError(ex, 400, "INVALID_ACT_COUNT", "章节数量必须大于 0");
            return null;
        }

        this.runState = RunFactory.createRun(catalog, character.id(), seed, actCount);
        this.characterName = character.name();
        // 奖励卡池按角色区分（角色专属牌 + 公共无色牌，含锻造牌）。
        this.controller = new GameController(
                runState,
                CardLibrary.rewardPoolFor(character.rewardCardIds()),
                System.out::println);
        this.battleId = null;
        return character;
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

    // ============ 开局房间 ============

    private void handleBlessing(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            if ("/api/v1/blessing".equals(path) && "GET".equals(ex.getRequestMethod())) {
                if (requireController(ex) == null) {
                    return;
                }
                sendJson(ex, 200, blessingStateJson());
                return;
            }
            if ("/api/v1/blessing/choose".equals(path) && "POST".equals(ex.getRequestMethod())) {
                handleChooseBlessing(ex);
                return;
            }
            sendError(ex, 404, "NOT_FOUND", "接口不存在");
        } catch (Exception e) {
            e.printStackTrace();
            sendError(ex, 500, "INTERNAL_ERROR", "服务器内部错误");
        }
    }

    private String blessingStateJson() {
        if (controller.getCurrentBlessingOptions().isEmpty()) {
            return GameStateJson.emptyBlessingJson();
        }
        return GameStateJson.blessingJson(
                BlessingService.TITLE,
                BlessingService.DESCRIPTION,
                controller.isBlessingAwaitingCard(),
                controller.isBlessingResolved(),
                controller.getChosenBlessingOptionId(),
                controller.getBlessingResultMessage(),
                controller.getCurrentBlessingOptions(),
                controller.getBlessingTargetCards());
    }

    private void handleChooseBlessing(HttpExchange ex) throws IOException {
        if (requireController(ex) == null) {
            return;
        }
        if (controller.getPhase() != GamePhase.BLESSING) {
            sendError(ex, 400, "WRONG_PHASE", "当前不是开局房间阶段");
            return;
        }
        String body = readBody(ex);
        String optionId = Json.field(body, "optionId");
        String cardId = Json.field(body, "cardId");
        if (optionId == null || optionId.isBlank()) {
            sendError(ex, 400, "INVALID_OPTION", "缺少 optionId");
            return;
        }
        try {
            BlessingActionResult result = controller.chooseBlessing(optionId);
            if (result.needsCard()) {
                if (cardId == null || cardId.isBlank()) {
                    sendJson(ex, 200, blessingStateJson());
                    return;
                }
                result = controller.chooseBlessingCard(cardId);
            }
            if (!result.succeeded() && !result.needsCard()) {
                sendError(ex, 400, result.status().name(), result.message());
                return;
            }
            sendJson(ex, 200, GameStateJson.runJson(
                    controller.getPhase().name(), characterName, runState));
        } catch (IllegalArgumentException | IllegalStateException e) {
            sendError(ex, 400, "INVALID_OPTION", e.getMessage());
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
            // 无待领取奖励：无操作，返回空奖励
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

    // 原来这里有一段按 item.kind() 手动结算购买效果的 switch（CARD/RELIC/POTION/SERVICE），
    // 那是 dev 把商店换成 ShopService 之前的写法。现在购买统一走
    // `controller.buyShopItem(itemId)`（见上面的 handleBuy），效果在 ShopService 里结算，
    // 这里不再需要。遗物/药水的支持由 ShopService + ShopItem 提供（#100 加的）。
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
                if (requireController(ex) == null) {
                    return;
                }
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
                    } else if ("GET".equals(method) && "piles".equals(action)) {
                        handlePiles(ex, id);
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

    /** 抽牌堆 / 弃牌堆的牌面内容，供战斗界面点开堆时按需拉取（战斗状态里只有数量）。 */
    private void handlePiles(HttpExchange ex, String id) throws IOException {
        Combat combat = requireCombat(ex, id);
        if (combat == null) {
            return;
        }
        sendJson(ex, 200, BattleStateJson.pilesDetailJson(combat));
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

    private String mapStateJson() {
        // 带上当前章号：前端要在地图左上角显示「第一层 / 第二层」。
        // 调用点都先过了 requireController，而 controller 与 runState 在 handleStart 里是一起赋的，
        // 所以这里 runState 必定非空（真为空也该让它抛出来，别用默认值把 bug 盖掉）。
        return GameStateJson.mapJson(controller.getMapService(), runState.getCurrentAct());
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
