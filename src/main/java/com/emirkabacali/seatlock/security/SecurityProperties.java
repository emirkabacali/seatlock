package com.emirkabacali.seatlock.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Binds the {@code seatlock.security.*} section of application.yml. */
@ConfigurationProperties(prefix = "seatlock.security")
public record SecurityProperties(Jwt jwt, Admin admin) {

	/** HS256 needs a key of at least 256 bits. */
	static final int MIN_SECRET_BYTES = 32;

	public SecurityProperties {
		if (jwt == null || jwt.secret() == null || jwt.secret().getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
			throw new IllegalStateException("seatlock.security.jwt.secret (env JWT_SECRET) must be set and at least "
					+ MIN_SECRET_BYTES + " bytes long. See .env.example.");
		}
		if (admin == null) {
			admin = new Admin(null, null);
		}
	}

	public record Jwt(String secret, String issuer, Duration ttl) {

		public Jwt {
			if (issuer == null || issuer.isBlank()) {
				issuer = "seatlock";
			}
			if (ttl == null) {
				ttl = Duration.ofHours(1);
			}
		}

	}

	public record Admin(String email, String password) {

		public boolean isConfigured() {
			return email != null && !email.isBlank() && password != null && !password.isBlank();
		}

	}

}
