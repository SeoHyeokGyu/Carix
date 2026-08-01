package com.carix.authservice.invitation;

import static org.assertj.core.api.Assertions.assertThat;

import com.carix.authservice.AbstractIntegrationTest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class InvitationServiceTest extends AbstractIntegrationTest {

	@Autowired
	private InvitationService invitationService;

	@Autowired
	private InvitationRepository invitationRepository;

	@Test
	void 초대_레코드가_있으면_초대된_이메일이다() {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		invitationRepository.save(new Invitation(UUID.randomUUID(), "invited@example.com", "PENDING", now.plusDays(7), now));

		assertThat(invitationService.isInvited("invited@example.com")).isTrue();
	}

	@Test
	void 초대_레코드가_없으면_초대되지_않은_이메일이다() {
		assertThat(invitationService.isInvited("stranger@example.com")).isFalse();
	}
}
