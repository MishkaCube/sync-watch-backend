package com.syncwatch.sync;

import com.syncwatch.model.EventType;
import com.syncwatch.model.PlayerEvent;

/**
 * Handles one type of player event. Adding support for a new event type means
 * adding a new implementation; the dispatcher and the controller stay untouched.
 */
public interface PlayerEventHandler {

    /** The event type this handler is responsible for. */
    EventType type();

    /**
     * @param roomId    room the event belongs to
     * @param event     the incoming event
     * @param sessionId WebSocket session of the sender (used to track per-connection state)
     */
    void handle(String roomId, PlayerEvent event, String sessionId);
}
