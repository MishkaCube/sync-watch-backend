package com.syncwatch.sync.handler;

import com.syncwatch.messaging.RoomEventPublisher;
import com.syncwatch.model.EventType;
import com.syncwatch.model.PlayerEvent;
import com.syncwatch.service.RoomService;
import com.syncwatch.sync.PlayerEventHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SourceChangeEventHandler implements PlayerEventHandler {

    private final RoomService roomService;
    private final RoomEventPublisher publisher;

    @Override
    public EventType type() {
        return EventType.SOURCE_CHANGE;
    }

    @Override
    public void handle(String roomId, PlayerEvent event, String sessionId) {
        roomService.updateLastSource(roomId, event);
        publisher.publish(roomId, event);
        log.info("Room {} source changed", roomId);
    }
}
