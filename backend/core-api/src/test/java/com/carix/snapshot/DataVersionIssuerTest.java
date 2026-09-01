package com.carix.snapshot;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DataVersionIssuerTest {

  @Test
  @DisplayName("형식은 YYYYMMDDHHMMSS-NNN 이다")
  void issue_matchesFormat() {
    DataVersionIssuer issuer = new FixedClockDataVersionIssuer("20260701093000");

    assertThat(issuer.issue(UUID.randomUUID())).matches("\\d{14}-\\d{3}");
  }

  @Test
  @DisplayName("같은 초에 재발급하면 시퀀스가 증가해 사전순으로 단조 증가한다")
  void issue_incrementsSequenceWithinSameSecond() {
    DataVersionIssuer issuer = new FixedClockDataVersionIssuer("20260701093000");
    UUID userId = UUID.randomUUID();

    String first = issuer.issue(userId);
    String second = issuer.issue(userId);

    assertThat(first).isEqualTo("20260701093000-000");
    assertThat(second).isEqualTo("20260701093000-001");
    assertThat(second).isGreaterThan(first);
  }

  /** 테스트용 고정 시각 발급기. 운영 구현({@link PostgresDataVersionIssuer})은 H2 에 없는 advisory lock 을 쓴다. */
  private static final class FixedClockDataVersionIssuer implements DataVersionIssuer {
    private final String fixedSecond;
    private int sequence = 0;

    private FixedClockDataVersionIssuer(String fixedSecond) {
      this.fixedSecond = fixedSecond;
    }

    @Override
    public String issue(UUID userId) {
      return "%s-%03d".formatted(fixedSecond, sequence++);
    }
  }
}
