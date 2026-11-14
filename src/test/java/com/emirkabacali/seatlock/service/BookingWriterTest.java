package com.emirkabacali.seatlock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.emirkabacali.seatlock.config.BookingProperties;
import com.emirkabacali.seatlock.dto.booking.BookingResponse;
import com.emirkabacali.seatlock.dto.booking.CreateBookingRequest;
import com.emirkabacali.seatlock.entity.Booking;
import com.emirkabacali.seatlock.entity.BookingStatus;
import com.emirkabacali.seatlock.entity.Event;
import com.emirkabacali.seatlock.entity.EventStatus;
import com.emirkabacali.seatlock.entity.Role;
import com.emirkabacali.seatlock.entity.Seat;
import com.emirkabacali.seatlock.entity.User;
import com.emirkabacali.seatlock.entity.Venue;
import com.emirkabacali.seatlock.exception.ConflictException;
import com.emirkabacali.seatlock.repository.BookingRepository;
import com.emirkabacali.seatlock.repository.EventRepository;
import com.emirkabacali.seatlock.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BookingWriterTest {

	private static final Instant NOW = Instant.parse("2026-11-14T12:00:00Z");

	private final BookingRepository bookingRepository = mock(BookingRepository.class);
	private final EventRepository eventRepository = mock(EventRepository.class);
	private final UserRepository userRepository = mock(UserRepository.class);
	private final SeatReserver seatReserver = mock(SeatReserver.class);
	private final BookingProperties properties = new BookingProperties(Duration.ofMinutes(10),
			LockingStrategy.OPTIMISTIC, 3, null);
	private BookingWriter bookingWriter;

	private final Event event = new Event(new Venue("V", "C", "A"), "Concert", null, NOW.plusSeconds(86_400));

	@BeforeEach
	void setUp() {
		bookingWriter = new BookingWriter(bookingRepository, eventRepository, userRepository, seatReserver,
				properties, Clock.fixed(NOW, ZoneOffset.UTC));
		event.changeStatus(EventStatus.ON_SALE);
		when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
		when(userRepository.getReferenceById(anyLong()))
				.thenReturn(new User("u@example.com", "hash", Role.USER));
		when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	void createsPendingBookingWithTotalPriceAndHoldExpiry() {
		List<Seat> seats = List.of(seat("50.00"), seat("75.50"));
		// The event is not persisted in this unit test, so its id is null.
		when(seatReserver.reserve(isNull(), eq(Set.of(10L, 11L)), eq(LockingStrategy.OPTIMISTIC))).thenReturn(seats);

		BookingResponse response = bookingWriter.create(7L, new CreateBookingRequest(1L, List.of(10L, 11L)),
				LockingStrategy.OPTIMISTIC, "token");

		assertThat(response.status()).isEqualTo(BookingStatus.PENDING);
		assertThat(response.totalPrice()).isEqualByComparingTo("125.50");
		assertThat(response.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
	}

	@Test
	void rejectsEventsThatAreNotOnSale() {
		event.changeStatus(EventStatus.CANCELLED);

		assertThatThrownBy(() -> bookingWriter.create(7L, new CreateBookingRequest(1L, List.of(10L)),
				LockingStrategy.OPTIMISTIC, null))
				.isInstanceOf(ConflictException.class);
		verify(seatReserver, never()).reserve(anyLong(), anySet(), any());
	}

	@Test
	void cancelReleasesSeatsAndReturnsHoldToken() {
		Seat seat = seat("10.00");
		seat.hold();
		Booking booking = new Booking(new User("u@example.com", "hash", Role.USER), event, List.of(seat), NOW, "token");
		when(bookingRepository.findByIdAndUserId(5L, 7L)).thenReturn(Optional.of(booking));

		BookingWriter.Cancelled cancelled = bookingWriter.cancel(7L, 5L);

		assertThat(cancelled.booking().status()).isEqualTo(BookingStatus.CANCELLED);
		assertThat(cancelled.holdToken()).isEqualTo("token");
		assertThat(seat.isAvailable()).isTrue();
	}

	@Test
	void onlyPendingBookingsCanBeCancelled() {
		Seat seat = seat("10.00");
		seat.hold();
		Booking booking = new Booking(new User("u@example.com", "hash", Role.USER), event, List.of(seat), NOW);
		booking.expire();
		when(bookingRepository.findByIdAndUserId(5L, 7L)).thenReturn(Optional.of(booking));

		assertThatThrownBy(() -> bookingWriter.cancel(7L, 5L)).isInstanceOf(ConflictException.class);
	}

	private Seat seat(String price) {
		return new Seat(event, "A", "A", 1, new BigDecimal(price));
	}

}
