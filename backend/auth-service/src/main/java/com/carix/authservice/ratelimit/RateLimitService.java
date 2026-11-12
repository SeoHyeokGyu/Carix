package com.carix.authservice.ratelimit;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Postgres 고정 윈도 카운터. Redis 는 쓰지 않는다 — 근거는
 * docs/superpowers/specs/2026-08-01-auth-service-phase1-design.md §6.
 */
@Service
public class RateLimitService {

  private final JdbcClient jdbcClient;

  public RateLimitService(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  public boolean isExceeded(String scope, String key, int limit) {
    String bucketKey = scope + ":" + key;
    OffsetDateTime windowStart = windowStart(OffsetDateTime.now(ZoneOffset.UTC));

    Integer requestCount =
        jdbcClient
            .sql(
                """
				INSERT INTO auth.rate_limit_bucket (id, bucket_key, window_start, request_count)
				VALUES (:id, :bucketKey, :windowStart, 1)
				ON CONFLICT (bucket_key, window_start)
				DO UPDATE SET request_count = auth.rate_limit_bucket.request_count + 1
				RETURNING request_count
				""")
            .param("id", UUID.randomUUID())
            .param("bucketKey", bucketKey)
            .param("windowStart", windowStart)
            .query(Integer.class)
            .single();

    return requestCount > limit;
  }

  /**
   * 끝난 창의 행을 지운다. 키에 이메일·IP 가 평문으로 들어 있어 창이 끝나면 남길 이유가 없다 — 탈퇴한 사용자의 이메일도 여기서 사라진다. 현재 창은 남긴다. 지우면
   * 그 시간의 카운터가 처음부터 다시 세져 제한이 풀린다.
   */
  public int deleteExpiredWindows(OffsetDateTime now) {
    return jdbcClient
        .sql("DELETE FROM auth.rate_limit_bucket WHERE window_start < :currentWindowStart")
        .param("currentWindowStart", windowStart(now))
        .update();
  }

  private static OffsetDateTime windowStart(OffsetDateTime now) {
    return now.withOffsetSameInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.HOURS);
  }
}
