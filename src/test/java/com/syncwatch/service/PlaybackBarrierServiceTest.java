package com.syncwatch.service;

import com.syncwatch.messaging.RoomEventPublisher;
import com.syncwatch.model.ClockEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * The barrier makes everyone start together: prepare (seek + prebuffer) -> ready -> go.
 */
class PlaybackBarrierServiceTest {

    private static final String ROOM = "room-1";

    private RoomEventPublisher publisher;
    private RoomService rooms;
    private ParticipantService participants;
    private PlaybackBarrierService barrier;

    @BeforeEach
    void setUp() {
        publisher = mock(RoomEventPublisher.class);
        rooms = new RoomService(new SourceCacheService());
        participants = new ParticipantService();
        barrier = new PlaybackBarrierService(publisher, rooms, participants);
    }

    private List<Object> sentToRoom() {
        ArgumentCaptor<Object> payloads = ArgumentCaptor.forClass(Object.class);
        verify(publisher, org.mockito.Mockito.atLeast(0)).publish(eq(ROOM), payloads.capture());
        return payloads.getAllValues();
    }

    private boolean sentGo() {
        return sentToRoom().stream()
                .anyMatch(p -> p instanceof Map<?, ?> m && "go".equals(m.get("type")));
    }

    @Test
    @DisplayName("requestStart pauses the clock at the target and asks everyone to prepare")
    void requestStartSendsPrepare() {
        participants.join("s1", ROOM);

        barrier.requestStart(ROOM, 30.0);

        assertThat(rooms.getClock(ROOM)).get().satisfies(clock -> {
            assertThat(clock.isPlaying()).isFalse();
            assertThat(clock.getPosition()).isEqualTo(30.0);
        });
        assertThat(sentToRoom()).anySatisfy(p -> {
            assertThat(p).isInstanceOf(Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> message = (Map<String, Object>) p;
            assertThat(message).containsEntry("type", "prepare").containsEntry("position", 30.0);
        });
        assertThat(sentGo()).isFalse();
    }

    @Test
    @DisplayName("go is sent only after every participant is ready")
    void goAfterAllReady() {
        participants.join("s1", ROOM);
        participants.join("s2", ROOM);
        barrier.requestStart(ROOM, 30.0);

        barrier.markReady(ROOM, "alice");
        assertThat(sentGo()).as("one of two ready").isFalse();

        barrier.markReady(ROOM, "bob");
        assertThat(sentGo()).as("both ready").isTrue();
        assertThat(rooms.getClock(ROOM)).get().satisfies(clock -> {
            assertThat(clock.isPlaying()).isTrue();
            assertThat(clock.getPosition()).isEqualTo(30.0);
        });
    }

    @Test
    @DisplayName("the same client reporting ready twice is counted once")
    void duplicateReadyIsCountedOnce() {
        participants.join("s1", ROOM);
        participants.join("s2", ROOM);
        barrier.requestStart(ROOM, 10.0);

        barrier.markReady(ROOM, "alice");
        barrier.markReady(ROOM, "alice");

        assertThat(sentGo()).isFalse();
    }

    @Test
    @DisplayName("a single participant starts immediately once ready")
    void singleParticipant() {
        participants.join("s1", ROOM);
        barrier.requestStart(ROOM, 5.0);

        barrier.markReady(ROOM, "alice");

        assertThat(sentGo()).isTrue();
    }

    @Test
    @DisplayName("cancel drops the pending start so a late ready does nothing")
    void cancelDropsPendingStart() {
        participants.join("s1", ROOM);
        barrier.requestStart(ROOM, 30.0);

        barrier.cancel(ROOM);
        barrier.markReady(ROOM, "alice");

        assertThat(sentGo()).isFalse();
        assertThat(rooms.getClock(ROOM)).get().extracting(c -> c.isPlaying()).isEqualTo(false);
    }

    @Test
    @DisplayName("ready without a pending start is ignored")
    void readyWithoutPending() {
        barrier.markReady(ROOM, "alice");

        verify(publisher, never()).publish(eq(ROOM), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("go also publishes the updated clock to the room")
    void goPublishesClock() {
        participants.join("s1", ROOM);
        barrier.requestStart(ROOM, 12.0);

        barrier.markReady(ROOM, "alice");

        assertThat(sentToRoom()).anySatisfy(p ->
                assertThat(p).isInstanceOfSatisfying(ClockEvent.class, c -> assertThat(c.isPlaying()).isTrue()));
    }
}
