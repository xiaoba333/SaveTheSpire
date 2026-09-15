package com.roguelike.dungeon.http;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameServerEventTest {

    private static final Pattern NODE = Pattern.compile(
            "\"id\":\"(\\d+)\",\"type\":\"(\\w+)\",\"column\":-?\\d+,\"row\":-?\\d+,\"state\":\"(\\w+)\"");
    private static final Pattern BATTLE_ID = Pattern.compile("\"battleId\":\"([^\"]+)\"");
    private static final Pattern BATTLE_RESULT = Pattern.compile("\"result\":\"([A-Z]+)\"");
    private static final Pattern HAND_CARD_ID = Pattern.compile("\"id\":\"([^\"]+)\"");

    private GameServer server;
    private HttpClient client;
    private String baseUrl;

    @BeforeEach
    void startServer() throws Exception {
        server = new GameServer(0);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getPort() + "/api/v1";
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    }

    @AfterEach
    void stopServer() {
        if (server != null) server.stop();
    }

    @Test
    void eventEndpointShouldExposeChoicesWithUnavailableReason() throws Exception {
        advanceToEventNode();

        HttpResponse<String> event = get("/event");

        assertEquals(200, event.statusCode());
        assertTrue(event.body().contains("\"choices\":["));
        assertTrue(event.body().contains("\"unavailableReason\":"));
    }

    @Test
    void chooseEventShouldReturnResultMessageAndResolveTheEvent() throws Exception {
        advanceToEventNode();

        HttpResponse<String> chosen = post("/event/choose", "{\"choiceId\":\"leave\"}");

        assertEquals(200, chosen.statusCode());
        assertTrue(chosen.body().contains("\"success\":true"));
        assertTrue(chosen.body().contains("\"status\":\"SUCCESS\""));
        assertTrue(chosen.body().contains("\"message\":\""));

        // 同一事件重复选择应失败：结算后阶段已切回 MAP，事件不再可提交
        HttpResponse<String> again = post("/event/choose", "{\"choiceId\":\"leave\"}");
        assertEquals(400, again.statusCode());
        assertTrue(again.body().contains("INVALID_CHOICE"));
    }

    /**
     * 开局后沿地图一路推进，直到进入一个事件节点。
     *
     * <p>地图生成器把第 0 层（入口）硬编码为普通战斗（见 MapGenerator.chooseType），
     * 所以开局快照里永远不会有「可选的事件节点」——需要走战斗/篝火/商店逐层推进，
     * 直到事件节点进入可选状态。这里优先走事件，其次篝火/商店（无需战斗），
     * 最后才打普通战斗，从而用最少的战斗到达事件。</p>
     */
    private void advanceToEventNode() throws Exception {
        post("/runs", "{\"characterId\":\"warrior\",\"seed\":20260915,\"actCount\":1}");
        String blessing = get("/blessing").body();
        String optionId = firstInstantBlessingId(blessing);
        post("/blessing/choose", "{\"optionId\":\"" + optionId + "\"}");

        for (int step = 0; step < 20; step++) {
            String map = get("/map").body();
            Node node = pickNextNode(map);
            if (node == null) {
                throw new AssertionError("地图没有可选节点：" + map);
            }
            // 注意：后端 handleAdvance 用 Json.field 解析字符串字段，nodeId 必须是 "5" 而非 5。
            HttpResponse<String> advance = post("/map/advance", "{\"nodeId\":\"" + node.id() + "\"}");
            assertEquals(200, advance.statusCode(), "进入节点失败：" + advance.body());
            if ("EVENT".equals(node.type())) {
                return;
            }
            resolveNode(node.type());
        }
        throw new AssertionError("推进 20 步仍未到达事件节点");
    }

    private record Node(String id, String type) {
    }

    /** 在可选节点里按「事件 > 篝火/商店 > 普通战斗 > 其余」的优先级挑一个。 */
    private static Node pickNextNode(String map) {
        List<Node> selectable = new ArrayList<>();
        Matcher matcher = NODE.matcher(map);
        while (matcher.find()) {
            if ("SELECTABLE".equals(matcher.group(3))) {
                selectable.add(new Node(matcher.group(1), matcher.group(2)));
            }
        }
        for (Node node : selectable) {
            if ("EVENT".equals(node.type())) return node;
        }
        for (Node node : selectable) {
            if ("REST".equals(node.type()) || "SHOP".equals(node.type())) return node;
        }
        for (Node node : selectable) {
            if ("BATTLE".equals(node.type())) return node;
        }
        return selectable.isEmpty() ? null : selectable.get(0);
    }

    /** 结算刚进入的非事件节点，把地图推回可选状态。 */
    private void resolveNode(String type) throws Exception {
        switch (type) {
            case "REST" -> post("/campfire/act", "{\"actionId\":\"leave\"}");
            case "SHOP" -> post("/shop/leave", "{}");
            default -> {
                winBattle();
                post("/reward/select", "{}");
            }
        }
    }

    /** 自动打完当前战斗：每回合打出全部手牌后结束回合，直到胜利。 */
    private void winBattle() throws Exception {
        HttpResponse<String> started = post("/battles", "{}");
        assertEquals(200, started.statusCode(), "开战失败：" + started.body());
        Matcher idMatcher = BATTLE_ID.matcher(started.body());
        assertTrue(idMatcher.find(), "开战响应缺少 battleId：" + started.body());
        String battleId = idMatcher.group(1);

        for (int turn = 0; turn < 50; turn++) {
            String state = get("/battles/" + battleId).body();
            Matcher stateResult = BATTLE_RESULT.matcher(state);
            if (stateResult.find()) {
                assertEquals("VICTORY", stateResult.group(1), "玩家战败：" + state);
                return;
            }
            for (String cardId : handCardIds(state)) {
                HttpResponse<String> play = post("/battles/" + battleId + "/play",
                        "{\"cardId\":\"" + cardId + "\"}");
                if (play.statusCode() == 200) {
                    Matcher playResult = BATTLE_RESULT.matcher(play.body());
                    if (playResult.find()) {
                        assertEquals("VICTORY", playResult.group(1), "玩家战败：" + play.body());
                        return;
                    }
                }
            }
            post("/battles/" + battleId + "/end-turn", "{}");
        }
        throw new AssertionError("战斗未在限定回合内结束");
    }

    /** 从战斗状态 JSON 的 hand 数组里提取每张手牌的实例 id（按顺序）。 */
    private static List<String> handCardIds(String state) {
        int start = state.indexOf("\"hand\":[");
        int end = state.indexOf("],\"piles\":", start);
        if (start < 0 || end < 0 || end <= start) {
            return List.of();
        }
        String hand = state.substring(start + 8, end);
        List<String> ids = new ArrayList<>();
        Matcher matcher = HAND_CARD_ID.matcher(hand);
        while (matcher.find()) {
            ids.add(matcher.group(1));
        }
        return ids;
    }

    private static String firstInstantBlessingId(String json) {
        for (String id : List.of("gold", "max_hp", "damage_gold")) {
            if (json.contains("\"id\":\"" + id + "\"")) return id;
        }
        throw new IllegalStateException("开局房间没有即时选项：" + json);
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
