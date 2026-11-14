package com.emirkabacali.seatlock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.emirkabacali.seatlock.AbstractIntegrationTest;
import com.emirkabacali.seatlock.dto.booking.BookingResponse;
import com.emirkabacali.seatlock.dto.booking.CreateBookingRequest;
import com.emirkabacali.seatlock.entity.BookingStatus;
import com.emirkabacali.seatlock.entity.SeatStatus;
import com.emirkabacali.seatlock.exception.ConflictException;
import com.emirkabacali.seatlock.repository.BookingRepository;
import com.emirkabacali.seatlock.repository.SeatRepository;
import com.emirkabacali.seatlock.support.TestData;
import com.emirkabacali.seatlock.support.TestData.OnSaleEvent;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(TestData.class)
class BookingExpiryIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private BookingService bookingService;

	@Autowired
	private BookingExpiryService expiryService;

	@Autowired
	private BookingRepository bookingRepository;

	@Autowired
	private SeatRepository seatRepository;

	@Autowired
	private SeatHoldService seatHolds;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private TestData testData;

	@Test
	void overdueBookingsAreExpiredAndTheirSeatsBecomeBookableAgain() {
		OnSaleEvent event = testData.onSaleEventWithSeats(2);
		List<Long> users = testData.users(2);
		BookingResponse overdue = bookingService.create(users.get(0), new CreateBookingRequest(event.eventId(),
				List.of(event.seatIds().get(0))));
		BookingResponse stillValid = bookingService.create(users.get(0), new CreateBookingRequest(event.eventId(),
				List.of(event.seatIds().get(1))));
		makeOverdue(overdue.id());

		expiryService.expireOverdueBookings();

		assertThat(bookingRepository.findById(overdue.id()).orElseThrow().getStatus()).isEqualTo(BookingStatus.EXPIRED);
		assertThat(bookingRepository.findById(stillValid.id()).orElseThrow().getStatus())
				.isEqualTo(BookingStatus.PENDING);
		assertThat(seatRepository.findById(event.seatIds().get(0)).orElseThrow().getStatus())
				.isEqualTo(SeatStatus.AVAILABLE);
		assertThat(seatRepository.findById(event.seatIds().get(1)).orElseThrow().getStatus())
				.isEqualTo(SeatStatus.HELD);

		// The Redis hold of the expired booking has not reached its TTL in this test; simulate that.
		redisHoldExpired(event, overdue);
		BookingResponse rebooked = bookingService.create(users.get(1), new CreateBookingRequest(event.eventId(),
				List.of(event.seatIds().get(0))));
		assertThat(rebooked.status()).isEqualTo(BookingStatus.PENDING);
	}

	@Test
	void expiredBookingCannotBeCancelled() {
		OnSaleEvent event = testData.onSaleEventWithSeats(1);
		Long userId = testData.users(1).get(0);
		BookingResponse booking = bookingService.create(userId, new CreateBookingRequest(event.eventId(),
				event.seatIds()));
		makeOverdue(booking.id());
		expiryService.expireOverdueBookings();

		assertThatThrownBy(() -> bookingService.cancel(userId, booking.id())).isInstanceOf(ConflictException.class);
	}

	@Test
	void runningTheJobTwiceIsHarmless() {
		OnSaleEvent event = testData.onSaleEventWithSeats(1);
		BookingResponse booking = bookingService.create(testData.users(1).get(0),
				new CreateBookingRequest(event.eventId(), event.seatIds()));
		makeOverdue(booking.id());

		expiryService.expireOverdueBookings();
		expiryService.expireOverdueBookings();

		assertThat(bookingRepository.findById(booking.id()).orElseThrow().getStatus()).isEqualTo(BookingStatus.EXPIRED);
	}

	private void makeOverdue(Long bookingId) {
		jdbc.update("update bookings set expires_at = now() - interval '1 minute' where id = ?", bookingId);
	}

	private void redisHoldExpired(OnSaleEvent event, BookingResponse booking) {
		String token = bookingRepository.findById(booking.id()).orElseThrow().getHoldToken();
		assertThat(token).isNotNull();
		seatHolds.release(event.eventId(), booking.seats().stream().map(BookingResponse.BookedSeat::id).toList(), token);
	}

}
