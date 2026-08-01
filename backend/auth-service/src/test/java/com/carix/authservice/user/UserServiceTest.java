package com.carix.authservice.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.carix.authservice.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class UserServiceTest extends AbstractIntegrationTest {

	@Autowired
	private UserService userService;

	@Test
	void 없는_이메일이면_새_사용자를_생성한다() {
		User user = userService.findOrCreate("new@example.com");

		assertThat(user.getId()).isNotNull();
		assertThat(user.getEmail()).isEqualTo("new@example.com");
		assertThat(user.getOnboardingStep()).isEqualTo("PROFILE");
		assertThat(user.isOnboardingComplete()).isFalse();
	}

	@Test
	void 있는_이메일이면_기존_사용자를_반환한다() {
		User first = userService.findOrCreate("existing@example.com");
		User second = userService.findOrCreate("existing@example.com");

		assertThat(second.getId()).isEqualTo(first.getId());
	}
}
