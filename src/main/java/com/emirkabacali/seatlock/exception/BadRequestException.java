package com.emirkabacali.seatlock.exception;

/**
 * The request is invalid in a way that Bean Validation annotations cannot express
 * (e.g. rules that span several fields). Mapped to HTTP 400.
 */
public class BadRequestException extends RuntimeException {

	public BadRequestException(String message) {
		super(message);
	}

}
