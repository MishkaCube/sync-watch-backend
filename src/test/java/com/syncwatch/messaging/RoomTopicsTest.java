package com.syncwatch.messaging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoomTopicsTest {

    @Test
    @DisplayName("a room id maps to its topic destination")
    void destination() {
        assertThat(RoomTopics.destination("abc123")).isEqualTo("/topic/room.abc123");
    }

    @Test
    @DisplayName("the room id is recovered from a room destination")
    void roomIdOf() {
        assertThat(RoomTopics.roomIdOf("/topic/room.abc123")).contains("abc123");
    }

    @Test
    @DisplayName("other destinations and null are not room topics")
    void notARoomTopic() {
        assertThat(RoomTopics.roomIdOf("/topic/other")).isEmpty();
        assertThat(RoomTopics.roomIdOf(null)).isEmpty();
    }

    @Test
    @DisplayName("destination and roomIdOf are inverse operations")
    void roundTrip() {
        assertThat(RoomTopics.roomIdOf(RoomTopics.destination("r-1"))).contains("r-1");
    }
}
