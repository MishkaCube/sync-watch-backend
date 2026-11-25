package com.syncwatch.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/** Delivers room events over STOMP to the {@code /topic/room.{id}} destination. */
@Component
@RequiredArgsConstructor
public class StompRoomEventPublisher implements RoomEventPublisher {

    private final SimpMessagingTemplate template;

    @Override
    public void publish(String roomId, Object payload) {
        template.convertAndSend(RoomTopics.destination(roomId), payload);
    }
}
