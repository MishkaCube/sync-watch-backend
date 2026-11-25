package com.syncwatch.sync;

import com.syncwatch.messaging.RoomEventPublisher;
import com.syncwatch.model.BufferingEvent;
import com.syncwatch.model.ClockEvent;
import com.syncwatch.model.RoomClock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Builds and publishes the events that describe a room's shared playback state. */
@Component
@RequiredArgsConstructor
public class RoomBroadcaster {

    private final RoomEventPublisher publisher;

    public void clock(String roomId, RoomClock clock) {
        if (clock == null) return;
        publisher.publish(roomId, ClockEvent.from(clock));
    }

    public void buffering(String roomId, int count) {
        publisher.publish(roomId, new BufferingEvent(count));
    }
}
