package com.emirkabacali.seatlock.repository;

import com.emirkabacali.seatlock.entity.Payment;
import com.emirkabacali.seatlock.entity.PaymentStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

	Optional<Payment> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

	long countByBookingId(Long bookingId);

	long countByBookingIdAndStatus(Long bookingId, PaymentStatus status);

}
