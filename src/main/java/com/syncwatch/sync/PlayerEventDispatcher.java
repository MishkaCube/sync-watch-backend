package com.syncwatch.sync;

import com.syncwatch.model.EventType;
import com.syncwatch.model.PlayerEvent;
import com.syncwatch.service.RoomService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Routes a player event to the handler registered for its type. */
@Component
public class PlayerEventDispatcher {

    private static final Logger log = LoggerFactory.getLogger(PlayerEventDispatcher.class);

    private final RoomService roomService;
    private final Map<EventType, PlayerEventHandler> handlers = new EnumMap<>(EventType.class);

    public PlayerEventDispatcher(RoomService roomService, List<PlayerEventHandler> handlerList) {
        this.roomService = roomService;
        for (PlayerEventHandler handler : handlerList) {
            PlayerEventHandler previous = handlers.put(handler.type(), handler);
            if (previous != null) {
                throw new IllegalStateException("Two handlers registered for event type " + handler.type()
                        + ": " + previous.getClass().getSimpleName() + " and " + handler.getClass().getSimpleName());
            }
        }
    }

    public void dispatch(String roomId, PlayerEvent event, String sessionId) {
        roomService.touch(roomId);   // any event keeps the room alive

        EventType type = event.getType() != null ? event.getType() : EventType.UNKNOWN;
        PlayerEventHandler handler = handlers.get(type);
        if (handler == null) {
            log.debug("Room {} unhandled event type: {}", roomId, type);
            return;
        }
        handler.handle(roomId, event, sessionId);
    }
}
