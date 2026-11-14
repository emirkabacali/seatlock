package com.emirkabacali.seatlock.service;

import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * Simulated provider with predictable test payment methods (similar to Stripe's test cards):
 * {@code pm_card_declined} and {@code pm_card_insufficient_funds} fail, anything else succeeds.
 * No real money is ever moved.
 */
@Component
public class SimulatedPaymentGateway implements PaymentGateway {

	public static final String DECLINED = "pm_card_declined";
	public static final String INSUFFICIENT_FUNDS = "pm_card_insufficient_funds";

	@Override
	public ChargeResult charge(BigDecimal amount, String paymentMethod) {
		return switch (paymentMethod) {
			case DECLINED -> ChargeResult.declined("card_declined");
			case INSUFFICIENT_FUNDS -> ChargeResult.declined("insufficient_funds");
			default -> ChargeResult.success();
		};
	}

}
