package com.github.rzo1.twitter.v2.streams.exception;

public class TwitterServiceException extends RuntimeException {

    private static final long serialVersionUID = -6068914821407565485L;

    public TwitterServiceException(String message) {
        super(message);
    }

    public TwitterServiceException(Throwable throwable) {
        super(throwable);
    }

    public TwitterServiceException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
