package com.carix.snapshot;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.ZoneOffset;
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

  /** {@code data_version} 문자열에서 {@code NNN} 시퀀스가 시작하는 위치. {@code "yyyyMMddHHmmss-"} 는 15자다. */
  private static final int SEQUENCE_OFFSET = 15;

  /** {@code NNN} 이 3자리라 표현 가능한 최댓값. */
  private static final int MAX_SEQUENCE = 999;

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

    String second = SECOND_FORMAT.format(clock.instant().atZone(ZoneOffset.UTC));
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

    int nextSequence = nextSequence(max, userId, second);
    return format(second, nextSequence);
  }

  /**
   * 한 초 안에서 다음에 발급할 시퀀스. 기존 최댓값이 없으면(그 초의 첫 발급) 0 이다. {@code userId}·{@code second} 는 3자리를 초과했을 때
   * 예외 메시지에만 쓰인다 — 순수 함수다.
   *
   * @throws IllegalStateException 다음 시퀀스가 999 를 넘으면(3자리 초과)
   */
  static int nextSequence(Object maxDataVersion, UUID userId, String second) {
    int sequence =
        maxDataVersion == null
            ? 0
            : Integer.parseInt(maxDataVersion.toString().substring(SEQUENCE_OFFSET)) + 1;
    if (sequence > MAX_SEQUENCE) {
      throw new IllegalStateException(
          "data_version 시퀀스(NNN)가 3자리를 초과했다 — userId=%s, second=%s. 이 초 안에서 더 이상 발급할 수 없다."
              .formatted(userId, second));
    }
    return sequence;
  }

  /** {@code YYYYMMDDHHMMSS-NNN} 형식으로 조립한다. 순수 함수다. */
  static String format(String second, int sequence) {
    return "%s-%03d".formatted(second, sequence);
  }
}
