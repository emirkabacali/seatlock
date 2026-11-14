package com.emirkabacali.seatlock.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.emirkabacali.seatlock.AbstractIntegrationTest;
import com.emirkabacali.seatlock.dto.booking.CreateBookingRequest;
import com.emirkabacali.seatlock.entity.BookingStatus;
import com.emirkabacali.seatlock.entity.SeatStatus;
import com.emirkabacali.seatlock.exception.ConflictException;
import com.emirkabacali.seatlock.repository.BookingRepository;
import com.emirkabacali.seatlock.repository.SeatRepository;
import com.emirkabacali.seatlock.support.TestData;
import com.emirkabacali.seatlock.support.TestData.OnSaleEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/**
 * Fires many booking requests for the same seats at exactly the same moment and checks how each
 * locking strategy behaves. This is the core guarantee of the project: a seat is never sold twice.
 */
@Import(TestData.class)
class BookingConcurrencyIntegrationTest extends AbstractIntegrationTest {

	private static final Logger log = LoggerFactory.getLogger(BookingConcurrencyIntegrationTest.class);
	private static final int THREADS = 30;
	private static final Set<BookingStatus> ACTIVE = Set.of(BookingStatus.PENDING, BookingStatus.CONFIRMED);

	@Autowired
	private BookingService bookingService;

	@Autowired
	private BookingRepository bookingRepository;

	@Autowired
	private SeatRepository seatRepository;

	@Autowired
	private TestData testData;

	/**
	 * Demonstrates the bug the other strategies fix. The race is timing dependent, so a few rounds
	 * are tried; in practice the first round almost always produces several "successful" bookings.
	 */
	@Test
	void naiveStrategyDoubleBooksTheSameSeat() throws Exception {
		int rounds = 5;
		OnSaleEvent event = testData.onSaleEventWithSeats(rounds);
		List<Long> users = testData.users(THREADS);

		int maxBookingsForOneSeat = 0;
		for (int round = 0; round < rounds && maxBookingsForOneSeat < 2; round++) {
			Long seatId = event.seatIds().get(round);
			Outcome outcome = race(LockingStrategy.NAIVE, event.eventId(), users, i -> List.of(seatId));
			long bookingsForSeat = bookingRepository.countBySeatIdAndStatusIn(seatId, ACTIVE);
			log.info("NAIVE round {}: {} successful bookings for seat {}", round + 1, outcome.successes, seatId);
			assertThat(outcome.unexpected).isEmpty();
			assertThat(bookingsForSeat).isEqualTo(outcome.successes);
			maxBookingsForOneSeat = (int) Math.max(maxBookingsForOneSeat, bookingsForSeat);
		}

		assertThat(maxBookingsForOneSeat)
				.as("the naive strategy should let more than one booking hold the same seat")
				.isGreaterThan(1);
	}

	@ParameterizedTest
	@EnumSource(value = LockingStrategy.class, names = { "OPTIMISTIC", "PESSIMISTIC" })
	void exactlyOneBookingWinsTheSameSeat(LockingStrategy strategy) throws Exception {
		OnSaleEvent event = testData.onSaleEventWithSeats(1);
		Long seatId = event.seatIds().get(0);
		List<Long> users = testData.users(THREADS);

		Outcome outcome = race(strategy, event.eventId(), users, i -> List.of(seatId));

		log.info("{}: {} success, {} conflicts", strategy, outcome.successes, outcome.conflicts);
		assertThat(outcome.unexpected).isEmpty();
		assertThat(outcome.successes).isEqualTo(1);
		assertThat(outcome.conflicts).isEqualTo(THREADS - 1);
		assertThat(bookingRepository.countBySeatIdAndStatusIn(seatId, ACTIVE)).isEqualTo(1);
		assertThat(seatRepository.findById(seatId).orElseThrow().getStatus()).isEqualTo(SeatStatus.HELD);
	}

	/**
	 * Each request wants two adjacent seats ({i, i+1}), so neighbouring requests overlap by one seat.
	 * Whatever the interleaving, no seat may end up in more than one booking.
	 */
	@ParameterizedTest
	@EnumSource(value = LockingStrategy.class, names = { "OPTIMISTIC", "PESSIMISTIC" })
	void overlappingMultiSeatBookingsNeverShareASeat(LockingStrategy strategy) throws Exception {
		OnSaleEvent event = testData.onSaleEventWithSeats(THREADS + 1);
		List<Long> seats = event.seatIds();
		List<Long> users = testData.users(THREADS);

		Outcome outcome = race(strategy, event.eventId(), users, i -> List.of(seats.get(i), seats.get(i + 1)));

		assertThat(outcome.unexpected).isEmpty();
		assertThat(outcome.successes).isPositive();
		assertThat(outcome.successes + outcome.conflicts).isEqualTo(THREADS);
		long heldSeats = 0;
		for (Long seatId : seats) {
			long bookings = bookingRepository.countBySeatIdAndStatusIn(seatId, ACTIVE);
			assertThat(bookings).as("bookings holding seat %d", seatId).isLessThanOrEqualTo(1);
			heldSeats += bookings;
		}
		assertThat(heldSeats).isEqualTo(2L * outcome.successes);
	}

	/**
	 * Starts {@link #THREADS} threads, lets them all wait on a latch, then releases them at once so
	 * the requests really overlap.
	 */
	private Outcome race(LockingStrategy strategy, Long eventId, List<Long> users, IntFunction<List<Long>> seatsFor)
			throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(THREADS);
		CountDownLatch ready = new CountDownLatch(THREADS);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Result>> futures = new ArrayList<>();
		try {
			for (int i = 0; i < THREADS; i++) {
				Long userId = users.get(i);
				CreateBookingRequest request = new CreateBookingRequest(eventId, seatsFor.apply(i));
				Callable<Result> task = () -> {
					ready.countDown();
					start.await();
					try {
						bookingService.create(userId, request, strategy);
						return Result.SUCCESS;
					}
					catch (ConflictException ex) {
						return Result.CONFLICT;
					}
				};
				futures.add(pool.submit(task));
			}
			ready.await(10, TimeUnit.SECONDS);
			start.countDown();

			Outcome outcome = new Outcome();
			for (Future<Result> future : futures) {
				try {
					switch (future.get(60, TimeUnit.SECONDS)) {
						case SUCCESS -> outcome.successes++;
						case CONFLICT -> outcome.conflicts++;
					}
				}
				catch (java.util.concurrent.ExecutionException ex) {
					outcome.unexpected.add(ex.getCause());
				}
			}
			return outcome;
		}
		finally {
			pool.shutdownNow();
		}
	}

	private enum Result {
		SUCCESS, CONFLICT
	}

	private static final class Outcome {

		int successes;

		int conflicts;

		final List<Throwable> unexpected = new ArrayList<>();

	}

}
