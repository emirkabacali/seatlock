package com.emirkabacali.seatlock.service;

import com.emirkabacali.seatlock.config.BookingProperties;
import com.emirkabacali.seatlock.dto.booking.BookingResponse;
import com.emirkabacali.seatlock.dto.booking.CreateBookingRequest;
import com.emirkabacali.seatlock.dto.common.PageResponse;
import com.emirkabacali.seatlock.exception.BadRequestException;
import com.emirkabacali.seatlock.exception.ConflictException;
import com.emirkabacali.seatlock.exception.ResourceNotFoundException;
import com.emirkabacali.seatlock.repository.BookingRepository;
import com.emirkabacali.seatlock.service.SeatHoldService.HoldResult;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Booking use cases. Creating a booking happens in two steps:
 * <ol>
 * <li>Redis: try to hold all requested seats at once (fast rejection without touching the database).</li>
 * <li>Database: lock and update the seats and insert the booking ({@link BookingWriter}).</li>
 * </ol>
 * If step 2 fails, the Redis holds from step 1 are released again.
 */
@Service
public class BookingService {

	private static final Logger log = LoggerFactory.getLogger(BookingService.class);

	private final BookingWriter bookingWriter;
	private final BookingRepository bookingRepository;
	private final SeatHoldService seatHolds;
	private final BookingProperties properties;

	public BookingService(BookingWriter bookingWriter, BookingRepository bookingRepository, SeatHoldService seatHolds,
			BookingProperties properties) {
		this.bookingWriter = bookingWriter;
		this.bookingRepository = bookingRepository;
		this.seatHolds = seatHolds;
		this.properties = properties;
		if (properties.lockingStrategy() == LockingStrategy.NAIVE) {
			log.warn("Booking locking strategy is NAIVE: seats CAN be double-booked. Use only for demonstrations.");
		}
	}

	public BookingResponse create(Long userId, CreateBookingRequest request) {
		Set<Long> seatIds = validateSeatIds(request);
		String token = UUID.randomUUID().toString();

		HoldResult hold = seatHolds.tryHold(request.eventId(), seatIds, token, properties.holdDuration());
		if (hold == HoldResult.REJECTED) {
			throw new ConflictException("One or more seats are currently held by another booking");
		}
		boolean heldInRedis = hold == HoldResult.ACQUIRED;
		try {
			return bookingWriter.create(userId, request, properties.lockingStrategy(), heldInRedis ? token : null);
		}
		catch (RuntimeException ex) {
			if (heldInRedis) {
				seatHolds.release(request.eventId(), seatIds, token);
			}
			throw ex;
		}
	}

	public BookingResponse cancel(Long userId, Long bookingId) {
		BookingWriter.Cancelled cancelled = bookingWriter.cancel(userId, bookingId);
		// After the commit: free the Redis holds so the seats can be booked again right away.
		seatHolds.release(cancelled.eventId(), cancelled.seatIds(), cancelled.holdToken());
		return cancelled.booking();
	}

	@Transactional(readOnly = true)
	public PageResponse<BookingResponse> findMine(Long userId, Pageable pageable) {
		return PageResponse.from(bookingRepository.findByUserId(userId, pageable), BookingResponse::from);
	}

	/** Bookings of other users are reported as "not found" so their existence is not revealed. */
	@Transactional(readOnly = true)
	public BookingResponse findMine(Long userId, Long bookingId) {
		return bookingRepository.findByIdAndUserId(bookingId, userId)
				.map(BookingResponse::from)
				.orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));
	}

	private Set<Long> validateSeatIds(CreateBookingRequest request) {
		Set<Long> seatIds = new LinkedHashSet<>(request.seatIds());
		if (seatIds.size() != request.seatIds().size()) {
			throw new BadRequestException("Seat ids must be unique");
		}
		if (seatIds.size() > properties.maxSeatsPerBooking()) {
			throw new BadRequestException("A booking can contain at most " + properties.maxSeatsPerBooking() + " seats");
		}
		return seatIds;
	}

}
