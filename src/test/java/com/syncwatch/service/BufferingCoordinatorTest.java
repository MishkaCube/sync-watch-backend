package com.syncwatch.service;

import com.syncwatch.service.BufferingCoordinator.BufferingResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class BufferingCoordinatorTest {

    private static final String ROOM = "room-1";

    private RoomService rooms;
    private BufferingCoordinator buffering;

    @BeforeEach
    void setUp() {
        RoomRepository repository = new InMemoryRoomRepository();
        rooms = new RoomService(repository, new SourceCacheService());
        buffering = new BufferingCoordinator(repository);
    }

    @Test
    @DisplayName("when a client starts buffering a playing clock is frozen")
    void freezesPlayingClock() {
        rooms.clockPlay(ROOM, 20.0);

        BufferingResult result = buffering.start(ROOM, "alice");

        assertThat(result.count()).isEqualTo(1);
        assertThat(result.clockChanged()).isTrue();
        assertThat(result.clock().isPlaying()).isFalse();
        assertThat(result.clock().getPosition()).isCloseTo(20.0, within(0.5));
    }

    @Test
    @DisplayName("buffering on a paused clock does not change the clock")
    void pausedClockIsUntouched() {
        rooms.clockPause(ROOM, 7.0);

        BufferingResult result = buffering.start(ROOM, "alice");

        assertThat(result.clockChanged()).isFalse();
        assertThat(result.clock().isPlaying()).isFalse();
    }

    @Test
    @DisplayName("playback resumes only after every buffering client has recovered")
    void resumesOnlyWhenAllRecovered() {
        rooms.clockPlay(ROOM, 20.0);
        buffering.start(ROOM, "alice");
        buffering.start(ROOM, "bob");

        BufferingResult first = buffering.end(ROOM, "alice");
        assertThat(first.count()).isEqualTo(1);
        assertThat(first.clockChanged()).isFalse();
        assertThat(first.clock().isPlaying()).isFalse();

        BufferingResult second = buffering.end(ROOM, "bob");
        assertThat(second.count()).isZero();
        assertThat(second.clockChanged()).isTrue();
        assertThat(second.clock().isPlaying()).isTrue();
    }

    @Test
    @DisplayName("a disconnecting buffering client releases the hold on the clock")
    void disconnectReleasesBuffering() {
        rooms.clockPlay(ROOM, 20.0);
        buffering.start(ROOM, "session-1");

        BufferingResult result = buffering.remove(ROOM, "session-1");

        assertThat(result).isNotNull();
        assertThat(result.clockChanged()).isTrue();
        assertThat(result.clock().isPlaying()).isTrue();
    }

    @Test
    @DisplayName("removing a client that was not buffering is a no-op")
    void removeNonBufferingClient() {
        rooms.clockPlay(ROOM, 20.0);

        assertThat(buffering.remove(ROOM, "unknown")).isNull();
        assertThat(buffering.remove("no-such-room", "unknown")).isNull();
    }

    @Test
    @DisplayName("reset forgets everyone who was buffering")
    void resetClearsBuffering() {
        rooms.clockPlay(ROOM, 20.0);
        buffering.start(ROOM, "alice");

        buffering.reset(ROOM);

        BufferingResult after = buffering.start(ROOM, "bob");
        assertThat(after.count()).isEqualTo(1);
    }
}
