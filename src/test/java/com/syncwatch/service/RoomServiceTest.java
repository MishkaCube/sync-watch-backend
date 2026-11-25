package com.syncwatch.service;

import com.syncwatch.model.Room;
import com.syncwatch.model.RoomClock;
import com.syncwatch.service.RoomService.BufferingResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RoomServiceTest {

    private static final String ROOM = "room-1";

    private RoomService rooms;

    @BeforeEach
    void setUp() {
        rooms = new RoomService(new SourceCacheService());
    }

    @Test
    @DisplayName("createRoom returns a short id and the room can be fetched by it")
    void createAndGet() {
        Room room = rooms.createRoom();

        assertThat(room.getId()).hasSize(8);
        assertThat(rooms.getRoom(room.getId())).isPresent();
    }

    @Test
    @DisplayName("an unknown room is reported as missing")
    void unknownRoom() {
        assertThat(rooms.getRoom("nope")).isEmpty();
        assertThat(rooms.getClock("nope")).isEmpty();
    }

    @Test
    @DisplayName("clock operations lazily recreate a room lost after a restart")
    void clockRecreatesRoom() {
        rooms.clockPlay(ROOM, 12.0);

        assertThat(rooms.getRoom(ROOM)).isPresent();
        assertThat(rooms.getClock(ROOM)).get().extracting(RoomClock::isPlaying).isEqualTo(true);
    }

    @Test
    @DisplayName("touch refreshes the last activity time")
    void touchRefreshesActivity() throws InterruptedException {
        rooms.touch(ROOM);
        var first = rooms.lastActivity(ROOM);
        Thread.sleep(5);

        rooms.touch(ROOM);

        assertThat(rooms.lastActivity(ROOM)).isAfter(first);
    }

    @Test
    @DisplayName("remove deletes the room")
    void removeDeletesRoom() {
        rooms.touch(ROOM);

        rooms.remove(ROOM);

        assertThat(rooms.roomIds()).doesNotContain(ROOM);
    }

    @Test
    @DisplayName("when a client starts buffering a playing clock is frozen")
    void bufferingFreezesPlayingClock() {
        rooms.clockPlay(ROOM, 20.0);

        BufferingResult result = rooms.bufferingStart(ROOM, "alice");

        assertThat(result.count()).isEqualTo(1);
        assertThat(result.clockChanged()).isTrue();
        assertThat(result.clock().isPlaying()).isFalse();
        assertThat(result.clock().getPosition()).isCloseTo(20.0, within(0.5));
    }

    @Test
    @DisplayName("buffering on a paused clock does not change the clock")
    void bufferingOnPausedClock() {
        rooms.clockPause(ROOM, 7.0);

        BufferingResult result = rooms.bufferingStart(ROOM, "alice");

        assertThat(result.clockChanged()).isFalse();
        assertThat(result.clock().isPlaying()).isFalse();
    }

    @Test
    @DisplayName("playback resumes only after every buffering client has recovered")
    void resumesOnlyWhenAllRecovered() {
        rooms.clockPlay(ROOM, 20.0);
        rooms.bufferingStart(ROOM, "alice");
        rooms.bufferingStart(ROOM, "bob");

        BufferingResult first = rooms.bufferingEnd(ROOM, "alice");
        assertThat(first.count()).isEqualTo(1);
        assertThat(first.clockChanged()).isFalse();
        assertThat(first.clock().isPlaying()).isFalse();

        BufferingResult second = rooms.bufferingEnd(ROOM, "bob");
        assertThat(second.count()).isZero();
        assertThat(second.clockChanged()).isTrue();
        assertThat(second.clock().isPlaying()).isTrue();
    }

    @Test
    @DisplayName("a disconnecting buffering client releases the hold on the clock")
    void disconnectReleasesBuffering() {
        rooms.clockPlay(ROOM, 20.0);
        rooms.bufferingStart(ROOM, "session-1");

        BufferingResult result = rooms.bufferingRemove(ROOM, "session-1");

        assertThat(result).isNotNull();
        assertThat(result.clockChanged()).isTrue();
        assertThat(result.clock().isPlaying()).isTrue();
    }

    @Test
    @DisplayName("removing a client that was not buffering is a no-op")
    void removeNonBufferingClient() {
        rooms.clockPlay(ROOM, 20.0);

        assertThat(rooms.bufferingRemove(ROOM, "unknown")).isNull();
        assertThat(rooms.bufferingRemove("no-such-room", "unknown")).isNull();
    }
}
