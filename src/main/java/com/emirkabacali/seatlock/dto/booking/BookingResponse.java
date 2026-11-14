package com.emirkabacali.seatlock.dto.booking;

import com.emirkabacali.seatlock.entity.Booking;
import com.emirkabacali.seatlock.entity.BookingStatus;
import com.emirkabacali.seatlock.entity.Seat;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record BookingResponse(
		Long id,
		Long eventId,
		String eventName,
		List<BookedSeat> seats,
		BigDecimal totalPrice,
		BookingStatus status,
		Instant expiresAt,
		Instant createdAt) {

	public record BookedSeat(Long id, String section, String row, int number, BigDecimal price) {

		static BookedSeat from(Seat seat) {
			return new BookedSeat(seat.getId(), seat.getSection(), seat.getRowLabel(), seat.getSeatNumber(),
					seat.getPrice());
		}

	}

	/** Event and seats must be loaded (inside a transaction). */
	public static BookingResponse from(Booking booking) {
		return new BookingResponse(booking.getId(), booking.getEvent().getId(), booking.getEvent().getName(),
				booking.getSeats().stream().map(BookedSeat::from).toList(), booking.getTotalPrice(),
				booking.getStatus(), booking.getExpiresAt(), booking.getCreatedAt());
	}

}
