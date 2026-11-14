package com.emirkabacali.seatlock.service;

import java.math.BigDecimal;

/** Abstraction over a payment provider, so a real one (e.g. Stripe) could replace the simulation. */
public interface PaymentGateway {

	ChargeResult charge(BigDecimal amount, String paymentMethod);

	record ChargeResult(boolean succeeded, String failureReason) {

		public static ChargeResult success() {
			return new ChargeResult(true, null);
		}

		public static ChargeResult declined(String reason) {
			return new ChargeResult(false, reason);
		}

	}

}
