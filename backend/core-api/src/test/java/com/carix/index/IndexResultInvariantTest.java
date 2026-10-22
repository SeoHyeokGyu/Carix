package com.carix.index;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carix.index.dto.MviResult;
import com.carix.index.dto.OwiResult;
import com.carix.index.dto.PpiResult;
import com.carix.index.enums.ExpenseWeightStatus;
import com.carix.index.enums.IndexStatus;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 지수 산출 결과의 값-상태 대응 불변식.
 *
 * <p>운영 {@code core.index_snapshot} 에 {@code opening_window_index} 가 {@code NULL} 인데 {@code
 * rationale.indexStatuses.owi} 는 {@code OK} 인 행 35개가 쌓였다. 어느 빌드가 만들었는지 규명하지 못했고 현재 코드 경로로는 재현되지
 * 않는다. 원인을 지목할 수 없으므로 계약이 그 모양을 거부한다.
 */
class IndexResultInvariantTest {

  private static final String YM = "202608";
  private static final BigDecimal VALUE = new BigDecimal("100.00");

  // --- PPI ---

  @Test
  @DisplayName("PPI - INSUFFICIENT_HISTORY 만 OK 아닌데 값을 갖는다 (기능 명세 §4.3)")
  void ppi_비교기간_부족은_값을_갖는다() {
    assertThatCode(() -> ppi(VALUE, IndexStatus.INSUFFICIENT_HISTORY)).doesNotThrowAnyException();
    assertThatThrownBy(() -> ppi(null, IndexStatus.INSUFFICIENT_HISTORY))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("PPI - 표본 부족은 MVI·OWI 전용이라 PPI 가 쓰면 위반이다")
  void ppi_는_표본_부족을_쓰지_않는다() {
    assertThatThrownBy(() -> ppi(null, IndexStatus.INSUFFICIENT_SAMPLE))
        .isInstanceOf(IllegalStateException.class);
  }

  // --- MVI ---

  @Test
  @DisplayName("MVI - 표본 부족에 값이 붙으면 만들 수 없다")
  void mvi_표본_부족은_값이_없다() {
    assertThatThrownBy(() -> mvi(VALUE, IndexStatus.INSUFFICIENT_SAMPLE))
        .isInstanceOf(IllegalStateException.class);
  }

  // --- OWI ---

  @Test
  @DisplayName("OWI - OK 인데 값이 없으면 만들 수 없다 (운영 35행이 이 모양이었다)")
  void owi_OK_는_값을_요구한다() {
    assertThatThrownBy(() -> owi(null, IndexStatus.OK))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("OWI");
  }

  // --- 공통 ---

  @Test
  @DisplayName("값이 있으면 0 초과여야 한다 — 기준 100 지수에서 0 은 도메인이 금지한 임의 수치다")
  void 지수값은_0_이_될_수_없다() {
    assertThatThrownBy(() -> mvi(BigDecimal.ZERO, IndexStatus.OK))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("0");
    assertThatThrownBy(() -> ppi(BigDecimal.ZERO, IndexStatus.OK))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> owi(new BigDecimal("-1.00"), IndexStatus.OK))
        .isInstanceOf(IllegalStateException.class);
  }

  // --- 픽스처 ---

  private static PpiResult ppi(BigDecimal value, IndexStatus status) {
    return new PpiResult(YM, value, status, ExpenseWeightStatus.DEFAULT_CPI, null, null);
  }

  private static MviResult mvi(BigDecimal value, IndexStatus status) {
    return new MviResult(YM, value, status, List.of(), List.of(), List.of(), List.of(), null, null);
  }

  private static OwiResult owi(BigDecimal value, IndexStatus status) {
    return new OwiResult(YM, value, status, null, null, null, null, null, null, null);
  }
}
