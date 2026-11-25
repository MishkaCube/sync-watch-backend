package com.syncwatch.sync.handler;

import com.syncwatch.model.EventType;
import com.syncwatch.model.PlayerEvent;
import com.syncwatch.model.RoomClock;
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
public class PauseEventHandler implements PlayerEventHandler {

    private final RoomService roomService;
    private final PlaybackBarrierService barrier;
    private final RoomBroadcaster broadcaster;

    @Override
    public EventType type() {
        return EventType.PAUSE;
    }

    @Override
    public void handle(String roomId, PlayerEvent event, String sessionId) {
        barrier.cancel(roomId);   // drop any pending start
        RoomClock clock = roomService.clockPause(roomId, event.getCurrentTime());
        broadcaster.clock(roomId, clock);
        log.info("Room {} clock PAUSE at {}s", roomId, event.getCurrentTime());
    }
}
