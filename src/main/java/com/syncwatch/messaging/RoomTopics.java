package com.syncwatch.messaging;

import java.util.Optional;

/** Single place that knows how room ids map to STOMP destinations and back. */
public final class RoomTopics {

    private static final String PREFIX = "/topic/room.";

    private RoomTopics() { }

    public static String destination(String roomId) {
        return PREFIX + roomId;
    }

    /** Returns the room id if the destination is a room topic, empty otherwise. */
    public static Optional<String> roomIdOf(String destination) {
        if (destination == null || !destination.startsWith(PREFIX)) return Optional.empty();
        return Optional.of(destination.substring(PREFIX.length()));
    }
}
