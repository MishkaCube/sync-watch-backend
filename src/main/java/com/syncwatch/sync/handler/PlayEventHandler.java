package com.syncwatch.sync.handler;

import com.syncwatch.model.EventType;
import com.syncwatch.model.PlayerEvent;
import com.syncwatch.service.PlaybackBarrierService;
import com.syncwatch.sync.PlayerEventHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Play starts a synchronized start through the readiness barrier (prepare, ready, go). */
@Component
@RequiredArgsConstructor
public class PlayEventHandler implements PlayerEventHandler {

    private final PlaybackBarrierService barrier;

    @Override
    public EventType type() {
        return EventType.PLAY;
    }

    @Override
    public void handle(String roomId, PlayerEvent event, String sessionId) {
        barrier.requestStart(roomId, event.getCurrentTime());
    }
}
