package com.carix.authservice.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

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
}
