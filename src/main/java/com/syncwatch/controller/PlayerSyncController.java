package com.syncwatch.controller;

import com.syncwatch.model.PlayerEvent;
import com.syncwatch.sync.PlayerEventDispatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

/** STOMP entry point for player events. All behavior lives in the per-type handlers. */
@Controller
@RequiredArgsConstructor
public class PlayerSyncController {

    private final PlayerEventDispatcher dispatcher;

    @MessageMapping("/room/{roomId}/event")
    public void handleEvent(@DestinationVariable String roomId, @Payload PlayerEvent event,
                            SimpMessageHeaderAccessor headers) {
        dispatcher.dispatch(roomId, event, headers.getSessionId());
    }
}
