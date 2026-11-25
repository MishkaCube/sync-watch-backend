package com.syncwatch.service;

import com.syncwatch.model.Room;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryRoomRepository implements RoomRepository {

    private final Map<String, Room> rooms = new ConcurrentHashMap<>();

    @Override
    public Room create() {
        Room room = newRoom(UUID.randomUUID().toString().substring(0, 8));
        rooms.put(room.getId(), room);
        return room;
    }

    @Override
    public Optional<Room> find(String roomId) {
        return Optional.ofNullable(rooms.get(roomId));
    }

    @Override
    public Room getOrCreate(String roomId) {
        return rooms.computeIfAbsent(roomId, this::newRoom);
    }

    @Override
    public void remove(String roomId) {
        rooms.remove(roomId);
    }

    @Override
    public Set<String> ids() {
        return new HashSet<>(rooms.keySet());
    }

    private Room newRoom(String id) {
        Room room = new Room();
        room.setId(id);
        room.setCreatedAt(Instant.now());
        room.setParticipantCount(0);
        return room;
    }
}
