package com.syncwatch.service;

import com.syncwatch.model.EventType;
import com.syncwatch.model.PlayerEvent;
import com.syncwatch.model.Room;
import com.syncwatch.model.RoomClock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/** Room lifecycle, the shared playback clock and the current video source. */
@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository rooms;
    private final SourceCacheService sourceCache;

    public Room createRoom() {
        return rooms.create();
    }

    public Optional<Room> getRoom(String id) {
        return rooms.find(id).map(room -> {
            // populate the source from the cache so the response is up to date
            room.setLastSource(sourceCache.get(id).orElse(null));
            return room;
        });
    }

    /** Refreshes a room's activity timestamp (called on every WebSocket event). */
    public void touch(String roomId) {
        activeRoom(roomId);
    }

    // ── Cleanup support ───────────────────────────────────────────────────────

    public Set<String> roomIds() {
        return rooms.ids();
    }

    public Instant lastActivity(String roomId) {
        return rooms.find(roomId).map(Room::getLastActivity).orElse(null);
    }

    public void remove(String roomId) {
        rooms.remove(roomId);
    }

    // ── Clock operations ──────────────────────────────────────────────────────

    public RoomClock clockPlay(String roomId, double position) {
        RoomClock clock = activeRoom(roomId).getClock();
        clock.play(position);
        return clock;
    }

    public RoomClock clockPause(String roomId, double position) {
        RoomClock clock = activeRoom(roomId).getClock();
        clock.pause(position);
        return clock;
    }

    public RoomClock clockSeek(String roomId, double position) {
        RoomClock clock = activeRoom(roomId).getClock();
        clock.seek(position);
        return clock;
    }

    public Optional<RoomClock> getClock(String roomId) {
        return getRoom(roomId).map(Room::getClock);
    }

    // ── Source tracking (for late joiners) ────────────────────────────────────

    public void updateLastSource(String roomId, PlayerEvent event) {
        if (event.getType() == EventType.SOURCE_CHANGE) {
            sourceCache.put(roomId, event);
        }
    }

    /** Clears the source and resets the playback clock to the start (paused). */
    public RoomClock resetSource(String roomId) {
        sourceCache.clear(roomId);
        RoomClock clock = activeRoom(roomId).getClock();
        clock.pause(0);
        return clock;
    }

    private Room activeRoom(String roomId) {
        Room room = rooms.getOrCreate(roomId);
        room.touch();
        return room;
    }
}
