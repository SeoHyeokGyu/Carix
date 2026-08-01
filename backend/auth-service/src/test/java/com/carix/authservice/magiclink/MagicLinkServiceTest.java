package com.carix.authservice.magiclink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carix.authservice.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class MagicLinkServiceTest extends AbstractIntegrationTest {

	@Autowired
	private MagicLinkService magicLinkService;

	@Test
	void 발급한_토큰으로_검증하면_같은_사용자_ID를_반환한다() {
		UUID userId = UUID.randomUUID();
		String rawToken = magicLinkService.issueToken(userId);

		UUID verifiedUserId = magicLinkService.verifyToken(rawToken);

		assertThat(verifiedUserId).isEqualTo(userId);
	}

	@Test
	void 같은_토큰을_두_번_검증하면_실패한다() {
		UUID userId = UUID.randomUUID();
		String rawToken = magicLinkService.issueToken(userId);
		magicLinkService.verifyToken(rawToken);

		assertThatThrownBy(() -> magicLinkService.verifyToken(rawToken))
				.isInstanceOf(MagicLinkInvalidException.class);
	}

	@Test
	void 존재하지_않는_토큰은_실패한다() {
		assertThatThrownBy(() -> magicLinkService.verifyToken("no-such-token"))
				.isInstanceOf(MagicLinkInvalidException.class);
	}
}
