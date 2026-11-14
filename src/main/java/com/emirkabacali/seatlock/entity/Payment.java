package com.emirkabacali.seatlock.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** A (simulated) payment attempt for a booking. Doubles as the idempotency record of the request. */
@Entity
@Table(name = "payments")
public class Payment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "booking_id", nullable = false)
	private Booking booking;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "idempotency_key", nullable = false, length = 255)
	private String idempotencyKey;

	@Column(name = "request_hash", nullable = false, length = 64)
	private String requestHash;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal amount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PaymentStatus status;

	@Column(name = "failure_reason", length = 255)
	private String failureReason;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	/** Required by JPA. */
	protected Payment() {
	}

	private Payment(Booking booking, Long userId, String idempotencyKey, String requestHash, PaymentStatus status,
			String failureReason) {
		this.booking = booking;
		this.userId = userId;
		this.idempotencyKey = idempotencyKey;
		this.requestHash = requestHash;
		this.amount = booking.getTotalPrice();
		this.status = status;
		this.failureReason = failureReason;
	}

	public static Payment succeeded(Booking booking, Long userId, String idempotencyKey, String requestHash) {
		return new Payment(booking, userId, idempotencyKey, requestHash, PaymentStatus.SUCCEEDED, null);
	}

	public static Payment failed(Booking booking, Long userId, String idempotencyKey, String requestHash,
			String reason) {
		return new Payment(booking, userId, idempotencyKey, requestHash, PaymentStatus.FAILED, reason);
	}

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
	}

	public boolean matchesRequest(String hash) {
		return requestHash.equals(hash);
	}

	public Long getId() {
		return id;
	}

	public Booking getBooking() {
		return booking;
	}

	public Long getUserId() {
		return userId;
	}

	public String getIdempotencyKey() {
		return idempotencyKey;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public PaymentStatus getStatus() {
		return status;
	}

	public String getFailureReason() {
		return failureReason;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
