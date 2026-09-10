package com.roguelike.dungeon.http;

import com.roguelike.dungeon.game.battle.Combat;
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
 * 战斗 HTTP 服务器：把后端 {@link Combat} 包装成 http-api.md v1.0 的 4 个端点。
 *
 * <p>后端权威：所有战斗逻辑都在 {@link Combat} 里算，这里只做「收请求 → 调 Combat → 返回完整 BattleState」。
 * {@link Combat} 非线程安全，用单线程 executor 串行化所有请求。</p>
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
        // 独立战斗：新玩家 + 默认牌组，与 http-api.md「开始一场新战斗」语义一致。
        Combat combat = new Combat(System.out::println);
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

    private void handlePlay(HttpExchange ex, String id) throws IOException {
        Battle battle = requireBattle(ex, id);
        if (battle == null) {
            return;
        }
        battle.lastAccess = System.currentTimeMillis();

        String cardId = Json.field(readBody(ex), "cardId");
        Combat.PlayCardResult result = battle.combat.playCard(cardId);
        if (result == Combat.PlayCardResult.SUCCESS) {
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

    private static String messageFor(Combat.PlayCardResult result) {
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
