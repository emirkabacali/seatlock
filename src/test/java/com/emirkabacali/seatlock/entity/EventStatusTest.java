package com.emirkabacali.seatlock.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class EventStatusTest {

	@ParameterizedTest(name = "{0} -> {1} allowed: {2}")
	@CsvSource({
			"DRAFT, ON_SALE, true",
			"DRAFT, CANCELLED, true",
			"DRAFT, FINISHED, false",
			"DRAFT, DRAFT, false",
			"ON_SALE, CANCELLED, true",
			"ON_SALE, FINISHED, true",
			"ON_SALE, DRAFT, false",
			"CANCELLED, ON_SALE, false",
			"FINISHED, ON_SALE, false",
			"FINISHED, CANCELLED, false" })
	void enforcesLifecycleTransitions(EventStatus from, EventStatus to, boolean allowed) {
		assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
	}

}
