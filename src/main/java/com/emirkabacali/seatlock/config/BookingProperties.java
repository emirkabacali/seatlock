package com.emirkabacali.seatlock.config;

import com.emirkabacali.seatlock.service.LockingStrategy;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Binds the {@code seatlock.booking.*} section of application.yml. */
@ConfigurationProperties(prefix = "seatlock.booking")
public record BookingProperties(Duration holdDuration, LockingStrategy lockingStrategy, Integer maxSeatsPerBooking) {

	public BookingProperties {
		if (holdDuration == null) {
			holdDuration = Duration.ofMinutes(10);
		}
		if (lockingStrategy == null) {
			lockingStrategy = LockingStrategy.OPTIMISTIC;
		}
		if (maxSeatsPerBooking == null) {
			maxSeatsPerBooking = 10;
		}
	}

}
