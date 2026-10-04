package com.carix.index.dto;

import com.carix.index.enums.IndexStatus;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Set;

/**
 * 지수 산출 결과의 값-상태 대응 불변식. 세 지수가 각자 쓰는 상태 집합만 다르고 규칙은 같다.
 *
 * <ul>
 *   <li>값이 있다 ⟺ 상태가 {@link IndexStatus#OK} 또는 {@link IndexStatus#INSUFFICIENT_HISTORY} 다
 *   <li>값이 있으면 0 초과다 — 기준 100 지수에서 0 은 도메인 정의서가 금지한 임의 수치다
 *   <li>상태는 그 지수가 실제로 쓰는 것이어야 한다. {@code INSUFFICIENT_HISTORY} 는 PPI 전용, {@code
 *       INSUFFICIENT_SAMPLE} 는 MVI·OWI 전용, {@code UPDATE_DELAYED} 는 외부 발행 주기에 매인 PPI·MVI 전용이다
 * </ul>
 *
 * <p><b>두 층에서 쓴다.</b> 산출기 계약(각 {@code *Result} 컴팩트 생성자)이 버그가 난 자리에서 {@link #requireArgument}로 터지고,
 * 스냅샷 쓰기 경계가 저장 직전 엔티티를 {@link #requireState}로 다시 본다. 뒤쪽 층은 앞쪽이 못 잡는 배선 사고 — 값 셋을 서로 바꿔 넣는 것 — 를
 * 잡는다.
 *
 * <p>두 층을 다 두는 이유는 운영 이력이다. {@code core.index_snapshot} 에 {@code opening_window_index} 가 {@code
 * NULL} 인데 {@code rationale.indexStatuses.owi} 는 {@code OK} 이고 산출 근거는 다 들어있는 행 35개가 있다. 어느 빌드가
 * 만들었는지 규명하지 못했고 현재 코드 경로로는 재현되지 않는다. 원인을 지목할 수 없으니 계약이 모양을 거부한다.
 */
public enum IndexValueContract {
  /** 구매력 지수. 기준월부터 12개월 미만이면 상태가 {@code INSUFFICIENT_HISTORY} 이고 값은 그대로 있다 (기능 명세 §4.3). */
  PPI(
      EnumSet.of(
          IndexStatus.OK,
          IndexStatus.INSUFFICIENT_HISTORY,
          IndexStatus.UPDATE_DELAYED,
          IndexStatus.NOT_AVAILABLE)),

  /** 시장가치 지수. 동료집단 표본 미달·계산 가능 스킬 0개는 {@code INSUFFICIENT_SAMPLE} 다 (기능 명세 §4.2). */
  MVI(
      EnumSet.of(
          IndexStatus.OK,
          IndexStatus.INSUFFICIENT_SAMPLE,
          IndexStatus.UPDATE_DELAYED,
          IndexStatus.NOT_AVAILABLE)),

  /** 개방창 지수. {@code UPDATE_DELAYED} 를 쓰지 않는다 — 외부 발행 주기에 매이지 않는다 (기능 명세 §4.5). */
  OWI(EnumSet.of(IndexStatus.OK, IndexStatus.INSUFFICIENT_SAMPLE, IndexStatus.NOT_AVAILABLE));

  /** 값을 갖는 상태. 지수마다 같다 — {@code INSUFFICIENT_HISTORY} 를 쓰지 않는 지수는 {@link #allowed} 가 먼저 거른다. */
  private static final Set<IndexStatus> VALUE_BEARING =
      EnumSet.of(IndexStatus.OK, IndexStatus.INSUFFICIENT_HISTORY);

  private final Set<IndexStatus> allowed;

  IndexValueContract(Set<IndexStatus> allowed) {
    this.allowed = allowed;
  }

  /** 그 상태라면 값이 있어야 하는가. 읽기 경로가 옛 행을 접을 때 쓴다. */
  public static boolean impliesValue(IndexStatus status) {
    return status != null && VALUE_BEARING.contains(status);
  }

  /**
   * 산출기 계약 위반. 산출기가 만들 수 없는 조합을 냈다는 뜻이라 {@link IllegalStateException} 이다.
   *
   * <p>{@link IllegalArgumentException} 을 쓰지 않는다 — {@code GlobalExceptionHandler} 가 그것을 {@code 400
   * INVALID_INPUT} 으로 바꿔 메시지까지 응답에 싣는다. 산출기 버그가 소득·스킬 저장 요청에서 "입력값이 올바르지 않습니다" 로 나가면 원인을 사용자 입력으로
   * 오진하게 된다. 이 불변식이 막으려는 것이 바로 그 오진이다.
   */
  public void requireArgument(String baseYearMonth, BigDecimal value, IndexStatus status) {
    String violation = violation(value, status);
    if (violation != null) {
      throw new IllegalStateException("%s — baseYearMonth=%s".formatted(violation, baseYearMonth));
    }
  }

  /** 쓰기 경계 위반. 저장하려는 행이 존재할 수 없는 상태다. */
  public void requireState(String where, BigDecimal value, IndexStatus status) {
    String violation = violation(value, status);
    if (violation != null) {
      throw new IllegalStateException("%s — %s".formatted(violation, where));
    }
  }

  /** 위반 문구, 없으면 {@code null}. 순수 함수다. */
  private String violation(BigDecimal value, IndexStatus status) {
    if (status == null) {
      return "%s 상태가 없다. 값이 없는 이유를 화면이 말할 수 없다".formatted(name());
    }
    if (!allowed.contains(status)) {
      return "%s 는 상태 %s 를 쓰지 않는다".formatted(name(), status);
    }
    if (VALUE_BEARING.contains(status)) {
      return value == null ? "%s 상태 %s 는 값을 요구한다".formatted(name(), status) : positive(value);
    }
    return value == null
        ? null
        : "%s 상태 %s 는 값을 가질 수 없다. value=%s".formatted(name(), status, value);
  }

  private String positive(BigDecimal value) {
    return value.signum() > 0
        ? null
        : "%s 값은 0 초과여야 한다. 기준 100 지수에서 0 은 도메인이 금지한 임의 수치다. value=%s".formatted(name(), value);
  }
}
