package com.syncwatch.messaging;

/**
 * Publishes an event to everyone subscribed to a room.
 * Services depend on this abstraction instead of a concrete messaging transport.
 */
public interface RoomEventPublisher {

    void publish(String roomId, Object payload);
}
