package com.roguelike.dungeon.http;

import com.roguelike.dungeon.game.battle.Combat;
import com.roguelike.dungeon.game.battle.CombatFactory;
import com.roguelike.dungeon.game.battle.MonsterCatalog;
import com.roguelike.dungeon.game.battle.PlayCardResult;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/**
 * 战斗 HTTP 服务器：把后端 {@link Combat} 包装成 http-api.md v1.0 的端点。
 *
 * <p>后端权威：所有战斗逻辑都在 {@link Combat} 里算，这里只做「收请求 → 调 Combat → 返回完整 BattleState」。
 * {@link Combat} 非线程安全，用单线程 executor 串行化所有请求。</p>
 *
 * <h2>多敌人相关</h2>
 *
 * <ul>
 *   <li>{@code POST /battles} 请求体可选 {@code {"encounterId":"act1_grubs"}}，
 *       用于直接开一场指定编队的战斗（联调多敌人用）；</li>
 *   <li>{@code POST /battles/{id}/target} 切换攻击目标；</li>
 *   <li>{@code POST /battles/{id}/play} 请求体可带 {@code targetIndex} / {@code targetId}，
 *       表示先切换目标再出这张牌。</li>
 * </ul>
 */
public final class BattleServer {

    private static final String PREFIX = "/api/v1/battles";

    /** 开发期 30 分钟无操作清理（对应 http-api.md 第 6 节第 7 条）。 */
    private static final long IDLE_TIMEOUT_MILLIS = 30 * 60 * 1000L;

    private final HttpServer server;
    private final Map<String, Battle> battles = new ConcurrentHashMap<>();

    /** 一场进行中的战斗及其最后访问时间。 */
    private static final class Battle {
        final String id;
        final Combat combat;
        volatile long lastAccess;

        Battle(String id, Combat combat) {
            this.id = id;
            this.combat = combat;
            this.lastAccess = System.currentTimeMillis();
        }
    }

    public BattleServer(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/v1/battles", this::handleBattle);
        server.setExecutor(Executors.newSingleThreadExecutor());
    }

    public void start() {
        server.start();
    }

    private void handleBattle(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            String method = ex.getRequestMethod();

            if (PREFIX.equals(path) && "POST".equals(method)) {
                handleStartBattle(ex);
                return;
            }

            String prefixWithSlash = PREFIX + "/";
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
                    } else if ("POST".equals(method) && "target".equals(action)) {
                        handleSelectTarget(ex, id);
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
        String id = UUID.randomUUID().toString();
        String body = readBody(ex);
        String encounterId = Json.field(body, "encounterId");
        // 独立战斗：新玩家 + 默认牌组，与 http-api.md「开始一场新战斗」语义一致。
        // 传入 encounterId 时按编队出场，方便前端联调多敌人与目标选择。
        Combat combat = encounterId == null || encounterId.isBlank()
                ? CombatFactory.createHttpDemo(System.out::println)
                : CombatFactory.createHttpDemo(
                        System.out::println, MonsterCatalog.encounter(encounterId));
        battles.put(id, new Battle(id, combat));
        sendState(ex, battles.get(id));
        pruneIdle();
    }

    private void handleGetState(HttpExchange ex, String id) throws IOException {
        Battle battle = requireBattle(ex, id);
        if (battle == null) {
            return;
        }
        battle.lastAccess = System.currentTimeMillis();
        // 查询不产生操作，newLogs 固定为空；也不 drain，避免吃掉下一次操作的增量日志。
        sendJson(ex, 200, BattleStateJson.toJson(battle.id, battle.combat, List.of()));
    }

    /** 切换攻击目标：{@code {"index":1}} 或 {@code {"monsterId":"grub"}}。 */
    private void handleSelectTarget(HttpExchange ex, String id) throws IOException {
        Battle battle = requireBattle(ex, id);
        if (battle == null) {
            return;
        }
        battle.lastAccess = System.currentTimeMillis();

        String body = readBody(ex);
        boolean ok;
        Long index = Json.longField(body, "index");
        String monsterId = Json.field(body, "monsterId");
        if (index != null) {
            ok = battle.combat.selectTarget(index.intValue());
        } else if (monsterId != null && !monsterId.isBlank()) {
            ok = battle.combat.selectTargetById(monsterId);
        } else {
            sendError(ex, 400, "INVALID_TARGET", "需要 index 或 monsterId");
            return;
        }

        if (ok) {
            sendState(ex, battle);
        } else {
            battle.combat.drainNewLogs();
            sendError(ex, 400, "INVALID_TARGET", "目标不存在或已阵亡");
        }
    }

    private void handlePlay(HttpExchange ex, String id) throws IOException {
        Battle battle = requireBattle(ex, id);
        if (battle == null) {
            return;
        }
        battle.lastAccess = System.currentTimeMillis();

        String body = readBody(ex);
        String cardId = Json.field(body, "cardId");
        String targetCardId = Json.field(body, "targetCardId");

        // 出牌前可选地切换目标：前端「直接把牌拖到某只怪身上」就靠这个字段。
        Long targetIndex = Json.longField(body, "targetIndex");
        String targetMonsterId = Json.field(body, "targetId");
        if (targetIndex != null) {
            if (!battle.combat.selectTarget(targetIndex.intValue())) {
                battle.combat.drainNewLogs();
                sendError(ex, 400, "INVALID_TARGET", "目标不存在或已阵亡");
                return;
            }
        } else if (targetMonsterId != null && !targetMonsterId.isBlank()) {
            if (!battle.combat.selectTargetById(targetMonsterId)) {
                battle.combat.drainNewLogs();
                sendError(ex, 400, "INVALID_TARGET", "目标不存在或已阵亡");
                return;
            }
        }

        PlayCardResult result = battle.combat.playCard(cardId, targetCardId);
        if (result == PlayCardResult.SUCCESS) {
            sendState(ex, battle);
        } else {
            // 失败操作不返回状态，丢弃其产生的日志，避免串到下一次成功响应的 newLogs 里。
            battle.combat.drainNewLogs();
            sendError(ex, 400, result.name(), messageFor(result));
        }
    }

    private void handleEndTurn(HttpExchange ex, String id) throws IOException {
        Battle battle = requireBattle(ex, id);
        if (battle == null) {
            return;
        }
        battle.lastAccess = System.currentTimeMillis();
        battle.combat.endPlayerTurn();
        sendState(ex, battle);
    }

    /** 按 battleId 查找；找不到时返回 null 并已写出 404。 */
    private Battle requireBattle(HttpExchange ex, String id) throws IOException {
        Battle battle = battles.get(id);
        if (battle == null) {
            sendError(ex, 404, "BATTLE_NOT_FOUND", "战斗不存在或已过期");
        }
        return battle;
    }

    private void sendState(HttpExchange ex, Battle battle) throws IOException {
        List<String> logs = battle.combat.drainNewLogs();
        sendJson(ex, 200, BattleStateJson.toJson(battle.id, battle.combat, logs));
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

    private void pruneIdle() {
        long now = System.currentTimeMillis();
        battles.entrySet().removeIf(e -> now - e.getValue().lastAccess > IDLE_TIMEOUT_MILLIS);
    }

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
