package com.sopdemo.content;

/**
 * Raised when a source document exceeds the configured size limit in UTF-8 bytes (FR-020).
 * Mapped to HTTP 413 by the API layer (IR-001).
 */
public class SourceTooLargeException extends RuntimeException {

    private final int maxBytes;
    private final int actualBytes;

    public SourceTooLargeException(int maxBytes, int actualBytes) {
        super("Source is " + actualBytes + " bytes; the limit is " + maxBytes + " bytes (64 KiB).");
        this.maxBytes = maxBytes;
        this.actualBytes = actualBytes;
    }

    public int maxBytes() {
        return maxBytes;
    }

    public int actualBytes() {
        return actualBytes;
    }
}
