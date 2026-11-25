package com.syncwatch.sync.handler;

import com.syncwatch.messaging.RoomEventPublisher;
import com.syncwatch.model.ChatMessage;
import com.syncwatch.model.EventType;
import com.syncwatch.model.PlayerEvent;
import com.syncwatch.service.ChatHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ChatEventHandlerTest {

    private RoomEventPublisher publisher;
    private ChatHistoryService history;
    private ChatEventHandler handler;

    @BeforeEach
    void setUp() {
        publisher = mock(RoomEventPublisher.class);
        history = new ChatHistoryService();
        handler = new ChatEventHandler(history, publisher);
    }

    private PlayerEvent chat(String text) {
        PlayerEvent e = new PlayerEvent();
        e.setType(EventType.CHAT);
        e.setSenderId("alice");
        e.setText(text);
        return e;
    }

    @Test
    @DisplayName("handles chat events")
    void handlesChat() {
        assertThat(handler.type()).isEqualTo(EventType.CHAT);
    }

    @Test
    @DisplayName("a message is trimmed, stored and broadcast")
    void storesAndBroadcasts() {
        handler.handle("room", chat("  hello  "), "s1");

        assertThat(history.getRecent("room")).singleElement().extracting(ChatMessage::getText).isEqualTo("hello");
        ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
        verify(publisher).publish(eq("room"), sent.capture());
        assertThat(sent.getValue()).isInstanceOfSatisfying(ChatMessage.class, m -> {
            assertThat(m.getText()).isEqualTo("hello");
            assertThat(m.getSenderId()).isEqualTo("alice");
        });
    }

    @Test
    @DisplayName("blank and missing text is ignored")
    void ignoresBlank() {
        handler.handle("room", chat("   "), "s1");
        handler.handle("room", chat(null), "s1");

        assertThat(history.getRecent("room")).isEmpty();
        verify(publisher, never()).publish(any(), any());
    }

    @Test
    @DisplayName("long messages are cut to 500 characters")
    void capsLength() {
        handler.handle("room", chat("x".repeat(900)), "s1");

        assertThat(history.getRecent("room")).singleElement()
                .extracting(m -> m.getText().length()).isEqualTo(ChatEventHandler.MAX_LENGTH);
    }
}
