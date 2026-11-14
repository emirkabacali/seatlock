package com.emirkabacali.seatlock.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.emirkabacali.seatlock.config.BookingProperties;
import com.emirkabacali.seatlock.dto.booking.CreateBookingRequest;
import com.emirkabacali.seatlock.exception.BadRequestException;
import com.emirkabacali.seatlock.exception.ConflictException;
import com.emirkabacali.seatlock.repository.BookingRepository;
import com.emirkabacali.seatlock.service.SeatHoldService.HoldResult;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Orchestration between the Redis gate and the database writer. */
class BookingServiceTest {

	private final BookingWriter bookingWriter = mock(BookingWriter.class);
	private final SeatHoldService seatHolds = mock(SeatHoldService.class);
	private final BookingProperties properties = new BookingProperties(Duration.ofMinutes(10),
			LockingStrategy.OPTIMISTIC, 3, null);
	private final BookingService bookingService = new BookingService(bookingWriter, mock(BookingRepository.class),
			seatHolds, properties);

	private final CreateBookingRequest request = new CreateBookingRequest(1L, List.of(10L, 11L));

	@Test
	void seatsHeldInRedisAreBookedInTheDatabaseWithTheHoldToken() {
		when(seatHolds.tryHold(eq(1L), eq(Set.of(10L, 11L)), anyString(), any())).thenReturn(HoldResult.ACQUIRED);

		bookingService.create(7L, request);

		verify(bookingWriter).create(eq(7L), eq(request), eq(LockingStrategy.OPTIMISTIC), notNull());
	}

	@Test
	void seatsAlreadyHeldInRedisAreRejectedWithoutTouchingTheDatabase() {
		when(seatHolds.tryHold(anyLong(), any(), anyString(), any())).thenReturn(HoldResult.REJECTED);

		assertThatThrownBy(() -> bookingService.create(7L, request)).isInstanceOf(ConflictException.class);
		verifyNoInteractions(bookingWriter);
	}

	@Test
	void redisHoldsAreReleasedWhenTheDatabaseStepFails() {
		when(seatHolds.tryHold(anyLong(), any(), anyString(), any())).thenReturn(HoldResult.ACQUIRED);
		when(bookingWriter.create(anyLong(), any(), any(), anyString()))
				.thenThrow(new ConflictException("Seats not available"));

		assertThatThrownBy(() -> bookingService.create(7L, request)).isInstanceOf(ConflictException.class);
		verify(seatHolds).release(eq(1L), eq(Set.of(10L, 11L)), anyString());
	}

	@Test
	void fallsBackToDatabaseOnlyWhenRedisIsUnavailable() {
		when(seatHolds.tryHold(anyLong(), any(), anyString(), any())).thenReturn(HoldResult.UNAVAILABLE);

		bookingService.create(7L, request);

		verify(bookingWriter).create(eq(7L), eq(request), eq(LockingStrategy.OPTIMISTIC), isNull());
	}

	@Test
	void validatesSeatIdsBeforeAnyHold() {
		assertThatThrownBy(() -> bookingService.create(7L, new CreateBookingRequest(1L, List.of(10L, 10L))))
				.isInstanceOf(BadRequestException.class);
		assertThatThrownBy(() -> bookingService.create(7L, new CreateBookingRequest(1L, List.of(1L, 2L, 3L, 4L))))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("at most 3");
		verify(seatHolds, never()).tryHold(anyLong(), any(), anyString(), any());
	}

}
