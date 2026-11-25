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
public class BufferingEndEventHandler implements PlayerEventHandler {

    private final BufferingCoordinator buffering;
    private final RoomBroadcaster broadcaster;

    @Override
    public EventType type() {
        return EventType.BUFFERING_END;
    }

    @Override
    public void handle(String roomId, PlayerEvent event, String sessionId) {
        BufferingResult result = buffering.end(roomId, sessionId);
        if (result == null) return;
        if (result.clockChanged()) broadcaster.clock(roomId, result.clock());
        broadcaster.buffering(roomId, result.count());
        log.info("Room {} buffering END: {} client(s) buffering", roomId, result.count());
    }
}
