package com.emirkabacali.seatlock.dto.payment;

import com.emirkabacali.seatlock.entity.Payment;
import com.emirkabacali.seatlock.entity.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Built only from the stored payment, so replaying the same Idempotency-Key always returns the
 * same body, even if the booking changes later.
 */
public record PaymentResponse(Long id, Long bookingId, BigDecimal amount, PaymentStatus status, String failureReason,
		Instant createdAt) {

	public static PaymentResponse from(Payment payment) {
		return new PaymentResponse(payment.getId(), payment.getBooking().getId(), payment.getAmount(),
				payment.getStatus(), payment.getFailureReason(), payment.getCreatedAt());
	}

}
