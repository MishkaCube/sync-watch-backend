package com.syncwatch.service;

import com.syncwatch.model.Room;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryRoomRepositoryTest {

    private final RoomRepository repository = new InMemoryRoomRepository();

    @Test
    @DisplayName("create stores a room that can be found by its id")
    void createAndFind() {
        Room room = repository.create();

        assertThat(repository.find(room.getId())).containsSame(room);
        assertThat(repository.ids()).contains(room.getId());
    }

    @Test
    @DisplayName("getOrCreate returns the same room on repeated calls")
    void getOrCreateIsStable() {
        Room first = repository.getOrCreate("abc");
        Room second = repository.getOrCreate("abc");

        assertThat(second).isSameAs(first);
        assertThat(first.getId()).isEqualTo("abc");
    }

    @Test
    @DisplayName("find does not create a missing room")
    void findDoesNotCreate() {
        assertThat(repository.find("missing")).isEmpty();
        assertThat(repository.ids()).isEmpty();
    }

    @Test
    @DisplayName("remove deletes the room and ids returns a copy")
    void removeAndIdsCopy() {
        Room room = repository.create();
        var ids = repository.ids();

        repository.remove(room.getId());

        assertThat(repository.find(room.getId())).isEmpty();
        assertThat(ids).contains(room.getId());   // the earlier snapshot is unaffected
    }
}
