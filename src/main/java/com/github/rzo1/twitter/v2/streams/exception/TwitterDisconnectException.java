package com.github.rzo1.twitter.v2.streams.exception;

public class TwitterDisconnectException extends RuntimeException {

    private static final long serialVersionUID = -956646895020191402L;

    public TwitterDisconnectException(String message) {
        super(message);
    }

}
