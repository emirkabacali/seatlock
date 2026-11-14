package com.emirkabacali.seatlock.exception;

/** The Idempotency-Key was already used for a different request. Mapped to HTTP 422. */
public class IdempotencyKeyReuseException extends RuntimeException {

	public IdempotencyKeyReuseException() {
		super("This Idempotency-Key was already used for a different request");
	}

}
