package com.emirkabacali.seatlock.service;

/** How seats are protected against concurrent bookings. See {@link SeatReserver}. */
public enum LockingStrategy {

	/**
	 * Check-then-act without any protection. Two concurrent requests can both see a seat as
	 * AVAILABLE and both book it. Exists only to demonstrate the race condition in tests.
	 */
	NAIVE,

	/** Relies on the seat's {@code @Version} column; the losing transaction fails on commit. */
	OPTIMISTIC,

	/** Locks the seat rows with {@code SELECT ... FOR UPDATE}; other transactions wait. */
	PESSIMISTIC

}
