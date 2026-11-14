package com.emirkabacali.seatlock.service;

import com.emirkabacali.seatlock.config.BookingProperties;
import com.emirkabacali.seatlock.dto.booking.BookingResponse;
import com.emirkabacali.seatlock.dto.booking.CreateBookingRequest;
import com.emirkabacali.seatlock.entity.Booking;
import com.emirkabacali.seatlock.entity.Event;
import com.emirkabacali.seatlock.entity.EventStatus;
import com.emirkabacali.seatlock.entity.Seat;
import com.emirkabacali.seatlock.exception.ConflictException;
import com.emirkabacali.seatlock.exception.ResourceNotFoundException;
import com.emirkabacali.seatlock.repository.BookingRepository;
import com.emirkabacali.seatlock.repository.EventRepository;
import com.emirkabacali.seatlock.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transactional (database) part of booking. Kept separate from {@link BookingService} so that
 * Redis calls happen outside the database transaction, and so the database guarantee can be
 * tested on its own.
 */
@Service
public class BookingWriter {

	private final BookingRepository bookingRepository;
	private final EventRepository eventRepository;
	private final UserRepository userRepository;
	private final SeatReserver seatReserver;
	private final BookingProperties properties;
	private final Clock clock;

	public BookingWriter(BookingRepository bookingRepository, EventRepository eventRepository,
			UserRepository userRepository, SeatReserver seatReserver, BookingProperties properties, Clock clock) {
		this.bookingRepository = bookingRepository;
		this.eventRepository = eventRepository;
		this.userRepository = userRepository;
		this.seatReserver = seatReserver;
		this.properties = properties;
		this.clock = clock;
	}

	/**
	 * Holds the seats in the database and creates a PENDING booking, in one transaction.
	 *
	 * @param holdToken owner token of the Redis holds, or {@code null} if Redis was not used
	 */
	@Transactional
	public BookingResponse create(Long userId, CreateBookingRequest request, LockingStrategy strategy,
			String holdToken) {
		Event event = eventRepository.findById(request.eventId())
				.orElseThrow(() -> new ResourceNotFoundException("Event", request.eventId()));
		if (event.getStatus() != EventStatus.ON_SALE) {
			throw new ConflictException("Event " + event.getId() + " is not on sale");
		}
		Set<Long> seatIds = new LinkedHashSet<>(request.seatIds());

		List<Seat> seats = seatReserver.reserve(event.getId(), seatIds, strategy);

		// PostgreSQL stores microseconds; truncating keeps the in-memory value equal to the stored one.
		Instant expiresAt = clock.instant().plus(properties.holdDuration()).truncatedTo(ChronoUnit.MICROS);
		Booking booking = new Booking(userRepository.getReferenceById(userId), event, seats, expiresAt, holdToken);
		return BookingResponse.from(bookingRepository.save(booking));
	}

	/** Cancels a PENDING booking of the user. Returns the booking so the caller can release Redis holds. */
	@Transactional
	public Cancelled cancel(Long userId, Long bookingId) {
		Booking booking = bookingRepository.findByIdAndUserId(bookingId, userId)
				.orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));
		if (!booking.isPending()) {
			throw new ConflictException("Booking " + bookingId + " is " + booking.getStatus() + " and cannot be cancelled");
		}
		booking.cancel();
		List<Long> seatIds = booking.getSeats().stream().map(Seat::getId).toList();
		return new Cancelled(BookingResponse.from(booking), booking.getEvent().getId(), seatIds, booking.getHoldToken());
	}

	public record Cancelled(BookingResponse booking, Long eventId, List<Long> seatIds, String holdToken) {
	}

}
