package com.carix.snapshot;

import com.carix.index.CurrentSalaryProvider;
import com.carix.index.MarketValueIndexProvider;
import com.carix.index.PurchasingPowerIndexProvider;
import com.carix.index.dto.CurrentSalary;
import com.carix.index.dto.ExcludedSkill;
import com.carix.index.dto.MviResult;
import com.carix.index.dto.PpiResult;
import com.carix.index.enums.IndexStatus;
import com.carix.snapshot.domain.IndexSnapshot;
import com.carix.snapshot.domain.SnapshotRationale;
import com.carix.snapshot.repository.IndexSnapshotRepository;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 월별 스냅샷 멱등성 저장·재계산 (Job 4, 기능 명세 §4.4).
 *
 * <p>A·B 가 소득·소비·스킬을 바꿨을 때 호출하는 유일한 진입점이다. 한 번의 호출은 처리하는 모든 월에 같은 {@code data_version} 을 쓴다 — 버전 발급
 * 단위가 "스냅샷 계산 실행"이기 때문이다 (기술 명세 §1.3).
 *
 * <p>같은 키의 재실행은 {@code advisory lock} 안에서 조회 후 대체한다. Postgres 의 {@code ON CONFLICT} 를 쓰지 않는 이유는 H2
 * 가 지원하지 않아 테스트할 수 없기 때문이다. DB 의 {@code UNIQUE} 제약이 최종 방어선으로 남는다.
 */
@Service
public class SnapshotRecalculationService {

  private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");

  private final PurchasingPowerIndexProvider ppiProvider;
  private final MarketValueIndexProvider mviProvider;
  private final CurrentSalaryProvider salaryProvider;
  private final DecisionSignalEngine decisionSignalEngine;
  private final DataVersionIssuer dataVersionIssuer;
  private final IndexSnapshotRepository indexSnapshotRepository;
  private final Supplier<String> currentYearMonth;

  @Autowired
  public SnapshotRecalculationService(
      PurchasingPowerIndexProvider ppiProvider,
      MarketValueIndexProvider mviProvider,
      CurrentSalaryProvider salaryProvider,
      DecisionSignalEngine decisionSignalEngine,
      DataVersionIssuer dataVersionIssuer,
      IndexSnapshotRepository indexSnapshotRepository) {
    this(
        ppiProvider,
        mviProvider,
        salaryProvider,
        decisionSignalEngine,
        dataVersionIssuer,
        indexSnapshotRepository,
        () -> YM.format(YearMonth.now(ZoneOffset.UTC)));
  }

  SnapshotRecalculationService(
      PurchasingPowerIndexProvider ppiProvider,
      MarketValueIndexProvider mviProvider,
      CurrentSalaryProvider salaryProvider,
      DecisionSignalEngine decisionSignalEngine,
      DataVersionIssuer dataVersionIssuer,
      IndexSnapshotRepository indexSnapshotRepository,
      Supplier<String> currentYearMonth) {
    this.ppiProvider = ppiProvider;
    this.mviProvider = mviProvider;
    this.salaryProvider = salaryProvider;
    this.decisionSignalEngine = decisionSignalEngine;
    this.dataVersionIssuer = dataVersionIssuer;
    this.indexSnapshotRepository = indexSnapshotRepository;
    this.currentYearMonth = currentYearMonth;
  }

  /**
   * {@code fromYearMonth} 부터 현재월까지 스냅샷을 새 데이터 버전으로 다시 계산해 저장한다.
   *
   * @return 실제로 기록한 월 수. CPI 최신월 발행 지연으로 건너뛴 월만 빠진다 — 과거월의 산출 불가는 행을 만들고 데이터 부족으로 기록한다
   */
  @Transactional
  public int recalculateSnapshot(UUID userId, String fromYearMonth) {
    String dataVersion = dataVersionIssuer.issue(userId);
    String ruleVersion = DecisionSignalEngine.RULE_VERSION;
    String latestYearMonth = currentYearMonth.get();
    int written = 0;

    for (String ym : monthsFrom(fromYearMonth, latestYearMonth)) {
      PpiResult ppi = ppiProvider.calculate(userId, ym);
      if (ppi.status() == IndexStatus.UPDATE_DELAYED && ym.equals(latestYearMonth)) {
        // CPI 최신월 결측은 보간하지 않고 그 월을 만들지 않는다 (기능 명세 §4.3, 기술 명세 §1.2).
        // 화면은 마지막 계산월과 `업데이트 지연` 을 표시하고 선을 잇지 않는다.
        // 과거월의 UPDATE_DELAYED 는 스킵하지 않는다 — 행을 만들고 지수를 NULL, decision_signal 을
        // INSUFFICIENT_DATA 로 기록한다. 모든 산출 불가를 행 스킵으로 처리하면 §2.2 우선순위 1이 저장될
        // 자리를 잃는다.
        continue;
      }

      MviResult mvi = mviProvider.calculate(userId, ym);
      CurrentSalary salary = salaryProvider.currentGrossSalary(userId, ym).orElse(null);
      DecisionOutcome outcome = decisionSignalEngine.evaluate(ppi, mvi, salary);

      SnapshotRationale rationale =
          new SnapshotRationale(
              outcome.rulesApplied(),
              outcome.salaryGapRatio(),
              mvi.excludedSkills().stream().map(ExcludedSkill::standardName).toList(),
              ppi.cpiAsOf(),
              mvi.aggregateAsOf(),
              mvi.wageBaseline() == null ? null : mvi.wageBaseline().statVintage(),
              dataVersion);

      save(userId, ym, dataVersion, ruleVersion, ppi, mvi, outcome, rationale);
      written++;
    }
    return written;
  }

  private void save(
      UUID userId,
      String ym,
      String dataVersion,
      String ruleVersion,
      PpiResult ppi,
      MviResult mvi,
      DecisionOutcome outcome,
      SnapshotRationale rationale) {
    Optional<IndexSnapshot> existing =
        indexSnapshotRepository.findByUserIdAndBaseYearMonthAndDataVersionAndRuleVersion(
            userId, ym, dataVersion, ruleVersion);

    if (existing.isPresent()) {
      existing.get().replaceWith(ppi.value(), mvi.value(), outcome.code(), rationale);
      return;
    }
    indexSnapshotRepository.save(
        new IndexSnapshot(
            userId,
            ym,
            dataVersion,
            ruleVersion,
            ppi.value(),
            mvi.value(),
            outcome.code(),
            rationale));
  }

  private List<String> monthsFrom(String fromYearMonth, String toYearMonth) {
    YearMonth from = YearMonth.parse(fromYearMonth, YM);
    YearMonth to = YearMonth.parse(toYearMonth, YM);
    List<String> months = new ArrayList<>();
    for (YearMonth ym = from; !ym.isAfter(to); ym = ym.plusMonths(1)) {
      months.add(YM.format(ym));
    }
    return months;
  }
}
