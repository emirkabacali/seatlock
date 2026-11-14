package com.emirkabacali.seatlock.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.emirkabacali.seatlock.AbstractIntegrationTest;
import com.emirkabacali.seatlock.dto.booking.BookingResponse;
import com.emirkabacali.seatlock.dto.booking.CreateBookingRequest;
import com.emirkabacali.seatlock.service.BookingService;
import com.emirkabacali.seatlock.support.TestData;
import com.emirkabacali.seatlock.support.TestData.OnSaleEvent;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

@Import(TestData.class)
class PaymentApiIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private TestData testData;

	@Autowired
	private BookingService bookingService;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void successfulPaymentConfirmsBookingAndBooksSeats() throws Exception {
		long userId = testData.users(1).get(0);
		OnSaleEvent event = testData.onSaleEventWithSeats(2);
		BookingResponse booking = book(userId, event);

		pay(userId, booking.id(), newKey(), "pm_card_visa")
				.andExpect(status().isCreated())
				.andExpect(header().string("Idempotent-Replayed", "false"))
				.andExpect(jsonPath("$.status").value("SUCCEEDED"))
				.andExpect(jsonPath("$.amount").value(200.00))
				.andExpect(jsonPath("$.bookingId").value(booking.id()));

		mockMvc.perform(get("/api/v1/bookings/{id}", booking.id()).with(asUser(userId)))
				.andExpect(jsonPath("$.status").value("CONFIRMED"));
		mockMvc.perform(get("/api/v1/events/{id}/seats", event.eventId()).param("status", "BOOKED"))
				.andExpect(jsonPath("$", hasSize(2)));
	}

	@Test
	void retryWithTheSameKeyReturnsTheOriginalResponseWithoutChargingAgain() throws Exception {
		long userId = testData.users(1).get(0);
		BookingResponse booking = book(userId, testData.onSaleEventWithSeats(1));
		String key = newKey();

		String first = pay(userId, booking.id(), key, "pm_card_visa")
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();

		pay(userId, booking.id(), key, "pm_card_visa")
				.andExpect(status().isCreated())
				.andExpect(header().string("Idempotent-Replayed", "true"))
				.andExpect(content().json(first, true));

		Long payments = jdbc.queryForObject("select count(*) from payments where booking_id = ?", Long.class,
				booking.id());
		org.assertj.core.api.Assertions.assertThat(payments).isEqualTo(1);
	}

	@Test
	void reusingAKeyForADifferentRequestReturns422() throws Exception {
		long userId = testData.users(1).get(0);
		BookingResponse booking = book(userId, testData.onSaleEventWithSeats(1));
		String key = newKey();
		pay(userId, booking.id(), key, "pm_card_declined").andExpect(status().isCreated());

		pay(userId, booking.id(), key, "pm_card_visa")
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.title").value("Idempotency key reused"));
	}

	@Test
	void declinedPaymentKeepsBookingPendingAndANewKeyCanRetry() throws Exception {
		long userId = testData.users(1).get(0);
		BookingResponse booking = book(userId, testData.onSaleEventWithSeats(1));
		String declinedKey = newKey();

		pay(userId, booking.id(), declinedKey, "pm_card_declined")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("FAILED"))
				.andExpect(jsonPath("$.failureReason").value("card_declined"));
		mockMvc.perform(get("/api/v1/bookings/{id}", booking.id()).with(asUser(userId)))
				.andExpect(jsonPath("$.status").value("PENDING"));

		// Replaying the failed attempt returns the same failure (it does not retry the charge) ...
		pay(userId, booking.id(), declinedKey, "pm_card_declined")
				.andExpect(header().string("Idempotent-Replayed", "true"))
				.andExpect(jsonPath("$.status").value("FAILED"));
		// ... while a new key is a new attempt.
		pay(userId, booking.id(), newKey(), "pm_card_visa")
				.andExpect(jsonPath("$.status").value("SUCCEEDED"));
	}

	@Test
	void paidBookingCannotBePaidAgainWithANewKeyOrCancelled() throws Exception {
		long userId = testData.users(1).get(0);
		BookingResponse booking = book(userId, testData.onSaleEventWithSeats(1));
		pay(userId, booking.id(), newKey(), "pm_card_visa").andExpect(status().isCreated());

		pay(userId, booking.id(), newKey(), "pm_card_visa").andExpect(status().isConflict());
		mockMvc.perform(post("/api/v1/bookings/{id}/cancel", booking.id()).with(asUser(userId)))
				.andExpect(status().isConflict());
	}

	@Test
	void expiredHoldCannotBePaid() throws Exception {
		long userId = testData.users(1).get(0);
		BookingResponse booking = book(userId, testData.onSaleEventWithSeats(1));
		jdbc.update("update bookings set expires_at = now() - interval '1 second' where id = ?", booking.id());

		pay(userId, booking.id(), newKey(), "pm_card_visa")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail").value("The hold for booking " + booking.id() + " has expired"));
	}

	@Test
	void paymentRequiresIdempotencyKeyAndOwnership() throws Exception {
		List<Long> users = testData.users(2);
		BookingResponse booking = book(users.get(0), testData.onSaleEventWithSeats(1));

		mockMvc.perform(post("/api/v1/bookings/{id}/pay", booking.id()).with(asUser(users.get(0)))
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"paymentMethod": "pm_card_visa"}
						"""))
				.andExpect(status().isBadRequest());

		pay(users.get(1), booking.id(), newKey(), "pm_card_visa").andExpect(status().isNotFound());
	}

	private BookingResponse book(long userId, OnSaleEvent event) {
		return bookingService.create(userId, new CreateBookingRequest(event.eventId(), event.seatIds()));
	}

	private ResultActions pay(long userId, long bookingId, String key, String paymentMethod) throws Exception {
		return mockMvc.perform(post("/api/v1/bookings/{id}/pay", bookingId).with(asUser(userId))
				.header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"paymentMethod": "%s"}
						""".formatted(paymentMethod)));
	}

	private static String newKey() {
		return UUID.randomUUID().toString();
	}

}
