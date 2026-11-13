package com.carix.snapshot;

import com.carix.income.repository.IncomeHistoryRepository;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** 사용자 이력 전체를 다시 계산할 때의 시작월. 구매력 지수의 기준월이 최초 소득월이라 그보다 앞은 계산할 것이 없다. */
@Component
public class SnapshotRangeResolver {

  private static final Pattern YEAR_MONTH = Pattern.compile("\\d{4}-?(0[1-9]|1[0-2])");

  private final IncomeHistoryRepository incomeHistoryRepository;

  public SnapshotRangeResolver(IncomeHistoryRepository incomeHistoryRepository) {
    this.incomeHistoryRepository = incomeHistoryRepository;
  }

  /** 최초 소득 유효월({@code YYYYMM}). 소득이 없으면 현재월이다. */
  public String earliestFrom(UUID userId) {
    var incomes = incomeHistoryRepository.findByUserIdOrderByEffectiveDateDesc(userId);
    if (incomes.isEmpty()) {
      return YearMonths.current();
    }
    return YearMonths.of(incomes.get(incomes.size() - 1).getEffectiveDate());
  }

  /**
   * 요청받은 시작월({@code YYYYMM} 또는 {@code YYYY-MM})을 최초 소득월~현재월로 묶는다. 재계산은 시작월부터 현재월까지를 사용자 락과 쓰기 커넥션을
   * 쥔 한 트랜잭션에서 돌아, 묶지 않으면 {@code 000101} 하나로 2만 개월 넘게 계산한다.
   *
   * @throws IllegalArgumentException 형식이 틀렸거나 월이 01~12 가 아닐 때
   */
  public String clampFrom(UUID userId, String requested) {
    if (!YEAR_MONTH.matcher(requested).matches()) {
      throw new IllegalArgumentException("fromYearMonth 는 YYYYMM 또는 YYYY-MM 형식이어야 합니다.");
    }
    String from = requested.replace("-", "");
    String earliest = earliestFrom(userId);
    String raised = from.compareTo(earliest) < 0 ? earliest : from;
    return YearMonths.earlier(raised, YearMonths.current());
  }
}
