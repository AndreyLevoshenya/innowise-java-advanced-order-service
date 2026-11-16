package com.inno.service.exception;

public class UserServiceUnavailableException extends RuntimeException {
    public UserServiceUnavailableException(String message, Throwable exception) {
        super(message, exception);
    }
}
