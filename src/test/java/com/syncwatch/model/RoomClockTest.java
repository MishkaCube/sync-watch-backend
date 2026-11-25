package com.syncwatch.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RoomClockTest {

    @Test
    @DisplayName("a new clock is paused at position zero")
    void newClockIsPausedAtZero() {
        RoomClock clock = new RoomClock();

        assertThat(clock.isPlaying()).isFalse();
        assertThat(clock.computePosition()).isZero();
    }

    @Test
    @DisplayName("a paused clock does not advance with time")
    void pausedClockDoesNotAdvance() {
        RoomClock clock = new RoomClock();
        clock.pause(42.0);
        clock.setUpdatedAt(Instant.now().minusSeconds(30));

        assertThat(clock.computePosition()).isEqualTo(42.0);
    }

    @Test
    @DisplayName("a playing clock extrapolates the position from the elapsed time")
    void playingClockExtrapolates() {
        RoomClock clock = new RoomClock();
        clock.play(10.0);
        clock.setUpdatedAt(Instant.now().minusSeconds(5));

        assertThat(clock.computePosition()).isCloseTo(15.0, within(0.5));
    }

    @Test
    @DisplayName("seek moves the position and keeps the playing state")
    void seekKeepsPlayingState() {
        RoomClock clock = new RoomClock();
        clock.play(10.0);

        clock.seek(100.0);

        assertThat(clock.isPlaying()).isTrue();
        assertThat(clock.getPosition()).isEqualTo(100.0);
        assertThat(clock.computePosition()).isCloseTo(100.0, within(0.5));
    }

    @Test
    @DisplayName("pause freezes the clock at the given position")
    void pauseFreezes() {
        RoomClock clock = new RoomClock();
        clock.play(5.0);

        clock.pause(8.0);

        assertThat(clock.isPlaying()).isFalse();
        assertThat(clock.computePosition()).isEqualTo(8.0);
    }
}
