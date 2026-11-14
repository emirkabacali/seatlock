package com.emirkabacali.seatlock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.emirkabacali.seatlock.dto.auth.LoginRequest;
import com.emirkabacali.seatlock.dto.auth.RegisterRequest;
import com.emirkabacali.seatlock.dto.auth.UserResponse;
import com.emirkabacali.seatlock.entity.Role;
import com.emirkabacali.seatlock.entity.User;
import com.emirkabacali.seatlock.exception.ConflictException;
import com.emirkabacali.seatlock.repository.UserRepository;
import com.emirkabacali.seatlock.security.TokenService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {

	// A real encoder with the lowest cost keeps the test fast while still hashing for real.
	private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
	private final UserRepository userRepository = mock(UserRepository.class);
	private final TokenService tokenService = mock(TokenService.class);
	private AuthService authService;

	@BeforeEach
	void setUp() {
		authService = new AuthService(userRepository, passwordEncoder, tokenService);
	}

	@Test
	void registerNormalizesEmailHashesPasswordAndAssignsUserRole() {
		when(userRepository.existsByEmail("jane@example.com")).thenReturn(false);
		when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		UserResponse response = authService.register(new RegisterRequest("  Jane@Example.COM ", "secret-password"));

		ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
		verify(userRepository).save(saved.capture());
		assertThat(response.email()).isEqualTo("jane@example.com");
		assertThat(response.role()).isEqualTo(Role.USER);
		assertThat(saved.getValue().getPasswordHash()).isNotEqualTo("secret-password").startsWith("$2");
		assertThat(passwordEncoder.matches("secret-password", saved.getValue().getPasswordHash())).isTrue();
	}

	@Test
	void registerRejectsExistingEmail() {
		when(userRepository.existsByEmail("jane@example.com")).thenReturn(true);

		assertThatThrownBy(() -> authService.register(new RegisterRequest("jane@example.com", "secret-password")))
				.isInstanceOf(ConflictException.class);
		verify(userRepository, never()).save(any());
	}

	@Test
	void loginIssuesTokenForCorrectPassword() {
		User user = new User("jane@example.com", passwordEncoder.encode("secret-password"), Role.USER);
		when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));
		when(tokenService.issue(user)).thenReturn(new TokenService.IssuedToken("jwt-value", 3600));

		var token = authService.login(new LoginRequest("Jane@example.com", "secret-password"));

		assertThat(token.accessToken()).isEqualTo("jwt-value");
		assertThat(token.tokenType()).isEqualTo("Bearer");
	}

	@Test
	void loginRejectsWrongPassword() {
		User user = new User("jane@example.com", passwordEncoder.encode("secret-password"), Role.USER);
		when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));

		assertThatThrownBy(() -> authService.login(new LoginRequest("jane@example.com", "wrong")))
				.isInstanceOf(BadCredentialsException.class);
		verify(tokenService, never()).issue(any());
	}

	@Test
	void loginRejectsUnknownEmailWithSameError() {
		when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> authService.login(new LoginRequest("ghost@example.com", "secret-password")))
				.isInstanceOf(BadCredentialsException.class)
				.hasMessage("Invalid email or password");
	}

}
