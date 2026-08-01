package com.carix.authservice.invitation;

import java.util.Locale;
import org.springframework.stereotype.Service;

/**
 * "초대됨"의 정의: {@code invitation} 행이 존재하는지만 본다 (status 무관).
 *
 * <p>초대 생성(운영자 명령)은 이 Phase 범위 밖이다. status 별 세분화가 필요해지면
 * (예: EXPIRED 초대는 재로그인도 막을지) 이 메서드만 바꾸면 된다 — 호출부는 영향받지 않는다.
 */
@Service
public class InvitationService {

	private final InvitationRepository invitationRepository;

	public InvitationService(InvitationRepository invitationRepository) {
		this.invitationRepository = invitationRepository;
	}

	public boolean isInvited(String email) {
		return invitationRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT)).isPresent();
	}
}
