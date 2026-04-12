package com.yosnowmow.exception;

/**
 * Thrown when a user document cannot be found in Firestore.
 * Mapped to HTTP 404 by GlobalExceptionHandler.
 */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(String userId) {
        super("User not found: " + userId);
    }
}
