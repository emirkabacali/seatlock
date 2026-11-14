package com.emirkabacali.seatlock.service;

import com.emirkabacali.seatlock.dto.auth.LoginRequest;
import com.emirkabacali.seatlock.dto.auth.RegisterRequest;
import com.emirkabacali.seatlock.dto.auth.TokenResponse;
import com.emirkabacali.seatlock.dto.auth.UserResponse;
import com.emirkabacali.seatlock.entity.Role;
import com.emirkabacali.seatlock.entity.User;
import com.emirkabacali.seatlock.exception.ConflictException;
import com.emirkabacali.seatlock.repository.UserRepository;
import com.emirkabacali.seatlock.security.TokenService;
import java.util.Locale;
import java.util.Optional;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final TokenService tokenService;

	/**
	 * Compared against when the email is unknown, so that a login attempt takes about as long
	 * whether or not the account exists (makes it harder to discover registered emails by timing).
	 */
	private final String dummyHash;

	public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, TokenService tokenService) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.tokenService = tokenService;
		this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
	}

	@Transactional
	public UserResponse register(RegisterRequest request) {
		String email = normalize(request.email());
		if (userRepository.existsByEmail(email)) {
			throw new ConflictException("An account with this email already exists");
		}
		User user = new User(email, passwordEncoder.encode(request.password()), Role.USER);
		return UserResponse.from(userRepository.save(user));
	}

	@Transactional(readOnly = true)
	public TokenResponse login(LoginRequest request) {
		Optional<User> user = userRepository.findByEmail(normalize(request.email()));
		String hash = user.map(User::getPasswordHash).orElse(dummyHash);
		boolean passwordMatches = passwordEncoder.matches(request.password(), hash);
		if (user.isEmpty() || !passwordMatches) {
			// Same message for both cases: do not reveal which part was wrong.
			throw new BadCredentialsException("Invalid email or password");
		}
		TokenService.IssuedToken token = tokenService.issue(user.get());
		return TokenResponse.bearer(token.value(), token.expiresInSeconds());
	}

	private static String normalize(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

}
