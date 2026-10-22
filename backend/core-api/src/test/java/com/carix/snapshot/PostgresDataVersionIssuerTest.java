package com.carix.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link PostgresDataVersionIssuer} 의 순수 산술만 검증한다.
 *
 * <p>advisory lock 과 native query 는 H2 에 없어 통합 테스트가 불가능하다({@link PostgresDataVersionIssuer} 클래스
 * Javadoc). 하지만 시퀀스를 파싱·증가·형식화하는 부분은 DB 없이도 테스트할 수 있는 순수 함수라, 여기서 문자열만으로 직접 검증한다.
 */
class PostgresDataVersionIssuerTest {

  private static final UUID USER_ID = UUID.randomUUID();
  private static final String SECOND = "20260701093000";

  @Test
  @DisplayName("기존 최댓값의 시퀀스에서 1 증가한다")
  void nextSequence_incrementsFromExistingMax() {
    int next = PostgresDataVersionIssuer.nextSequence(SECOND + "-005", USER_ID, SECOND);

    assertThat(next).isEqualTo(6);
  }

  @Test
  @DisplayName("999 를 넘어가면(1000) 예외를 던진다")
  void nextSequence_throwsWhenExceeding999() {
    assertThatThrownBy(
            () -> PostgresDataVersionIssuer.nextSequence(SECOND + "-999", USER_ID, SECOND))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(SECOND);
  }

  @Test
  @DisplayName("형식은 YYYYMMDDHHMMSS-NNN 이다 — 시퀀스는 3자리로 0-패딩한다")
  void format_padsSequenceToThreeDigits() {
    assertThat(PostgresDataVersionIssuer.format(SECOND, 0)).isEqualTo(SECOND + "-000");
    assertThat(PostgresDataVersionIssuer.format(SECOND, 42)).isEqualTo(SECOND + "-042");
    assertThat(PostgresDataVersionIssuer.format(SECOND, 999)).isEqualTo(SECOND + "-999");
  }

  @Test
  @DisplayName("기존 최댓값이 없으면(그 초의 첫 발급) 시퀀스는 0 이다")
  void nextSequence_isZeroWhenNoExistingMax() {
    assertThat(PostgresDataVersionIssuer.nextSequence(null, USER_ID, SECOND)).isZero();
  }
}
