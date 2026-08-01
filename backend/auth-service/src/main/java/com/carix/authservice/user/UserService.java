package com.carix.authservice.user;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class UserService {

	private final UserRepository userRepository;

	public UserService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	public User findOrCreate(String email) {
		return userRepository.findByEmail(email)
				.orElseGet(() -> {
					OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
					User user = new User(UUID.randomUUID(), email, "PROFILE", now, now);
					return userRepository.save(user);
				});
	}
}
