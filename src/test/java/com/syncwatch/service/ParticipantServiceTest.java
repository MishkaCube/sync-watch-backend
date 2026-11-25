package com.syncwatch.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ParticipantServiceTest {

    private final ParticipantService participants = new ParticipantService();

    @Test
    @DisplayName("join and leave keep an accurate participant count")
    void joinAndLeave() {
        assertThat(participants.join("s1", "room")).isEqualTo(1);
        assertThat(participants.join("s2", "room")).isEqualTo(2);
        assertThat(participants.getCount("room")).isEqualTo(2);

        assertThat(participants.leave("s1")).isEqualTo(1);
        assertThat(participants.getCount("room")).isEqualTo(1);
    }

    @Test
    @DisplayName("sessions are mapped to their room")
    void sessionRoomMapping() {
        participants.join("s1", "room-a");
        participants.join("s2", "room-b");

        assertThat(participants.getRoomId("s1")).isEqualTo("room-a");
        assertThat(participants.getRoomId("s2")).isEqualTo("room-b");
        assertThat(participants.getCount("room-a")).isEqualTo(1);
    }

    @Test
    @DisplayName("leaving with an unknown session returns -1 and changes nothing")
    void leaveUnknownSession() {
        participants.join("s1", "room");

        assertThat(participants.leave("ghost")).isEqualTo(-1);
        assertThat(participants.getCount("room")).isEqualTo(1);
    }

    @Test
    @DisplayName("the count of an unknown room is zero")
    void unknownRoomCount() {
        assertThat(participants.getCount("nowhere")).isZero();
    }

    @Test
    @DisplayName("removeRoom drops the room and its sessions")
    void removeRoom() {
        participants.join("s1", "room");
        participants.join("s2", "room");
        participants.join("s3", "other");

        participants.removeRoom("room");

        assertThat(participants.getCount("room")).isZero();
        assertThat(participants.getRoomId("s1")).isNull();
        assertThat(participants.getCount("other")).isEqualTo(1);
    }
}
