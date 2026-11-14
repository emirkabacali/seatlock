package com.emirkabacali.seatlock.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.emirkabacali.seatlock.AbstractIntegrationTest;
import com.emirkabacali.seatlock.dto.booking.BookingResponse;
import com.emirkabacali.seatlock.dto.booking.CreateBookingRequest;
import com.emirkabacali.seatlock.dto.payment.PaymentRequest;
import com.emirkabacali.seatlock.entity.BookingStatus;
import com.emirkabacali.seatlock.entity.PaymentStatus;
import com.emirkabacali.seatlock.exception.ConflictException;
import com.emirkabacali.seatlock.repository.BookingRepository;
import com.emirkabacali.seatlock.repository.PaymentRepository;
import com.emirkabacali.seatlock.support.TestData;
import com.emirkabacali.seatlock.support.TestData.OnSaleEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/** Concurrent payment attempts for the same booking must never charge twice. */
@Import(TestData.class)
class PaymentConcurrencyIntegrationTest extends AbstractIntegrationTest {

	private static final int THREADS = 10;
	private static final PaymentRequest VISA = new PaymentRequest("pm_card_visa");

	@Autowired
	private PaymentService paymentService;

	@Autowired
	private BookingService bookingService;

	@Autowired
	private PaymentRepository paymentRepository;

	@Autowired
	private BookingRepository bookingRepository;

	@Autowired
	private TestData testData;

	/** A double-clicked "Pay" button or an automatic client retry: same key, many requests at once. */
	@Test
	void concurrentRetriesWithTheSameKeyChargeOnceAndReturnTheSameResult() throws Exception {
		Long userId = testData.users(1).get(0);
		BookingResponse booking = newBooking(userId);
		String key = UUID.randomUUID().toString();

		List<Object> results = race(i -> () -> paymentService.pay(userId, booking.id(), key, VISA));

		assertThat(results).allMatch(PaymentService.PaymentResult.class::isInstance);
		List<PaymentService.PaymentResult> payments = results.stream()
				.map(PaymentService.PaymentResult.class::cast).toList();
		assertThat(payments).extracting(result -> result.payment().id()).containsOnly(payments.get(0).payment().id());
		assertThat(payments).filteredOn(result -> !result.replayed()).hasSize(1);
		assertThat(paymentRepository.countByBookingId(booking.id())).isEqualTo(1);
		assertThat(bookingRepository.findById(booking.id()).orElseThrow().getStatus())
				.isEqualTo(BookingStatus.CONFIRMED);
	}

	/** Different keys for the same booking (e.g. two browser tabs): exactly one payment succeeds. */
	@Test
	void concurrentPaymentsWithDifferentKeysSucceedOnlyOnce() throws Exception {
		Long userId = testData.users(1).get(0);
		BookingResponse booking = newBooking(userId);

		List<Object> results = race(i -> () -> paymentService.pay(userId, booking.id(), "key-" + i + "-" + UUID.randomUUID(),
				VISA));

		long succeeded = results.stream().filter(PaymentService.PaymentResult.class::isInstance).count();
		long conflicts = results.stream().filter(ConflictException.class::isInstance).count();
		assertThat(succeeded).isEqualTo(1);
		assertThat(conflicts).isEqualTo(THREADS - 1);
		assertThat(paymentRepository.countByBookingIdAndStatus(booking.id(), PaymentStatus.SUCCEEDED)).isEqualTo(1);
		assertThat(paymentRepository.countByBookingId(booking.id())).isEqualTo(1);
	}

	private BookingResponse newBooking(Long userId) {
		OnSaleEvent event = testData.onSaleEventWithSeats(2);
		return bookingService.create(userId, new CreateBookingRequest(event.eventId(), event.seatIds()));
	}

	/** Runs the tasks at the same moment; returns each result or the exception it threw. */
	private List<Object> race(IntFunction<Callable<Object>> taskFor) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(THREADS);
		CountDownLatch ready = new CountDownLatch(THREADS);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Object>> futures = new ArrayList<>();
		try {
			for (int i = 0; i < THREADS; i++) {
				Callable<Object> task = taskFor.apply(i);
				futures.add(pool.submit(() -> {
					ready.countDown();
					start.await();
					try {
						return task.call();
					}
					catch (RuntimeException ex) {
						return ex;
					}
				}));
			}
			ready.await(10, TimeUnit.SECONDS);
			start.countDown();
			List<Object> results = new ArrayList<>();
			for (Future<Object> future : futures) {
				results.add(future.get(60, TimeUnit.SECONDS));
			}
			return results;
		}
		finally {
			pool.shutdownNow();
		}
	}

}
