package com.syncwatch.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the whole application on a random port and drives it like a real client:
 * REST for rooms, STOMP over WebSocket for synchronized playback.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"rezka.service.enabled=false", "rezka.feature.enabled=false"})
@AutoConfigureTestRestTemplate
class SyncFlowIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    private WebSocketStompClient stomp;
    private StompSession session;

    @BeforeEach
    void setUp() {
        stomp = new WebSocketStompClient(new StandardWebSocketClient());
        stomp.setMessageConverter(new JacksonJsonMessageConverter());
    }

    @AfterEach
    void tearDown() {
        if (session != null && session.isConnected()) session.disconnect();
        stomp.stop();
    }

    // ── REST ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("health endpoint reports HEALTHY")
    void health() {
        ResponseEntity<Map> response = rest.getForEntity("/api/health", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "HEALTHY");
    }

    @Test
    @DisplayName("a created room can be fetched and an unknown one returns 404")
    void roomLifecycle() {
        ResponseEntity<Map> created = rest.postForEntity("/api/rooms", null, Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        String id = (String) created.getBody().get("id");

        assertThat(rest.getForEntity("/api/rooms/" + id, Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rest.getForEntity("/api/rooms/missing", Map.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("config exposes the feature flag to the UI")
    void configEndpoint() {
        ResponseEntity<Map> response = rest.getForEntity("/api/config", Map.class);

        assertThat(response.getBody()).containsEntry("rezkaEnabled", false);
    }

    // ── WebSocket: synchronized start ─────────────────────────────────────────

    @Test
    @DisplayName("play triggers prepare, and go is broadcast once the client is ready")
    void synchronizedStart() throws Exception {
        String roomId = createRoom();
        BlockingQueue<Map<String, Object>> inbox = connectAndSubscribe(roomId);

        session.send("/app/room/" + roomId + "/event",
                Map.of("type", "play", "currentTime", 30.0, "senderId", "alice"));

        Map<String, Object> prepare = awaitMessage(inbox, "prepare");
        assertThat(((Number) prepare.get("position")).doubleValue()).isEqualTo(30.0);

        session.send("/app/room/" + roomId + "/event",
                Map.of("type", "ready", "currentTime", 30.0, "senderId", "alice"));

        Map<String, Object> go = awaitMessage(inbox, "go");
        assertThat(((Number) go.get("position")).doubleValue()).isEqualTo(30.0);

        Map<String, Object> clock = awaitMessage(inbox, "clock", m -> Boolean.TRUE.equals(m.get("playing")));
        assertThat(((Number) clock.get("position")).doubleValue()).isGreaterThanOrEqualTo(30.0);
    }

    @Test
    @DisplayName("pause is broadcast as a paused clock at the given position")
    void pauseBroadcastsClock() throws Exception {
        String roomId = createRoom();
        BlockingQueue<Map<String, Object>> inbox = connectAndSubscribe(roomId);

        session.send("/app/room/" + roomId + "/event",
                Map.of("type", "pause", "currentTime", 12.5, "senderId", "alice"));

        Map<String, Object> clock = awaitMessage(inbox, "clock", m -> Boolean.FALSE.equals(m.get("playing")));
        assertThat(((Number) clock.get("position")).doubleValue()).isEqualTo(12.5);
    }

    @Test
    @DisplayName("a chat message is broadcast and stored in the room history")
    void chat() throws Exception {
        String roomId = createRoom();
        BlockingQueue<Map<String, Object>> inbox = connectAndSubscribe(roomId);

        session.send("/app/room/" + roomId + "/event",
                Map.of("type", "chat", "senderId", "alice", "text", "hello"));

        Map<String, Object> message = awaitMessage(inbox, "chat");
        assertThat(message).containsEntry("text", "hello");

        ResponseEntity<Object[]> history = rest.getForEntity("/api/rooms/" + roomId + "/chat", Object[].class);
        assertThat(history.getBody()).hasSize(1);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String createRoom() {
        return (String) rest.postForEntity("/api/rooms", null, Map.class).getBody().get("id");
    }

    private BlockingQueue<Map<String, Object>> connectAndSubscribe(String roomId) throws Exception {
        BlockingQueue<Map<String, Object>> inbox = new LinkedBlockingQueue<>();
        session = stomp.connectAsync("ws://localhost:" + port + "/ws/websocket",
                new StompSessionHandlerAdapter() { }).get(10, TimeUnit.SECONDS);
        session.subscribe("/topic/room." + roomId, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders headers, Object payload) {
                inbox.add((Map<String, Object>) payload);
            }
        });
        awaitSubscriptionActive(roomId, inbox);
        return inbox;
    }

    /**
     * The server announces a new participant before the broker has finished registering the
     * subscription, so waiting for that announcement is racy on a slow machine. Instead, send
     * presence heartbeats until the lobby update comes back: that proves messages sent to the
     * room reach this client.
     */
    private void awaitSubscriptionActive(String roomId, BlockingQueue<Map<String, Object>> inbox)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            session.send("/app/room/" + roomId + "/event",
                    Map.of("type", "presence", "senderId", "probe", "quality", "good"));
            Map<String, Object> message = inbox.poll(250, TimeUnit.MILLISECONDS);
            if (message != null && "lobby".equals(message.get("type"))) {
                inbox.clear();
                return;
            }
        }
        throw new AssertionError("The subscription to room " + roomId + " did not become active within 10 seconds");
    }

    private static Map<String, Object> awaitMessage(BlockingQueue<Map<String, Object>> inbox, String type)
            throws InterruptedException {
        return awaitMessage(inbox, type, m -> true);
    }

    private static Map<String, Object> awaitMessage(BlockingQueue<Map<String, Object>> inbox, String type,
                                                    java.util.function.Predicate<Map<String, Object>> filter)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            Map<String, Object> message = inbox.poll(200, TimeUnit.MILLISECONDS);
            if (message != null && type.equals(message.get("type")) && filter.test(message)) {
                return message;
            }
        }
        throw new AssertionError("No '" + type + "' message received within 10 seconds");
    }
}
