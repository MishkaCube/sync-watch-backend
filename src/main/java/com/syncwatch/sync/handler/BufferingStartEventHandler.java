package com.syncwatch.sync.handler;

import com.syncwatch.model.EventType;
import com.syncwatch.model.PlayerEvent;
import com.syncwatch.service.BufferingCoordinator;
import com.syncwatch.service.BufferingCoordinator.BufferingResult;
import com.syncwatch.sync.PlayerEventHandler;
import com.syncwatch.sync.RoomBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BufferingStartEventHandler implements PlayerEventHandler {

    private final BufferingCoordinator buffering;
    private final RoomBroadcaster broadcaster;

    @Override
    public EventType type() {
        return EventType.BUFFERING_START;
    }

    @Override
    public void handle(String roomId, PlayerEvent event, String sessionId) {
        // keyed by WebSocket session so the hold can be released on disconnect
        BufferingResult result = buffering.start(roomId, sessionId);
        if (result == null) return;
        if (result.clockChanged()) broadcaster.clock(roomId, result.clock());
        broadcaster.buffering(roomId, result.count());
        log.info("Room {} buffering START: {} client(s) buffering", roomId, result.count());
    }
}
