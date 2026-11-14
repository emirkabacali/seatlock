package com.emirkabacali.seatlock.security;

import com.emirkabacali.seatlock.entity.Role;
import com.emirkabacali.seatlock.entity.User;
import com.emirkabacali.seatlock.repository.UserRepository;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public registration only creates USER accounts. The first ADMIN is created on startup from
 * {@code ADMIN_EMAIL} / {@code ADMIN_PASSWORD} if both are set and the account does not exist yet.
 */
@Component
public class AdminAccountInitializer implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AdminAccountInitializer.class);

	private final SecurityProperties.Admin admin;
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	public AdminAccountInitializer(SecurityProperties properties, UserRepository userRepository,
			PasswordEncoder passwordEncoder) {
		this.admin = properties.admin();
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (!admin.isConfigured()) {
			return;
		}
		String email = admin.email().trim().toLowerCase(Locale.ROOT);
		if (userRepository.existsByEmail(email)) {
			return;
		}
		userRepository.save(new User(email, passwordEncoder.encode(admin.password()), Role.ADMIN));
		log.info("Created admin account {}", email);
	}

}
