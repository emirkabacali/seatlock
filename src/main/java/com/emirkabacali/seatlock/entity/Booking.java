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
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "bookings")
public class Booking {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "event_id", nullable = false)
	private Event event;

	@ManyToMany(fetch = FetchType.LAZY)
	@JoinTable(name = "booking_seats",
			joinColumns = @JoinColumn(name = "booking_id"),
			inverseJoinColumns = @JoinColumn(name = "seat_id"))
	@OrderBy("id")
	private List<Seat> seats = new ArrayList<>();

	@Column(name = "total_price", nullable = false, precision = 12, scale = 2)
	private BigDecimal totalPrice;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private BookingStatus status;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Version
	@Column(nullable = false)
	private long version;

	/** Required by JPA. */
	protected Booking() {
	}

	public Booking(User user, Event event, List<Seat> seats, Instant expiresAt) {
		this.user = user;
		this.event = event;
		this.seats = new ArrayList<>(seats);
		this.totalPrice = seats.stream().map(Seat::getPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
		this.status = BookingStatus.PENDING;
		this.expiresAt = expiresAt;
	}

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
	}

	/** Cancels a PENDING booking and releases its seats. */
	public void cancel() {
		requirePending("cancelled");
		seats.forEach(Seat::release);
		this.status = BookingStatus.CANCELLED;
	}

	public boolean isPending() {
		return status == BookingStatus.PENDING;
	}

	private void requirePending(String action) {
		if (!isPending()) {
			throw new IllegalStateException("Booking " + id + " is " + status + " and cannot be " + action);
		}
	}

	public Long getId() {
		return id;
	}

	public User getUser() {
		return user;
	}

	public Event getEvent() {
		return event;
	}

	public List<Seat> getSeats() {
		return Collections.unmodifiableList(seats);
	}

	public BigDecimal getTotalPrice() {
		return totalPrice;
	}

	public BookingStatus getStatus() {
		return status;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
