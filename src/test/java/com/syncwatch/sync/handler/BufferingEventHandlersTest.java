package com.syncwatch.sync.handler;

import com.syncwatch.messaging.RoomEventPublisher;
import com.syncwatch.model.BufferingEvent;
import com.syncwatch.model.ClockEvent;
import com.syncwatch.model.EventType;
import com.syncwatch.model.PlayerEvent;
import com.syncwatch.service.RoomService;
import com.syncwatch.service.SourceCacheService;
import com.syncwatch.sync.RoomBroadcaster;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class BufferingEventHandlersTest {

    private static final String ROOM = "room";

    private RoomEventPublisher publisher;
    private RoomService rooms;
    private BufferingStartEventHandler start;
    private BufferingEndEventHandler end;

    @BeforeEach
    void setUp() {
        publisher = mock(RoomEventPublisher.class);
        rooms = new RoomService(new SourceCacheService());
        RoomBroadcaster broadcaster = new RoomBroadcaster(publisher);
        start = new BufferingStartEventHandler(rooms, broadcaster);
        end = new BufferingEndEventHandler(rooms, broadcaster);
    }

    private List<Object> published() {
        ArgumentCaptor<Object> payloads = ArgumentCaptor.forClass(Object.class);
        verify(publisher, atLeastOnce()).publish(eq(ROOM), payloads.capture());
        return payloads.getAllValues();
    }

    @Test
    @DisplayName("handlers are registered for the buffering event types")
    void types() {
        assertThat(start.type()).isEqualTo(EventType.BUFFERING_START);
        assertThat(end.type()).isEqualTo(EventType.BUFFERING_END);
    }

    @Test
    @DisplayName("start while playing freezes the clock and reports the buffering count")
    void startFreezesClock() {
        rooms.clockPlay(ROOM, 20.0);

        start.handle(ROOM, new PlayerEvent(), "session-1");

        assertThat(published()).anySatisfy(p ->
                assertThat(p).isInstanceOfSatisfying(ClockEvent.class, c -> assertThat(c.isPlaying()).isFalse()));
        assertThat(published()).anySatisfy(p ->
                assertThat(p).isInstanceOfSatisfying(BufferingEvent.class, b -> assertThat(b.getCount()).isEqualTo(1)));
    }

    @Test
    @DisplayName("end resumes the clock once nobody is buffering")
    void endResumesClock() {
        rooms.clockPlay(ROOM, 20.0);
        start.handle(ROOM, new PlayerEvent(), "session-1");

        end.handle(ROOM, new PlayerEvent(), "session-1");

        assertThat(rooms.getClock(ROOM)).get().satisfies(c -> assertThat(c.isPlaying()).isTrue());
        assertThat(published()).anySatisfy(p ->
                assertThat(p).isInstanceOfSatisfying(BufferingEvent.class, b -> assertThat(b.getCount()).isZero()));
    }
}
