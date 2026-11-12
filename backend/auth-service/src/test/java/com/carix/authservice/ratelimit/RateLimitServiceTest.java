package com.carix.authservice.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

class RateLimitServiceTest {

  private JdbcClient jdbcClient;
  private JdbcClient.StatementSpec statementSpec;
  private JdbcClient.MappedQuerySpec<Integer> querySpec;
  private RateLimitService rateLimitService;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    jdbcClient = mock(JdbcClient.class);
    statementSpec = mock(JdbcClient.StatementSpec.class);
    querySpec = mock(JdbcClient.MappedQuerySpec.class);

    given(jdbcClient.sql(anyString())).willReturn(statementSpec);
    given(statementSpec.param(anyString(), any())).willReturn(statementSpec);
    given(statementSpec.query(Integer.class)).willReturn(querySpec);

    rateLimitService = new RateLimitService(jdbcClient);
  }

  @Test
  void 요청_수가_제한_이하이면_초과하지_않음() {
    given(querySpec.single()).willReturn(5);

    boolean exceeded = rateLimitService.isExceeded("LOGIN", "user@example.com", 5);

    assertThat(exceeded).isFalse();
  }

  @Test
  void 요청_수가_제한_초과이면_true_반환() {
    given(querySpec.single()).willReturn(6);

    boolean exceeded = rateLimitService.isExceeded("LOGIN", "user@example.com", 5);

    assertThat(exceeded).isTrue();
  }

  // 현재 창을 지우면 그 시간의 카운터가 0 으로 돌아가 속도 제한이 풀린다. 지우는 것은 끝난 창뿐이다.
  @Test
  void 만료_창_정리는_현재_창_시작_이전만_지운다() {
    given(statementSpec.update()).willReturn(3);
    OffsetDateTime now = OffsetDateTime.of(2026, 11, 12, 12, 34, 56, 0, ZoneOffset.UTC);

    int deleted = rateLimitService.deleteExpiredWindows(now);

    assertThat(deleted).isEqualTo(3);
    verify(statementSpec)
        .param("currentWindowStart", OffsetDateTime.of(2026, 11, 12, 12, 0, 0, 0, ZoneOffset.UTC));
  }
}
