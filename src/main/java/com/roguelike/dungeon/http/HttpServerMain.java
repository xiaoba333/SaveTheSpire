package com.roguelike.dungeon.http;

import java.io.IOException;

/**
 * 后端 HTTP 服务器入口。
 *
 * <p>端口通过系统属性 {@code port}（或环境变量 {@code PORT}）配置，默认 8080，不写死。</p>
 *
 * <p>运行方式：</p>
 * <pre>
 *   .\mvnw.cmd -q compile
 *   java -cp target/classes com.roguelike.dungeon.http.HttpServerMain
 *   # 指定端口：
 *   java -Dport=9000 -cp target/classes com.roguelike.dungeon.http.HttpServerMain
 * </pre>
 */
public final class HttpServerMain {

    public static void main(String[] args) throws IOException {
        int port = resolvePort();
        BattleServer server = new BattleServer(port);
        server.start();
        System.out.println("SaveTheSpire 后端 HTTP 已启动：http://localhost:"
                + port + "/api/v1/battles");
    }

    private static int resolvePort() {
        String value = System.getProperty("port");
        if (value == null || value.isBlank()) {
            value = System.getenv("PORT");
        }
        if (value == null || value.isBlank()) {
            return 8080;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            System.out.println("无效端口 " + value + "，回退到 8080");
            return 8080;
        }
    }
}
