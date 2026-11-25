package com.syncwatch.sync;

import com.syncwatch.model.EventType;
import com.syncwatch.model.PlayerEvent;
import com.syncwatch.service.InMemoryRoomRepository;
import com.syncwatch.service.RoomService;
import com.syncwatch.service.SourceCacheService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlayerEventDispatcherTest {

    /** Records what it was called with. */
    private static final class RecordingHandler implements PlayerEventHandler {
        private final EventType type;
        final List<String> calls = new ArrayList<>();

        RecordingHandler(EventType type) {
            this.type = type;
        }

        @Override
        public EventType type() {
            return type;
        }

        @Override
        public void handle(String roomId, PlayerEvent event, String sessionId) {
            calls.add(roomId + "/" + sessionId);
        }
    }

    private final RoomService rooms = new RoomService(new InMemoryRoomRepository(), new SourceCacheService());

    private static PlayerEvent event(EventType type) {
        PlayerEvent e = new PlayerEvent();
        e.setType(type);
        return e;
    }

    @Test
    @DisplayName("an event goes only to the handler registered for its type")
    void routesByType() {
        RecordingHandler play = new RecordingHandler(EventType.PLAY);
        RecordingHandler pause = new RecordingHandler(EventType.PAUSE);
        PlayerEventDispatcher dispatcher = new PlayerEventDispatcher(rooms, List.of(play, pause));

        dispatcher.dispatch("room", event(EventType.PAUSE), "session-1");

        assertThat(play.calls).isEmpty();
        assertThat(pause.calls).containsExactly("room/session-1");
    }

    @Test
    @DisplayName("every event refreshes the room's activity, even an unhandled one")
    void touchesRoom() {
        PlayerEventDispatcher dispatcher = new PlayerEventDispatcher(rooms, List.of());

        dispatcher.dispatch("room", event(EventType.SYNC), "s");

        assertThat(rooms.roomIds()).contains("room");
    }

    @Test
    @DisplayName("an unhandled or missing type is ignored instead of failing")
    void ignoresUnhandledTypes() {
        RecordingHandler play = new RecordingHandler(EventType.PLAY);
        PlayerEventDispatcher dispatcher = new PlayerEventDispatcher(rooms, List.of(play));

        dispatcher.dispatch("room", event(EventType.SYNC), "s");
        dispatcher.dispatch("room", event(null), "s");

        assertThat(play.calls).isEmpty();
    }

    @Test
    @DisplayName("two handlers for the same type fail fast at startup")
    void rejectsDuplicateHandlers() {
        assertThatThrownBy(() -> new PlayerEventDispatcher(rooms,
                List.of(new RecordingHandler(EventType.PLAY), new RecordingHandler(EventType.PLAY))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PLAY");
    }
}
