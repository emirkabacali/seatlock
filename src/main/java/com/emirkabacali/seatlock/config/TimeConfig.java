package com.emirkabacali.seatlock.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** A single injectable clock makes time-dependent code (token expiry, seat holds) testable. */
@Configuration
public class TimeConfig {

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

}
