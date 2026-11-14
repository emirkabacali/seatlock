package com.emirkabacali.seatlock.dto.auth;

public record TokenResponse(String accessToken, String tokenType, long expiresIn) {

	public static TokenResponse bearer(String accessToken, long expiresInSeconds) {
		return new TokenResponse(accessToken, "Bearer", expiresInSeconds);
	}

}
