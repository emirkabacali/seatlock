package com.emirkabacali.seatlock.config;

import com.emirkabacali.seatlock.service.LockingStrategy;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Binds the {@code seatlock.booking.*} section of application.yml. */
@ConfigurationProperties(prefix = "seatlock.booking")
public record BookingProperties(Duration holdDuration, LockingStrategy lockingStrategy, Integer maxSeatsPerBooking,
		ExpiryJob expiryJob) {

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
		if (expiryJob == null) {
			expiryJob = new ExpiryJob(true, null, null);
		}
	}

	public record ExpiryJob(boolean enabled, Duration interval, Integer batchSize) {

		public ExpiryJob {
			if (interval == null) {
				interval = Duration.ofSeconds(30);
			}
			if (batchSize == null) {
				batchSize = 100;
			}
		}

	}

}
