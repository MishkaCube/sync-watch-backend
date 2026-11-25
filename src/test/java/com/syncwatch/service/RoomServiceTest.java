package com.syncwatch.service;

import com.syncwatch.model.EventType;
import com.syncwatch.model.PlayerEvent;
import com.syncwatch.model.Room;
import com.syncwatch.model.RoomClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoomServiceTest {

    private static final String ROOM = "room-1";

    private RoomService rooms;

    @BeforeEach
    void setUp() {
        rooms = new RoomService(new InMemoryRoomRepository(), new SourceCacheService());
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
    @DisplayName("play, pause and seek move the shared clock")
    void clockOperations() {
        assertThat(rooms.clockPlay(ROOM, 10.0).isPlaying()).isTrue();
        assertThat(rooms.clockSeek(ROOM, 50.0).getPosition()).isEqualTo(50.0);
        RoomClock paused = rooms.clockPause(ROOM, 55.0);
        assertThat(paused.isPlaying()).isFalse();
        assertThat(paused.getPosition()).isEqualTo(55.0);
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
    @DisplayName("lastActivity of an unknown room is null")
    void lastActivityOfUnknownRoom() {
        assertThat(rooms.lastActivity("nope")).isNull();
    }

    @Test
    @DisplayName("remove deletes the room")
    void removeDeletesRoom() {
        rooms.touch(ROOM);

        rooms.remove(ROOM);

        assertThat(rooms.roomIds()).doesNotContain(ROOM);
    }

    @Test
    @DisplayName("only a source-change event is remembered for late joiners")
    void remembersOnlySourceChange() {
        PlayerEvent source = new PlayerEvent();
        source.setType(EventType.SOURCE_CHANGE);
        source.setSourceValue("https://example.com/video.mp4");
        rooms.touch(ROOM);

        rooms.updateLastSource(ROOM, source);

        assertThat(rooms.getRoom(ROOM)).get().extracting(Room::getLastSource).isEqualTo(source);

        PlayerEvent pause = new PlayerEvent();
        pause.setType(EventType.PAUSE);
        rooms.updateLastSource("other", pause);
        assertThat(rooms.getRoom("other")).isEmpty();
    }

    @Test
    @DisplayName("resetSource clears the source and rewinds the clock, paused")
    void resetSource() {
        PlayerEvent source = new PlayerEvent();
        source.setType(EventType.SOURCE_CHANGE);
        rooms.clockPlay(ROOM, 99.0);
        rooms.updateLastSource(ROOM, source);

        RoomClock clock = rooms.resetSource(ROOM);

        assertThat(clock.isPlaying()).isFalse();
        assertThat(clock.getPosition()).isZero();
        assertThat(rooms.getRoom(ROOM)).get().extracting(Room::getLastSource).isNull();
    }
}
