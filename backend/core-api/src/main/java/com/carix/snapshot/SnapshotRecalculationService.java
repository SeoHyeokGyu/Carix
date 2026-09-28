package com.carix.snapshot;

import com.carix.index.CurrentSalaryProvider;
import com.carix.index.MarketValueIndexProvider;
import com.carix.index.OpeningWindowIndexProvider;
import com.carix.index.PurchasingPowerIndexProvider;
import com.carix.index.dto.CurrentSalary;
import com.carix.index.dto.ExcludedSkill;
import com.carix.index.dto.IndexValueContract;
import com.carix.index.dto.MviResult;
import com.carix.index.dto.OwiResult;
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
  private final OpeningWindowIndexProvider owiProvider;
  private final CurrentSalaryProvider salaryProvider;
  private final DecisionSignalEngine decisionSignalEngine;
  private final DataVersionIssuer dataVersionIssuer;
  private final IndexSnapshotRepository indexSnapshotRepository;
  private final Supplier<String> currentYearMonth;

  @Autowired
  public SnapshotRecalculationService(
      PurchasingPowerIndexProvider ppiProvider,
      MarketValueIndexProvider mviProvider,
      OpeningWindowIndexProvider owiProvider,
      CurrentSalaryProvider salaryProvider,
      DecisionSignalEngine decisionSignalEngine,
      DataVersionIssuer dataVersionIssuer,
      IndexSnapshotRepository indexSnapshotRepository) {
    this(
        ppiProvider,
        mviProvider,
        owiProvider,
        salaryProvider,
        decisionSignalEngine,
        dataVersionIssuer,
        indexSnapshotRepository,
        () -> YM.format(YearMonth.now(ZoneOffset.UTC)));
  }

  SnapshotRecalculationService(
      PurchasingPowerIndexProvider ppiProvider,
      MarketValueIndexProvider mviProvider,
      OpeningWindowIndexProvider owiProvider,
      CurrentSalaryProvider salaryProvider,
      DecisionSignalEngine decisionSignalEngine,
      DataVersionIssuer dataVersionIssuer,
      IndexSnapshotRepository indexSnapshotRepository,
      Supplier<String> currentYearMonth) {
    this.ppiProvider = ppiProvider;
    this.mviProvider = mviProvider;
    this.owiProvider = owiProvider;
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
      OwiResult owi = owiProvider.calculate(userId, ym);
      CurrentSalary salary = salaryProvider.currentGrossSalary(userId, ym).orElse(null);
      DecisionOutcome outcome = decisionSignalEngine.evaluate(ppi, mvi, salary);

      SnapshotRationale rationale =
          new SnapshotRationale(
              outcome.rulesApplied(),
              bucketName(outcome),
              mvi.sources(),
              mvi.skillContributions(),
              mvi.excludedSkills().stream().map(ExcludedSkill::standardName).toList(),
              ppi.cpiAsOf(),
              mvi.aggregateAsOf(),
              mvi.wageBaseline() == null ? null : mvi.wageBaseline().statVintage(),
              dataVersion,
              new SnapshotRationale.IndexStatuses(ppi.status(), mvi.status(), owi.status()),
              openingWindowEvidence(owi),
              mvi.wageBaseline() == null || mvi.wageBaseline().source() == null
                  ? null
                  : mvi.wageBaseline().source().name());

      save(userId, ym, dataVersion, ruleVersion, ppi, mvi, owi, outcome, rationale);
      written++;
    }
    return written;
  }

  /**
   * 격차 비율을 구간 이름으로 접는다 (기술 명세 §1.3). 비율 자체는 {@link DecisionOutcome} 안에만 두고 저장하지 않는다 — 임금통계 중앙값과
   * 조인하면 연봉이 원 단위로 복원되기 때문이다.
   */
  private static String bucketName(DecisionOutcome outcome) {
    SalaryGapBucket bucket = SalaryGapBucket.of(outcome.salaryGapRatio());
    return bucket == null ? null : bucket.name();
  }

  /** 근거를 하나도 못 받았으면 블록 자체를 만들지 않는다. 필드가 전부 {@code null} 인 블록은 "근거가 없다"와 "근거를 모른다"를 같은 모양으로 만든다. */
  private static SnapshotRationale.OpeningWindowEvidence openingWindowEvidence(OwiResult owi) {
    if (owi.peerSampleSize() == null
        && owi.deadlineSampleSize() == null
        && owi.medianWindowDays() == null
        && owi.openEndedRatePercent() == null
        && owi.fallbackLevel() == null
        && owi.appliedConditions() == null
        && owi.marketMedianWindowDays() == null) {
      return null;
    }
    return new SnapshotRationale.OpeningWindowEvidence(
        owi.peerSampleSize(),
        owi.deadlineSampleSize(),
        owi.medianWindowDays(),
        owi.openEndedRatePercent(),
        owi.fallbackLevel(),
        owi.appliedConditions(),
        owi.marketMedianWindowDays());
  }

  private void save(
      UUID userId,
      String ym,
      String dataVersion,
      String ruleVersion,
      PpiResult ppi,
      MviResult mvi,
      OwiResult owi,
      DecisionOutcome outcome,
      SnapshotRationale rationale) {
    Optional<IndexSnapshot> existing =
        indexSnapshotRepository.findByUserIdAndBaseYearMonthAndDataVersionAndRuleVersion(
            userId, ym, dataVersion, ruleVersion);

    if (existing.isPresent()) {
      IndexSnapshot snapshot = existing.get();
      snapshot.replaceWith(ppi.value(), mvi.value(), owi.value(), outcome.code(), rationale);
      verifyPersistable(snapshot);
      return;
    }
    IndexSnapshot snapshot =
        new IndexSnapshot(
            userId,
            ym,
            dataVersion,
            ruleVersion,
            ppi.value(),
            mvi.value(),
            owi.value(),
            outcome.code(),
            rationale);
    verifyPersistable(snapshot);
    indexSnapshotRepository.save(snapshot);
  }

  /**
   * 저장 직전 행이 값-상태 대응을 지키는지 다시 본다 ({@link IndexValueContract}).
   *
   * <p>산출기 계약이 이미 같은 불변식을 걸지만 그 층은 각 결과를 따로 볼 뿐이다. 세 값을 서로 바꿔 넣는 배선 사고는 결과 셋이 전부 합법이어도 행을 뒤집으므로 여기서
   * 엔티티를 읽어 {@code rationale} 의 상태와 맞춰 본다. 운영에 {@code opening_window_index} 가 {@code NULL} 인데 상태는
   * {@code OK} 인 행 35개가 남은 경위를 규명하지 못했다 — 원인을 지목할 수 없으므로 층을 둘 둔다.
   */
  static void verifyPersistable(IndexSnapshot snapshot) {
    SnapshotRationale.IndexStatuses statuses = snapshot.getRationale().indexStatuses();
    String where =
        "userId=%s, baseYearMonth=%s, dataVersion=%s"
            .formatted(
                snapshot.getUserId(), snapshot.getBaseYearMonth(), snapshot.getDataVersion());

    IndexValueContract.PPI.requireState(where, snapshot.getPurchasingPowerIndex(), statuses.ppi());
    IndexValueContract.MVI.requireState(where, snapshot.getMarketValueIndex(), statuses.mvi());
    IndexValueContract.OWI.requireState(where, snapshot.getOpeningWindowIndex(), statuses.owi());
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
