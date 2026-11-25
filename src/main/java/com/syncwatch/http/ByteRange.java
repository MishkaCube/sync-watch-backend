package com.syncwatch.http;

/** An inclusive byte range {@code [start, end]} of a resource. */
public record ByteRange(long start, long end) {

    public long length() {
        return end - start + 1;
    }
}
