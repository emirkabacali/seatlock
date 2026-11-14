package com.emirkabacali.seatlock.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs {@link BookingExpiryService} periodically. Disable with {@code seatlock.booking.expiry-job.enabled=false}. */
@Component
@ConditionalOnProperty(prefix = "seatlock.booking.expiry-job", name = "enabled", havingValue = "true",
		matchIfMissing = true)
public class BookingExpiryJob {

	private final BookingExpiryService expiryService;

	public BookingExpiryJob(BookingExpiryService expiryService) {
		this.expiryService = expiryService;
	}

	/** Keeps expiring batches until none are left, so a backlog is cleared in one run. */
	@Scheduled(fixedDelayString = "${seatlock.booking.expiry-job.interval:PT30S}",
			initialDelayString = "${seatlock.booking.expiry-job.interval:PT30S}")
	public void run() {
		int expired;
		do {
			expired = expiryService.expireOverdueBookings();
		}
		while (expired > 0);
	}

}
