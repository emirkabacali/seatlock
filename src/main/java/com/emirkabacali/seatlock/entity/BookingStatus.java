package com.emirkabacali.seatlock.entity;

/** PENDING → CONFIRMED | EXPIRED | CANCELLED. Only PENDING bookings can change state. */
public enum BookingStatus {

	PENDING,
	CONFIRMED,
	EXPIRED,
	CANCELLED

}
