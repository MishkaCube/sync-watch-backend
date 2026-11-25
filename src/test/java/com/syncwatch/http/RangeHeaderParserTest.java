package com.syncwatch.http;

import com.syncwatch.http.RangeHeaderParser.RangeNotSatisfiableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RangeHeaderParserTest {

    private static final long SIZE = 1000;

    @Test
    @DisplayName("a closed range returns exactly that range")
    void closedRange() {
        assertThat(RangeHeaderParser.parse("bytes=0-499", SIZE)).contains(new ByteRange(0, 499));
    }

    @Test
    @DisplayName("an open-ended range runs to the end of the file")
    void openEnded() {
        ByteRange range = RangeHeaderParser.parse("bytes=500-", SIZE).orElseThrow();

        assertThat(range).isEqualTo(new ByteRange(500, 999));
        assertThat(range.length()).isEqualTo(500);
    }

    @Test
    @DisplayName("a suffix range returns the last bytes")
    void suffixRange() {
        assertThat(RangeHeaderParser.parse("bytes=-200", SIZE)).contains(new ByteRange(800, 999));
    }

    @Test
    @DisplayName("a suffix longer than the file returns the whole file")
    void suffixLongerThanFile() {
        assertThat(RangeHeaderParser.parse("bytes=-5000", SIZE)).contains(new ByteRange(0, 999));
    }

    @Test
    @DisplayName("an end beyond the file is clamped to the last byte")
    void endIsClamped() {
        assertThat(RangeHeaderParser.parse("bytes=900-5000", SIZE)).contains(new ByteRange(900, 999));
    }

    @Test
    @DisplayName("a start beyond the file is unsatisfiable")
    void startBeyondFile() {
        assertThatThrownBy(() -> RangeHeaderParser.parse("bytes=1000-", SIZE))
                .isInstanceOf(RangeNotSatisfiableException.class);
    }

    @Test
    @DisplayName("an end before the start is unsatisfiable")
    void endBeforeStart() {
        assertThatThrownBy(() -> RangeHeaderParser.parse("bytes=500-100", SIZE))
                .isInstanceOf(RangeNotSatisfiableException.class);
    }

    @Test
    @DisplayName("a zero-length suffix is unsatisfiable")
    void zeroSuffix() {
        assertThatThrownBy(() -> RangeHeaderParser.parse("bytes=-0", SIZE))
                .isInstanceOf(RangeNotSatisfiableException.class);
    }

    @Test
    @DisplayName("missing, malformed and multi-range headers are not handled by the parser")
    void ignoredHeaders() {
        assertThat(RangeHeaderParser.parse(null, SIZE)).isEqualTo(Optional.empty());
        assertThat(RangeHeaderParser.parse("items=0-5", SIZE)).isEmpty();
        assertThat(RangeHeaderParser.parse("bytes=abc-def", SIZE)).isEmpty();
        assertThat(RangeHeaderParser.parse("bytes=5", SIZE)).isEmpty();
        assertThat(RangeHeaderParser.parse("bytes=-", SIZE)).isEmpty();
        assertThat(RangeHeaderParser.parse("bytes=0-10,20-30", SIZE)).isEmpty();
    }
}
