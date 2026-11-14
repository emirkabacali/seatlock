package com.emirkabacali.seatlock.exception;

/** Mapped to HTTP 404 by {@link GlobalExceptionHandler}. */
public class ResourceNotFoundException extends RuntimeException {

	public ResourceNotFoundException(String resource, Object id) {
		super(resource + " with id " + id + " was not found");
	}

}
