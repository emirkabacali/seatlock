package com.emirkabacali.seatlock.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.emirkabacali.seatlock.AbstractIntegrationTest;
import com.emirkabacali.seatlock.service.SeatHoldService.HoldResult;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Seat holds against a real Redis (Testcontainers). */
class SeatHoldServiceIntegrationTest extends AbstractIntegrationTest {

	private static final AtomicLong IDS = new AtomicLong(1_000_000);
	private static final Duration TTL = Duration.ofMinutes(10);

	@Autowired
	private SeatHoldService seatHolds;

	@Autowired
	private StringRedisTemplate redis;

	@Test
	void holdsAllSeatsWithTheTokenAndATtl() {
		long eventId = IDS.incrementAndGet();
		List<Long> seats = List.of(IDS.incrementAndGet(), IDS.incrementAndGet());

		assertThat(seatHolds.tryHold(eventId, seats, "token-a", TTL)).isEqualTo(HoldResult.ACQUIRED);

		for (String key : SeatHoldService.keys(eventId, seats)) {
			assertThat(redis.opsForValue().get(key)).isEqualTo("token-a");
			assertThat(redis.getExpire(key)).isBetween(1L, TTL.toSeconds());
		}
	}

	@Test
	void overlappingHoldIsRejectedAndHoldsNothing() {
		long eventId = IDS.incrementAndGet();
		long seat1 = IDS.incrementAndGet();
		long seat2 = IDS.incrementAndGet();
		long seat3 = IDS.incrementAndGet();
		seatHolds.tryHold(eventId, List.of(seat1, seat2), "token-a", TTL);

		assertThat(seatHolds.tryHold(eventId, List.of(seat2, seat3), "token-b", TTL)).isEqualTo(HoldResult.REJECTED);

		// All or nothing: seat3 was free but must not have been held by the rejected request.
		assertThat(redis.hasKey(SeatHoldService.keys(eventId, List.of(seat3)).get(0))).isFalse();
	}

	@Test
	void releaseOnlyDeletesHoldsOwnedByTheToken() {
		long eventId = IDS.incrementAndGet();
		List<Long> seats = List.of(IDS.incrementAndGet());
		seatHolds.tryHold(eventId, seats, "token-a", TTL);

		seatHolds.release(eventId, seats, "token-b");
		assertThat(seatHolds.tryHold(eventId, seats, "token-c", TTL)).isEqualTo(HoldResult.REJECTED);

		seatHolds.release(eventId, seats, "token-a");
		assertThat(seatHolds.tryHold(eventId, seats, "token-c", TTL)).isEqualTo(HoldResult.ACQUIRED);
	}

	@Test
	void holdsExpireAfterTheTtl() throws InterruptedException {
		long eventId = IDS.incrementAndGet();
		List<Long> seats = List.of(IDS.incrementAndGet());

		seatHolds.tryHold(eventId, seats, "token-a", Duration.ofMillis(300));
		Thread.sleep(600);

		assertThat(seatHolds.tryHold(eventId, seats, "token-b", TTL)).isEqualTo(HoldResult.ACQUIRED);
	}

}
