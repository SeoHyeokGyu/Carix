package com.carix.profile.guest;

import com.carix.profile.service.UserDataEraser;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 세션이 모두 만료된 게스트를 시간마다 지운다. 게스트 세션 TTL 은 24시간이라 만료 후 1시간 안에 지워진다. */
@Component
public class GuestCleanupJob {

  private static final Logger log = LoggerFactory.getLogger(GuestCleanupJob.class);
  private static final Duration GUEST_SESSION_TTL = Duration.ofHours(24);

  private final JdbcTemplate jdbcTemplate;
  private final UserDataEraser eraser;
  private final Clock clock;

  public GuestCleanupJob(JdbcTemplate jdbcTemplate, UserDataEraser eraser, Clock clock) {
    this.jdbcTemplate = jdbcTemplate;
    this.eraser = eraser;
    this.clock = clock;
  }

  // 트랜잭션은 사용자 한 명의 eraseGuest 단위다. 여기에 걸면 한 건 실패가 앞서 지운 건을 롤백한다.
  @Scheduled(cron = "0 0 * * * *")
  public void run() {
    int erased = 0;
    for (UUID userId : findExpiredGuests(OffsetDateTime.now(clock))) {
      try {
        if (eraser.eraseGuest(userId)) {
          erased++;
        }
      } catch (RuntimeException e) {
        log.error("게스트 정리 실패 userId={}", userId, e);
      }
    }
    log.info("게스트 정리 완료: {}건 삭제", erased);
  }

  /** 대상 시각을 인자로 받는다. created_at 조건은 세션 INSERT 직전의 막 만든 게스트를 건너뛴다. */
  List<UUID> findExpiredGuests(OffsetDateTime now) {
    return jdbcTemplate.query(
        """
        SELECT u.id FROM auth.users u
        WHERE u.guest
          AND u.created_at < ?
          AND NOT EXISTS (
            SELECT 1 FROM auth.session s WHERE s.user_id = u.id AND s.expires_at > ?)
        """,
        (rs, rowNum) -> rs.getObject("id", UUID.class),
        now.minus(GUEST_SESSION_TTL),
        now);
  }
}
