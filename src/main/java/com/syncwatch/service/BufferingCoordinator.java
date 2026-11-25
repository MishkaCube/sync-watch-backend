package com.syncwatch.service;

import com.syncwatch.model.Room;
import com.syncwatch.model.RoomClock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Keeps the room waiting for slow clients: while anyone is buffering the shared
 * clock is frozen, and it resumes once every buffering client has recovered or left.
 */
@Service
@RequiredArgsConstructor
public class BufferingCoordinator {

    /** Outcome of a buffering change: the clock (possibly frozen or resumed) and who is still buffering. */
    public record BufferingResult(RoomClock clock, int count, boolean clockChanged) {}

    private final RoomRepository rooms;

    public BufferingResult start(String roomId, String clientKey) {
        Room room = activeRoom(roomId);
        boolean wasEmpty = room.getBufferingClients().isEmpty();
        room.getBufferingClients().add(clientKey);

        boolean clockChanged = false;
        if (wasEmpty && room.getClock().isPlaying()) {
            // freeze the clock at the current expected position
            room.setPlayingBeforeBuffer(true);
            room.getClock().pause(room.getClock().computePosition());
            clockChanged = true;
        }
        return result(room, clockChanged);
    }

    public BufferingResult end(String roomId, String clientKey) {
        Room room = activeRoom(roomId);
        room.getBufferingClients().remove(clientKey);
        return result(room, resumeIfRecovered(room));
    }

    /** Cleans up when a client disconnects. Returns null if it was not buffering. */
    public BufferingResult remove(String roomId, String clientKey) {
        Room room = rooms.find(roomId).orElse(null);
        if (room == null) return null;
        if (!room.getBufferingClients().remove(clientKey)) return null;   // wasn't buffering
        return result(room, resumeIfRecovered(room));
    }

    /** Forgets all buffering state, for example when the video source is reset. */
    public void reset(String roomId) {
        Room room = activeRoom(roomId);
        room.getBufferingClients().clear();
        room.setPlayingBeforeBuffer(false);
    }

    private boolean resumeIfRecovered(Room room) {
        if (room.getBufferingClients().isEmpty() && room.isPlayingBeforeBuffer()) {
            // all clients recovered: resume from the frozen position
            room.setPlayingBeforeBuffer(false);
            room.getClock().play(room.getClock().getPosition());
            return true;
        }
        return false;
    }

    private Room activeRoom(String roomId) {
        Room room = rooms.getOrCreate(roomId);
        room.touch();
        return room;
    }

    private static BufferingResult result(Room room, boolean clockChanged) {
        return new BufferingResult(room.getClock(), room.getBufferingClients().size(), clockChanged);
    }
}
