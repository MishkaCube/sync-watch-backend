package com.syncwatch.service;

import com.syncwatch.model.Room;

import java.util.Optional;
import java.util.Set;

/**
 * Where rooms are kept. The services only know this interface, so the in-memory
 * implementation can be replaced (for example by Redis) without touching them.
 */
public interface RoomRepository {

    /** Creates a room with a fresh id and stores it. */
    Room create();

    Optional<Room> find(String roomId);

    /** Returns the room, lazily recreating it if it was lost (for example after a restart). */
    Room getOrCreate(String roomId);

    void remove(String roomId);

    Set<String> ids();
}
