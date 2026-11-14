package com.emirkabacali.seatlock.dto.auth;

import com.emirkabacali.seatlock.entity.Role;
import com.emirkabacali.seatlock.entity.User;
import java.time.Instant;

/** Public view of a user: never contains the password hash. */
public record UserResponse(Long id, String email, Role role, Instant createdAt) {

	public static UserResponse from(User user) {
		return new UserResponse(user.getId(), user.getEmail(), user.getRole(), user.getCreatedAt());
	}

}
