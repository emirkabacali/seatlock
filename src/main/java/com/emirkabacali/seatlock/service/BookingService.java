package com.emirkabacali.seatlock.service;

import com.emirkabacali.seatlock.config.BookingProperties;
import com.emirkabacali.seatlock.dto.booking.BookingResponse;
import com.emirkabacali.seatlock.dto.booking.CreateBookingRequest;
import com.emirkabacali.seatlock.dto.common.PageResponse;
import com.emirkabacali.seatlock.entity.Booking;
import com.emirkabacali.seatlock.entity.Event;
import com.emirkabacali.seatlock.entity.EventStatus;
import com.emirkabacali.seatlock.entity.Seat;
import com.emirkabacali.seatlock.exception.BadRequestException;
import com.emirkabacali.seatlock.exception.ConflictException;
import com.emirkabacali.seatlock.exception.ResourceNotFoundException;
import com.emirkabacali.seatlock.repository.BookingRepository;
import com.emirkabacali.seatlock.repository.EventRepository;
import com.emirkabacali.seatlock.repository.UserRepository;
import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {

	private static final Logger log = LoggerFactory.getLogger(BookingService.class);

	private final BookingRepository bookingRepository;
	private final EventRepository eventRepository;
	private final UserRepository userRepository;
	private final SeatReserver seatReserver;
	private final BookingProperties properties;
	private final Clock clock;

	public BookingService(BookingRepository bookingRepository, EventRepository eventRepository,
			UserRepository userRepository, SeatReserver seatReserver, BookingProperties properties, Clock clock) {
		this.bookingRepository = bookingRepository;
		this.eventRepository = eventRepository;
		this.userRepository = userRepository;
		this.seatReserver = seatReserver;
		this.properties = properties;
		this.clock = clock;
		if (properties.lockingStrategy() == LockingStrategy.NAIVE) {
			log.warn("Booking locking strategy is NAIVE: seats CAN be double-booked. Use only for demonstrations.");
		}
	}

	/** Holds the requested seats for the user using the configured locking strategy. */
	@Transactional
	public BookingResponse create(Long userId, CreateBookingRequest request) {
		return create(userId, request, properties.lockingStrategy());
	}

	/** Same as {@link #create(Long, CreateBookingRequest)} with an explicit strategy (used to compare strategies). */
	@Transactional
	public BookingResponse create(Long userId, CreateBookingRequest request, LockingStrategy strategy) {
		Set<Long> seatIds = new LinkedHashSet<>(request.seatIds());
		if (seatIds.size() != request.seatIds().size()) {
			throw new BadRequestException("Seat ids must be unique");
		}
		if (seatIds.size() > properties.maxSeatsPerBooking()) {
			throw new BadRequestException("A booking can contain at most " + properties.maxSeatsPerBooking() + " seats");
		}
		Event event = eventRepository.findById(request.eventId())
				.orElseThrow(() -> new ResourceNotFoundException("Event", request.eventId()));
		if (event.getStatus() != EventStatus.ON_SALE) {
			throw new ConflictException("Event " + event.getId() + " is not on sale");
		}

		List<Seat> seats = seatReserver.reserve(event.getId(), seatIds, strategy);

		Booking booking = new Booking(userRepository.getReferenceById(userId), event, seats,
				clock.instant().plus(properties.holdDuration()));
		return BookingResponse.from(bookingRepository.save(booking));
	}

	@Transactional(readOnly = true)
	public PageResponse<BookingResponse> findMine(Long userId, Pageable pageable) {
		return PageResponse.from(bookingRepository.findByUserId(userId, pageable), BookingResponse::from);
	}

	@Transactional(readOnly = true)
	public BookingResponse findMine(Long userId, Long bookingId) {
		return BookingResponse.from(getOwnBooking(userId, bookingId));
	}

	@Transactional
	public BookingResponse cancel(Long userId, Long bookingId) {
		Booking booking = getOwnBooking(userId, bookingId);
		if (!booking.isPending()) {
			throw new ConflictException("Booking " + bookingId + " is " + booking.getStatus() + " and cannot be cancelled");
		}
		booking.cancel();
		return BookingResponse.from(booking);
	}

	/** Bookings of other users are reported as "not found" so their existence is not revealed. */
	private Booking getOwnBooking(Long userId, Long bookingId) {
		return bookingRepository.findByIdAndUserId(bookingId, userId)
				.orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));
	}

}
