package com.carix.support;

import com.carix.snapshot.DataVersionIssuer;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * H2 로 도는 통합 테스트용 데이터 버전 발급기.
 *
 * <p>운영 발급기 {@code PostgresDataVersionIssuer} 는 {@code pg_advisory_xact_lock} 을 쓰는데 H2 에 그 함수가 없다.
 * {@code DataVersionIssuerConfiguration} 이 {@code @ConditionalOnMissingBean} 이라 이 빈을 올리면 운영 발급기는 뜨지
 * 않는다.
 *
 * <p><b>이 대체가 곧 개발 계획 §5-K 다.</b> 자동 검증이 여기서 멈추므로 자문 잠금과 {@code jsonb} 바인딩은 운영 Postgres 에서 한 번 직접
 * 돌려봐야 한다. 스냅샷 트리거가 배선되면서(개발 계획 §4-2) 이 경로는 배치가 아니라 <b>평범한 사용자 쓰기</b>에 걸린다 — 잠금이 운영에서 깨지면
 * 소득·소비·스킬·프로필 저장이 통째로 실패한다.
 */
@TestConfiguration
public class H2DataVersionIssuerConfiguration {

  @Bean
  DataVersionIssuer h2DataVersionIssuer() {
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    Clock clock = Clock.systemUTC();
    AtomicInteger sequence = new AtomicInteger();

    return (UUID userId) ->
        "%s-%03d"
            .formatted(
                formatter.format(clock.instant().atZone(ZoneOffset.UTC)),
                sequence.incrementAndGet() % 1000);
  }
}
