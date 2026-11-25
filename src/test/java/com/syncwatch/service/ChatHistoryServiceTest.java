package com.syncwatch.service;

import com.syncwatch.model.ChatMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatHistoryServiceTest {

    private final ChatHistoryService chat = new ChatHistoryService();

    @Test
    @DisplayName("an unknown room has an empty history")
    void emptyHistory() {
        assertThat(chat.getRecent("nowhere")).isEmpty();
    }

    @Test
    @DisplayName("messages are returned in the order they were sent")
    void keepsOrder() {
        chat.add("room", new ChatMessage("a", "first", 1L));
        chat.add("room", new ChatMessage("b", "second", 2L));

        List<ChatMessage> recent = chat.getRecent("room");

        assertThat(recent).extracting(ChatMessage::getText).containsExactly("first", "second");
    }

    @Test
    @DisplayName("only the 20 most recent messages are kept")
    void keepsLastTwenty() {
        for (int i = 1; i <= 25; i++) {
            chat.add("room", new ChatMessage("a", "m" + i, i));
        }

        List<ChatMessage> recent = chat.getRecent("room");

        assertThat(recent).hasSize(20);
        assertThat(recent.get(0).getText()).isEqualTo("m6");
        assertThat(recent.get(19).getText()).isEqualTo("m25");
    }

    @Test
    @DisplayName("rooms do not share history")
    void isolatesRooms() {
        chat.add("room-a", new ChatMessage("a", "hello", 1L));

        assertThat(chat.getRecent("room-b")).isEmpty();
    }
}
