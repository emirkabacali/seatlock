package com.emirkabacali.seatlock.service;

import com.emirkabacali.seatlock.config.BookingProperties;
import com.emirkabacali.seatlock.entity.Booking;
import com.emirkabacali.seatlock.repository.BookingRepository;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Database side of hold expiry. Redis forgets expired holds by itself (TTL), but the seats and
 * the booking in the database must be updated explicitly.
 */
@Service
public class BookingExpiryService {

	private static final Logger log = LoggerFactory.getLogger(BookingExpiryService.class);

	private final BookingRepository bookingRepository;
	private final BookingProperties properties;
	private final Clock clock;

	public BookingExpiryService(BookingRepository bookingRepository, BookingProperties properties, Clock clock) {
		this.bookingRepository = bookingRepository;
		this.properties = properties;
		this.clock = clock;
	}

	/**
	 * Expires one batch of overdue PENDING bookings and releases their seats.
	 *
	 * @return the number of bookings expired
	 */
	@Transactional
	public int expireOverdueBookings() {
		List<Booking> overdue = bookingRepository.findOverdueForUpdate(clock.instant(),
				PageRequest.of(0, properties.expiryJob().batchSize()));
		overdue.forEach(Booking::expire);
		if (!overdue.isEmpty()) {
			log.info("Expired {} booking(s) and released their seats", overdue.size());
		}
		return overdue.size();
	}

}
