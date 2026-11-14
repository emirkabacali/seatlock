package com.emirkabacali.seatlock.dto.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code paymentMethod}: a simulated payment method, e.g. "pm_card_visa" or "pm_card_declined". */
public record PaymentRequest(@NotBlank @Size(max = 100) String paymentMethod) {
}
