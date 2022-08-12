package com.github.rzo1.twitter.v2.streams.exception;

public class RateLimitException extends RuntimeException {

    private static final long serialVersionUID = -6593596199980783890L;
    private final long rateLimitResetMs;

    public RateLimitException(String message, long rateLimitResetMs) {
        super(message);
        this.rateLimitResetMs = rateLimitResetMs;
    }

    public long getRateLimitResetMs() {
        return rateLimitResetMs;
    }
}