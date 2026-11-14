package com.emirkabacali.seatlock.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SeatTest {

	private final Seat seat = new Seat(new Event(new Venue("V", "C", "A"), "E", null, Instant.now()), "A", "A", 1,
			BigDecimal.TEN);

	@Test
	void newSeatIsAvailable() {
		assertThat(seat.isAvailable()).isTrue();
	}

	@Test
	void holdAndReleaseRoundTrip() {
		seat.hold();
		assertThat(seat.getStatus()).isEqualTo(SeatStatus.HELD);

		seat.release();
		assertThat(seat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
	}

	@Test
	void heldSeatCannotBeHeldAgain() {
		seat.hold();
		assertThatThrownBy(seat::hold).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void availableSeatCannotBeReleased() {
		assertThatThrownBy(seat::release).isInstanceOf(IllegalStateException.class);
	}

}
