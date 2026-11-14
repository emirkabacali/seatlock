package com.emirkabacali.seatlock.service;

import com.emirkabacali.seatlock.dto.payment.PaymentRequest;
import com.emirkabacali.seatlock.dto.payment.PaymentResponse;
import com.emirkabacali.seatlock.entity.Booking;
import com.emirkabacali.seatlock.entity.Payment;
import com.emirkabacali.seatlock.entity.Seat;
import com.emirkabacali.seatlock.exception.BadRequestException;
import com.emirkabacali.seatlock.exception.ConflictException;
import com.emirkabacali.seatlock.exception.IdempotencyKeyReuseException;
import com.emirkabacali.seatlock.exception.ResourceNotFoundException;
import com.emirkabacali.seatlock.repository.BookingRepository;
import com.emirkabacali.seatlock.repository.PaymentRepository;
import com.emirkabacali.seatlock.service.PaymentGateway.ChargeResult;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Idempotent (simulated) payment of a booking.
 *
 * <p>The same user sending the same {@code Idempotency-Key} again gets the stored result of the
 * first attempt instead of a second charge. The booking row is locked first, so even concurrent
 * retries are processed one after another.
 */
@Service
public class PaymentService {

	static final int MAX_KEY_LENGTH = 255;

	private final BookingRepository bookingRepository;
	private final PaymentRepository paymentRepository;
	private final PaymentGateway paymentGateway;
	private final SeatHoldService seatHolds;
	private final Clock clock;

	public PaymentService(BookingRepository bookingRepository, PaymentRepository paymentRepository,
			PaymentGateway paymentGateway, SeatHoldService seatHolds, Clock clock) {
		this.bookingRepository = bookingRepository;
		this.paymentRepository = paymentRepository;
		this.paymentGateway = paymentGateway;
		this.seatHolds = seatHolds;
		this.clock = clock;
	}

	@Transactional
	public PaymentResult pay(Long userId, Long bookingId, String idempotencyKey, PaymentRequest request) {
		validateKey(idempotencyKey);
		String requestHash = hash(bookingId + "|" + request.paymentMethod());

		// 1. Serialize all attempts for this booking.
		Booking booking = bookingRepository.findByIdAndUserIdForUpdate(bookingId, userId)
				.orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));

		// 2. Same key seen before? Return the stored result (or reject a different request with that key).
		Optional<Payment> previous = paymentRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
		if (previous.isPresent()) {
			if (!previous.get().matchesRequest(requestHash)) {
				throw new IdempotencyKeyReuseException();
			}
			return new PaymentResult(PaymentResponse.from(previous.get()), true);
		}

		// 3. Only an unexpired PENDING booking can be paid.
		if (!booking.isPending()) {
			throw new ConflictException("Booking " + bookingId + " is " + booking.getStatus() + " and cannot be paid");
		}
		if (booking.isExpiredAt(clock.instant())) {
			throw new ConflictException("The hold for booking " + bookingId + " has expired");
		}

		// 4. Charge and record the attempt (successful or not).
		ChargeResult charge = paymentGateway.charge(booking.getTotalPrice(), request.paymentMethod());
		Payment payment;
		if (charge.succeeded()) {
			payment = Payment.succeeded(booking, userId, idempotencyKey, requestHash);
			booking.confirm();
			releaseRedisHoldsAfterCommit(booking);
		}
		else {
			// The booking stays PENDING: the user may retry with a new Idempotency-Key until the hold expires.
			payment = Payment.failed(booking, userId, idempotencyKey, requestHash, charge.failureReason());
		}
		return new PaymentResult(PaymentResponse.from(paymentRepository.save(payment)), false);
	}

	/** Seats are BOOKED in the database now; the Redis holds are no longer needed. */
	private void releaseRedisHoldsAfterCommit(Booking booking) {
		Long eventId = booking.getEvent().getId();
		List<Long> seatIds = booking.getSeats().stream().map(Seat::getId).toList();
		String token = booking.getHoldToken();
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				seatHolds.release(eventId, seatIds, token);
			}
		});
	}

	private static void validateKey(String key) {
		if (key == null || key.isBlank() || key.length() > MAX_KEY_LENGTH) {
			throw new BadRequestException("Idempotency-Key header must be 1 to " + MAX_KEY_LENGTH + " characters");
		}
	}

	private static String hash(String value) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 not available", ex);
		}
	}

	/** {@code replayed} is true when the response comes from an earlier request with the same key. */
	public record PaymentResult(PaymentResponse payment, boolean replayed) {
	}

}
