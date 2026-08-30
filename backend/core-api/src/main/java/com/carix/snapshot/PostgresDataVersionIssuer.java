package com.carix.snapshot;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 단위로 직렬화해 버전을 발급한다.
 *
 * <p>명세는 "행 잠금"이라고 쓰지만 잠글 행을 전제할 수 없다 — 온보딩 중인 사용자는 {@code career_profile} 행이 아직 없다. 대신 행이 필요 없고
 * 트랜잭션 종료 시 자동 해제되는 {@code pg_advisory_xact_lock} 을 쓴다. 해시 충돌은 무관한 두 사용자를 직렬화할 뿐 정합성을 해치지 않는다.
 *
 * <p><b>Postgres 전용이다.</b> 테스트 DB(H2)에는 이 함수가 없어 {@link DataVersionIssuerConfiguration} 이 프로파일로
 * 격리한다. 따라서 이 클래스는 자동 검증 대상이 아니다.
 */
public class PostgresDataVersionIssuer implements DataVersionIssuer {

  private static final DateTimeFormatter SECOND_FORMAT =
      DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

  private final EntityManager entityManager;
  private final Clock clock;

  public PostgresDataVersionIssuer(EntityManager entityManager, Clock clock) {
    this.entityManager = entityManager;
    this.clock = clock;
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public String issue(UUID userId) {
    entityManager
        .createNativeQuery(
            "SELECT pg_advisory_xact_lock(hashtextextended(cast(:userId as text), 0))")
        .setParameter("userId", userId.toString())
        .getSingleResult();

    String second = SECOND_FORMAT.format(clock.instant().atZone(java.time.ZoneOffset.UTC));
    Object max =
        entityManager
            .createNativeQuery(
                """
                SELECT max(data_version) FROM core.index_snapshot
                WHERE user_id = cast(:userId as uuid) AND data_version LIKE :prefix
                """)
            .setParameter("userId", userId.toString())
            .setParameter("prefix", second + "-%")
            .getSingleResult();

    int nextSequence = max == null ? 0 : Integer.parseInt(max.toString().substring(15)) + 1;
    return "%s-%03d".formatted(second, nextSequence);
  }
}
