package com.syncwatch.service;

import com.syncwatch.model.LobbyEvent.LobbyUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PresenceServiceTest {

    private final PresenceService presence = new PresenceService();

    @Test
    @DisplayName("update returns the live user list with reported quality")
    void tracksUsers() {
        presence.update("room", "alice", "good");
        List<LobbyUser> users = presence.update("room", "bob", "weak");

        assertThat(users).extracting(LobbyUser::getId, LobbyUser::getQuality)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("alice", "good"),
                        org.assertj.core.groups.Tuple.tuple("bob", "weak"));
    }

    @Test
    @DisplayName("a missing quality defaults to normal")
    void defaultsQuality() {
        List<LobbyUser> users = presence.update("room", "alice", null);

        assertThat(users).singleElement().extracting(LobbyUser::getQuality).isEqualTo("normal");
    }

    @Test
    @DisplayName("a repeated heartbeat updates the user instead of duplicating it")
    void heartbeatUpdatesInPlace() {
        presence.update("room", "alice", "good");

        List<LobbyUser> users = presence.update("room", "alice", "weak");

        assertThat(users).singleElement().extracting(LobbyUser::getQuality).isEqualTo("weak");
    }

    @Test
    @DisplayName("remove drops a single user")
    void removeUser() {
        presence.update("room", "alice", "good");
        presence.update("room", "bob", "good");

        List<LobbyUser> users = presence.remove("room", "alice");

        assertThat(users).extracting(LobbyUser::getId).containsExactly("bob");
    }

    @Test
    @DisplayName("removing from an unknown room returns an empty list")
    void removeFromUnknownRoom() {
        assertThat(presence.remove("nowhere", "alice")).isEmpty();
    }
}
