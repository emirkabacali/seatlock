package com.emirkabacali.seatlock.dto.seat;

import com.emirkabacali.seatlock.entity.Seat;
import com.emirkabacali.seatlock.entity.SeatStatus;
import java.math.BigDecimal;

public record SeatResponse(Long id, String section, String row, int number, BigDecimal price, SeatStatus status) {

	public static SeatResponse from(Seat seat) {
		return new SeatResponse(seat.getId(), seat.getSection(), seat.getRowLabel(), seat.getSeatNumber(),
				seat.getPrice(), seat.getStatus());
	}

}
