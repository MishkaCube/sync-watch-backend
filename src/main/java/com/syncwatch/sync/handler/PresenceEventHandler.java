package com.syncwatch.sync.handler;

import com.syncwatch.messaging.RoomEventPublisher;
import com.syncwatch.model.EventType;
import com.syncwatch.model.LobbyEvent;
import com.syncwatch.model.PlayerEvent;
import com.syncwatch.service.PresenceService;
import com.syncwatch.sync.PlayerEventHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Heartbeat with the client's connection quality; broadcasts the live lobby list. */
@Component
@RequiredArgsConstructor
public class PresenceEventHandler implements PlayerEventHandler {

    private final PresenceService presence;
    private final RoomEventPublisher publisher;

    @Override
    public EventType type() {
        return EventType.PRESENCE;
    }

    @Override
    public void handle(String roomId, PlayerEvent event, String sessionId) {
        var users = presence.update(roomId, event.getSenderId(), event.getQuality());
        publisher.publish(roomId, new LobbyEvent(users));
    }
}
