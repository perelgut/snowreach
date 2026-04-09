package com.yosnowmow.exception;

/**
 * Thrown when a payment operation fails (Stripe charge, refund, payout, etc.).
 * Maps to HTTP 402 Payment Required in GlobalExceptionHandler.
 */
public class PaymentException extends RuntimeException {

    public PaymentException(String message) {
        super(message);
    }

    public PaymentException(String message, Throwable cause) {
        super(message, cause);
    }
}
