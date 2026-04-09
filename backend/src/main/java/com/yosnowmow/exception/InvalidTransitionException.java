package com.yosnowmow.exception;

/**
 * Thrown when a job status transition is not permitted by the state machine.
 * For example: attempting to move a CANCELLED job to IN_PROGRESS.
 * Maps to HTTP 409 Conflict in GlobalExceptionHandler.
 */
public class InvalidTransitionException extends RuntimeException {

    public InvalidTransitionException(String message) {
        super(message);
    }
}
