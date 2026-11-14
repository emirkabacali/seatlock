package com.emirkabacali.seatlock.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class SecurityPropertiesTest {

	private static final String VALID_SECRET = "0123456789abcdef0123456789abcdef";

	@Test
	void rejectsMissingSecret() {
		assertThatThrownBy(() -> new SecurityProperties(new SecurityProperties.Jwt("", null, null), null))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("JWT_SECRET");
	}

	@Test
	void rejectsSecretShorterThan32Bytes() {
		assertThatThrownBy(() -> new SecurityProperties(new SecurityProperties.Jwt("too-short", null, null), null))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void appliesDefaultsForIssuerTtlAndAdmin() {
		SecurityProperties properties = new SecurityProperties(new SecurityProperties.Jwt(VALID_SECRET, null, null),
				null);

		assertThat(properties.jwt().issuer()).isEqualTo("seatlock");
		assertThat(properties.jwt().ttl()).isEqualTo(Duration.ofHours(1));
		assertThat(properties.admin().isConfigured()).isFalse();
	}

}
