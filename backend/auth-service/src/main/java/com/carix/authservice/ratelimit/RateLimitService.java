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
    OffsetDateTime windowStart = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.HOURS);

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
}
