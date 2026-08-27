package com.carix.authservice.invitation;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "초대됨"의 정의: {@code invitation} 행이 존재하는지만 본다 (status 무관).
 *
 * <p>status 별 세분화가 필요해지면 (예: EXPIRED 초대는 재로그인도 막을지) {@link #isInvited(String)} 만 바꾸면 된다 — 호출부는 영향받지 않는다.
 * 같은 이유로 {@code expires_at} 도 지금은 판정에 쓰지 않는다. 값은 남기되 만료 정책이 정해질 때 이 클래스에서만 반영한다.
 */
@Service
public class InvitationService {

  /** 초대 유효기간. 현재 판정에는 쓰이지 않으나 컬럼이 NOT NULL 이라 값이 필요하다. */
  private static final Duration DEFAULT_VALIDITY = Duration.ofDays(90);

  private final InvitationRepository invitationRepository;

  public InvitationService(InvitationRepository invitationRepository) {
    this.invitationRepository = invitationRepository;
  }

  public boolean isInvited(String email) {
    return invitationRepository.findByEmail(normalize(email)).isPresent();
  }

  /**
   * 초대를 등록한다. 이미 초대된 이메일은 건너뛴다 — 같은 목록을 다시 넣어도 결과가 같다.
   *
   * @return 새로 만든 이메일과 건너뛴 이메일
   */
  @Transactional
  public InvitationRegistrationResponse register(List<String> emails) {
    OffsetDateTime expiresAt = OffsetDateTime.now().plus(DEFAULT_VALIDITY);
    List<String> created = new ArrayList<>();
    List<String> skipped = new ArrayList<>();

    // 요청 안의 중복부터 접는다 — 접지 않으면 같은 이메일이 created 와 skipped 에 함께 실린다.
    for (String email : new LinkedHashSet<>(emails.stream().map(this::normalize).toList())) {
      if (invitationRepository.findByEmail(email).isPresent()) {
        skipped.add(email);
        continue;
      }
      invitationRepository.save(Invitation.pending(email, expiresAt));
      created.add(email);
    }

    return new InvitationRegistrationResponse(created, skipped);
  }

  public List<InvitationSummary> findAll() {
    return invitationRepository.findAllByOrderByCreatedAtAsc().stream()
        .map(InvitationSummary::from)
        .toList();
  }

  private String normalize(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }
}
