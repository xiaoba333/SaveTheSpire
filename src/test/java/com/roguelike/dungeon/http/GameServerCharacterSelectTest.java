package com.roguelike.dungeon.http;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameServerCharacterSelectTest {

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
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void charactersEndpointListsWarriorAndBlood() throws Exception {
        HttpResponse<String> response = get("/characters");

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"id\":\"warrior\""));
        assertTrue(response.body().contains("铁血战士"));
        assertTrue(response.body().contains("\"id\":\"blood\""));
        assertTrue(response.body().contains("血祭者"));
        assertTrue(!response.body().contains("\"id\":\"god\""));
    }

    @Test
    void mapRequiresAnActiveRun() throws Exception {
        HttpResponse<String> response = get("/map");

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("NO_ACTIVE_RUN"));
    }

    @Test
    void creatingWarriorRunReturnsMapPhaseAndStartingStats() throws Exception {
        HttpResponse<String> created = post("/runs",
                "{\"characterId\":\"warrior\",\"seed\":12345,\"actCount\":1}");

        assertEquals(200, created.statusCode());
        assertTrue(created.body().contains("\"phase\":\"MAP\""));
        assertTrue(created.body().contains("铁血战士"));
        assertTrue(created.body().contains("\"hp\":50"));
        assertTrue(created.body().contains("燃烧之血"));

        HttpResponse<String> character = get("/character");
        assertEquals(200, character.statusCode());
        assertTrue(character.body().contains("铁血战士"));
        assertTrue(character.body().contains("\"gold\":0"));
    }

    @Test
    void unknownCharacterIsRejected() throws Exception {
        HttpResponse<String> response = post("/runs", "{\"characterId\":\"unknown\"}");

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("INVALID_CHARACTER"));
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
