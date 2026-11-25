package com.syncwatch.sync.handler;

import com.syncwatch.model.EventType;
import com.syncwatch.model.PlayerEvent;
import com.syncwatch.service.PlaybackBarrierService;
import com.syncwatch.sync.PlayerEventHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** A client reports it has buffered and is ready to start at the barrier position. */
@Component
@RequiredArgsConstructor
public class ReadyEventHandler implements PlayerEventHandler {

    private final PlaybackBarrierService barrier;

    @Override
    public EventType type() {
        return EventType.READY;
    }

    @Override
    public void handle(String roomId, PlayerEvent event, String sessionId) {
        barrier.markReady(roomId, event.getSenderId());
    }
}
