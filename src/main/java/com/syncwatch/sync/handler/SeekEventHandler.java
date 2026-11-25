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

/**
 * Seek is applied directly, without the barrier: a pause, prepare, play cycle
 * fights Safari's native fullscreen player and breaks seeking there. The other
 * clients catch up through drift correction; buffering coordination still kicks
 * in if someone actually stalls.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeekEventHandler implements PlayerEventHandler {

    private final RoomService roomService;
    private final PlaybackBarrierService barrier;
    private final RoomBroadcaster broadcaster;

    @Override
    public EventType type() {
        return EventType.SEEK;
    }

    @Override
    public void handle(String roomId, PlayerEvent event, String sessionId) {
        barrier.cancel(roomId);
        RoomClock clock = roomService.clockSeek(roomId, event.getCurrentTime());
        broadcaster.clock(roomId, clock);
        log.info("Room {} clock SEEK to {}s", roomId, event.getCurrentTime());
    }
}
