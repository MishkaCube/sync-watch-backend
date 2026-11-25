package com.syncwatch.sync.handler;

import com.syncwatch.messaging.RoomEventPublisher;
import com.syncwatch.model.EventType;
import com.syncwatch.model.PlayerEvent;
import com.syncwatch.model.RoomClock;
import com.syncwatch.service.BufferingCoordinator;
import com.syncwatch.service.PlaybackBarrierService;
import com.syncwatch.service.RoomService;
import com.syncwatch.sync.PlayerEventHandler;
import com.syncwatch.sync.RoomBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SourceResetEventHandler implements PlayerEventHandler {

    private final RoomService roomService;
    private final PlaybackBarrierService barrier;
    private final BufferingCoordinator buffering;
    private final RoomEventPublisher publisher;
    private final RoomBroadcaster broadcaster;

    @Override
    public EventType type() {
        return EventType.SOURCE_RESET;
    }

    @Override
    public void handle(String roomId, PlayerEvent event, String sessionId) {
        barrier.cancel(roomId);
        RoomClock clock = roomService.resetSource(roomId);
        buffering.reset(roomId);
        publisher.publish(roomId, event);
        broadcaster.clock(roomId, clock);
        broadcaster.buffering(roomId, 0);   // clear any stuck buffering overlay
        log.info("Room {} source reset", roomId);
    }
}
