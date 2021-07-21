package com.castsoftware.aip.console.tools.core.exceptions;

public class InvalidVersionException extends RuntimeException {

    private final static String DEFAULT_MESSAGE = "Invalid AIP Console version error";

    public InvalidVersionException() {
        super(DEFAULT_MESSAGE);
    }

    public InvalidVersionException(String message) {
        super(message);
    }

    public InvalidVersionException(Throwable cause) {
        super(DEFAULT_MESSAGE, cause);
    }

    public InvalidVersionException(String message, Throwable cause) {
        super(message, cause);
    }
}
