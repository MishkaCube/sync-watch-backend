package com.syncwatch.sync.handler;

import com.syncwatch.messaging.RoomEventPublisher;
import com.syncwatch.model.ChatMessage;
import com.syncwatch.model.EventType;
import com.syncwatch.model.PlayerEvent;
import com.syncwatch.service.ChatHistoryService;
import com.syncwatch.sync.PlayerEventHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatEventHandler implements PlayerEventHandler {

    static final int MAX_LENGTH = 500;

    private final ChatHistoryService chatHistory;
    private final RoomEventPublisher publisher;

    @Override
    public EventType type() {
        return EventType.CHAT;
    }

    @Override
    public void handle(String roomId, PlayerEvent event, String sessionId) {
        String text = event.getText();
        if (text == null || text.isBlank()) return;

        // cap the length and strip; the client escapes on render so there is no XSS
        String safe = text.strip();
        if (safe.length() > MAX_LENGTH) safe = safe.substring(0, MAX_LENGTH);

        ChatMessage message = new ChatMessage(event.getSenderId(), safe, System.currentTimeMillis());
        chatHistory.add(roomId, message);
        publisher.publish(roomId, message);
    }
}
