package com.carix.authservice.cleanup;

import com.carix.authservice.ratelimit.RateLimitService;
import com.carix.authservice.session.SessionRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 만료 세션과 끝난 속도 제한 창을 시간마다 지운다. 속도 제한 키에는 이메일·IP 가 평문으로 들어 있어 탈퇴한 사용자의 이메일도 창이 끝나면 여기서 사라진다.
 * auth-service 단일 인스턴스를 전제한다 — 여러 개 띄우면 중복 실행되지만 둘 다 멱등이다.
 */
@Component
class AuthCleanupJob {

  private static final Logger log = LoggerFactory.getLogger(AuthCleanupJob.class);

  private final SessionRepository sessionRepository;
  private final RateLimitService rateLimitService;

  AuthCleanupJob(SessionRepository sessionRepository, RateLimitService rateLimitService) {
    this.sessionRepository = sessionRepository;
    this.rateLimitService = rateLimitService;
  }

  // 정각은 새 창이 막 열리는 때라 피한다. 지난 창은 정각부터 이미 끝난 상태다.
  @Scheduled(cron = "0 5 * * * *")
  void run() {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    int sessions = sessionRepository.deleteExpired(now);
    int buckets = rateLimitService.deleteExpiredWindows(now);
    log.info("인증 정리 완료: 만료 세션 {}행, 속도 제한 창 {}행 삭제", sessions, buckets);
  }
}
