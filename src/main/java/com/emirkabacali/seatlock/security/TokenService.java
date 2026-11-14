package com.emirkabacali.seatlock.security;

import com.emirkabacali.seatlock.entity.User;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Issues signed access tokens (JWT, HS256). Validation is done by Spring Security's JwtDecoder. */
@Service
public class TokenService {

	public static final String ROLES_CLAIM = "roles";
	public static final String EMAIL_CLAIM = "email";

	private final JwtEncoder jwtEncoder;
	private final SecurityProperties.Jwt properties;
	private final Clock clock;

	public TokenService(JwtEncoder jwtEncoder, SecurityProperties securityProperties, Clock clock) {
		this.jwtEncoder = jwtEncoder;
		this.properties = securityProperties.jwt();
		this.clock = clock;
	}

	public IssuedToken issue(User user) {
		Instant now = clock.instant();
		Instant expiresAt = now.plus(properties.ttl());
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(properties.issuer())
				.subject(user.getId().toString())
				.issuedAt(now)
				.expiresAt(expiresAt)
				.claim(EMAIL_CLAIM, user.getEmail())
				.claim(ROLES_CLAIM, List.of(user.getRole().name()))
				.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
		return new IssuedToken(token, properties.ttl().toSeconds());
	}

	public record IssuedToken(String value, long expiresInSeconds) {
	}

}
