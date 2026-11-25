package com.syncwatch.http;

import java.util.Optional;

/**
 * Parses a single-range HTTP {@code Range: bytes=...} header against a resource size.
 *
 * <ul>
 *   <li>{@code bytes=0-499}: first 500 bytes</li>
 *   <li>{@code bytes=500-}: from byte 500 to the end</li>
 *   <li>{@code bytes=-200}: the last 200 bytes</li>
 * </ul>
 *
 * A well-formed range that lies outside the resource is reported as unsatisfiable. A missing
 * header, or one this parser does not handle (other units, multiple ranges, malformed input),
 * yields an empty result and is left to Spring, which serves the full resource, a multipart
 * response, or answers 416 for invalid syntax.
 */
public final class RangeHeaderParser {

    private static final String UNIT = "bytes=";

    private RangeHeaderParser() { }

    /** Thrown for a well-formed range that cannot be satisfied (HTTP 416). */
    public static class RangeNotSatisfiableException extends RuntimeException {
        public RangeNotSatisfiableException(String message) {
            super(message);
        }
    }

    /** @return the single range to serve, or empty if this parser does not handle the header */
    public static Optional<ByteRange> parse(String header, long size) {
        if (header == null || !header.startsWith(UNIT) || header.contains(",")) return Optional.empty();

        String spec = header.substring(UNIT.length()).trim();
        int dash = spec.indexOf('-');
        if (dash < 0) return Optional.empty();

        String from = spec.substring(0, dash).trim();
        String to = spec.substring(dash + 1).trim();

        try {
            if (from.isEmpty()) {
                if (to.isEmpty()) return Optional.empty();
                long suffix = Long.parseLong(to);
                if (suffix <= 0) throw new RangeNotSatisfiableException("Empty suffix range");
                return Optional.of(new ByteRange(Math.max(0, size - suffix), size - 1));
            }
            long start = Long.parseLong(from);
            long end = to.isEmpty() ? size - 1 : Math.min(Long.parseLong(to), size - 1);
            if (start < 0 || start >= size || end < start) {
                throw new RangeNotSatisfiableException("Range " + spec + " is outside of " + size + " bytes");
            }
            return Optional.of(new ByteRange(start, end));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
