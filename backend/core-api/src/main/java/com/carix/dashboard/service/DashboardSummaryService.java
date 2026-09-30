package com.carix.dashboard.service;

import com.carix.dashboard.dto.DashboardSummaryResponse;
import com.carix.dashboard.dto.DashboardSummaryResponse.DataLimitSource;
import com.carix.dashboard.dto.DashboardSummaryResponse.DecisionSignal;
import com.carix.dashboard.dto.DashboardSummaryResponse.ExcludedSkill;
import com.carix.dashboard.dto.DashboardSummaryResponse.IndexDetail;
import com.carix.dashboard.dto.DashboardSummaryResponse.PeerGroup;
import com.carix.dashboard.dto.DashboardSummaryResponse.PriceDriver;
import com.carix.dashboard.dto.DashboardSummaryResponse.SummaryMetadata;
import com.carix.dashboard.dto.DashboardSummaryResponse.WageStatInfo;
import com.carix.index.dto.IndexValueContract;
import com.carix.index.dto.SourceCoverage;
import com.carix.index.dto.TopPriceDriver;
import com.carix.index.enums.IndexStatus;
import com.carix.snapshot.DecisionSignalCode;
import com.carix.snapshot.SalaryGapBucket;
import com.carix.snapshot.domain.IndexSnapshot;
import com.carix.snapshot.domain.SnapshotRationale;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대시보드 지표 요약 (API 규격 §5.1, 화면 `S-10`).
 *
 * <p>스냅샷이 없으면 예외가 아니라 {@link IndexStatus#NOT_AVAILABLE} 이다. 값을 0 이나 100 으로 채우지 않는다 (API 규격 §5.1,
 * [MVP 규격 §13] 데이터가 없는 기능을 추정값으로 채워 출시하지 않는다).
 */
@Service
@RequiredArgsConstructor
public class DashboardSummaryService {

  private static final String DIRECTION_INCREASED = "INCREASED";
  private static final String DIRECTION_DECREASED = "DECREASED";
  private static final String DIRECTION_FLAT = "FLAT";

  private final SnapshotQueryService snapshotQueryService;

  @Transactional(readOnly = true)
  public DashboardSummaryResponse resolve(UUID userId, String baseYearMonth) {
    List<IndexSnapshot> months = snapshotQueryService.latestPerMonthDesc(userId);
    Optional<IndexSnapshot> current = snapshotQueryService.forMonth(months, baseYearMonth);

    if (current.isEmpty()) {
      return empty();
    }

    IndexSnapshot snapshot = current.get();
    Optional<IndexSnapshot> previous = snapshotQueryService.previousOf(months, baseYearMonth);
    SnapshotRationale.IndexStatuses statuses = statusesOf(snapshot);

    return new DashboardSummaryResponse(
        detail(
            snapshot.getPurchasingPowerIndex(),
            previous.map(IndexSnapshot::getPurchasingPowerIndex).orElse(null),
            statuses.ppi()),
        detail(
            snapshot.getMarketValueIndex(),
            previous.map(IndexSnapshot::getMarketValueIndex).orElse(null),
            statuses.mvi()),
        detail(
            snapshot.getOpeningWindowIndex(),
            previous.map(IndexSnapshot::getOpeningWindowIndex).orElse(null),
            statuses.owi()),
        decisionSignal(snapshot),
        metadata(snapshot));
  }

  // --- 지수 ---

  /** {@code rationale} 자체가 없는 행이 있다. 컴팩트 생성자가 보장하는 것은 rationale 이 있을 때뿐이다. */
  private static SnapshotRationale.IndexStatuses statusesOf(IndexSnapshot snapshot) {
    SnapshotRationale rationale = snapshot.getRationale();
    return rationale == null
        ? SnapshotRationale.IndexStatuses.unknown()
        : rationale.indexStatuses();
  }

  /**
   * 지수 한 건.
   *
   * <p><b>값이 있으면 상태는 항상 {@code OK} 다.</b> 저장된 상태를 그대로 실으면 {@code UPDATE_DELAYED} + 값 있음이 도달 가능해지는데,
   * 프런트 {@code lib/api/dashboard.ts} 가 "{@code OK} 가 아니면 값은 항상 {@code null}" 을 계약으로 문서화했다.
   */
  private IndexDetail detail(BigDecimal value, BigDecimal previousValue, IndexStatus storedStatus) {
    if (value == null) {
      // 값을 요구하는 상태를 값 없이 실으면 프런트 계약이 뒤집힌다. 운영에 OWI 가 `OK` 인데 값이 NULL 인
      // 옛 행이 남아 있어(경위 미규명) 읽을 때 접는다. 새로 쓰는 행은 IndexValueContract 가 막는다.
      IndexStatus status =
          storedStatus == null || IndexValueContract.impliesValue(storedStatus)
              ? IndexStatus.NOT_AVAILABLE
              : storedStatus;
      return new IndexDetail(null, null, null, status.name(), null);
    }
    Double change =
        previousValue == null
            ? null
            : value.subtract(previousValue).setScale(2, RoundingMode.HALF_UP).doubleValue();

    return new IndexDetail(
        value.doubleValue(), change, direction(change), IndexStatus.OK.name(), null);
  }

  /** 전월이 없으면 방향도 없다. 0 을 넣으면 `변화 없음`으로 잘못 읽힌다. */
  private String direction(Double change) {
    if (change == null) {
      return null;
    }
    if (change > 0) {
      return DIRECTION_INCREASED;
    }
    return change < 0 ? DIRECTION_DECREASED : DIRECTION_FLAT;
  }

  // --- 결정 신호 ---

  private DecisionSignal decisionSignal(IndexSnapshot snapshot) {
    DecisionSignalCode code = snapshot.getDecisionSignal();
    SnapshotRationale rationale = snapshot.getRationale();

    return new DecisionSignal(
        code.name(),
        code.label(),
        rationale == null ? List.of() : rationale.rulesApplied(),
        gapSentence(rationale));
  }

  /** 근거 문장. 격차는 구간으로만 저장되므로 문구도 구간이다 (기술 명세 §1.3). */
  private String gapSentence(SnapshotRationale rationale) {
    if (rationale == null || rationale.salaryGapBucket() == null) {
      return null;
    }
    try {
      return SalaryGapBucket.valueOf(rationale.salaryGapBucket()).label() + ".";
    } catch (IllegalArgumentException e) {
      // NOTE: (cjkang) 지금 읽히지 않는 가지다 — rationale 은 jsonb 라 운영 행이 없는 현재는
      // 저장된 값이 곧 현재 enum 이다. 남겨 두는 이유는 나중이다. jsonb 에는 enum 제약이 없어
      // 구간을 이름만 바꿔도 옛 행의 문자열이 매핑을 잃는다. 그때 문구를 지어내는 대신 비운다.
      return null;
    }
  }

  // --- 메타데이터 ---

  /**
   * 근거·기준일 묶음. 원천별 커버리지는 {@code rationale.sourceCoverages} 에서 온다 (기술 명세 §1.3 결정분) — 그 달의 완화 결과는 당시
   * 프로필에 의존해 나중에 재계산하면 같은 값이 안 나오므로 저장분을 그대로 읽는다.
   */
  private SummaryMetadata metadata(IndexSnapshot snapshot) {
    SnapshotRationale rationale = snapshot.getRationale();
    if (rationale == null) {
      return new SummaryMetadata(
          null,
          snapshot.getDataVersion(),
          snapshot.getRuleVersion(),
          false,
          List.of(),
          null,
          null,
          null,
          null,
          null,
          null,
          null);
    }

    List<SourceCoverage> successful =
        rationale.sourceCoverages().stream().filter(SourceCoverage::successful).toList();
    List<DataLimitSource> sources =
        successful.stream()
            .map(coverage -> new DataLimitSource(coverage.source(), coverage.sampleCount()))
            .toList();
    Integer sampleSize =
        successful.isEmpty()
            ? null
            : successful.stream().mapToInt(SourceCoverage::sampleCount).sum();

    return new SummaryMetadata(
        rationale.aggregateAsOf(),
        snapshot.getDataVersion(),
        snapshot.getRuleVersion(),
        false,
        sources,
        sampleSize,
        weightedMissingRate(successful),
        peerGroup(successful, rationale),
        rationale.wageStatVintage() == null
            ? new WageStatInfo(null, null, "AGE_BAND_PROXY", "UNKNOWN")
            : new WageStatInfo(
                "ANNUAL_GROSS_MEDIAN",
                rationale.wageStatVintage(),
                "AGE_BAND_PROXY",
                rationale.wageStatSource() != null ? rationale.wageStatSource() : "UNKNOWN"),
        null,
        openingWindow(rationale),
        priceDriver(rationale));
  }

  /** 저장된 1위를 화면 계약으로 옮긴다. 기여도 값은 빠진다. */
  private static PriceDriver priceDriver(SnapshotRationale rationale) {
    TopPriceDriver driver = rationale.topPriceDriver();
    if (driver == null) {
      return null;
    }
    return new PriceDriver(
        driver.category(), driver.label(), driver.estimated(), driver.cpiMonth());
  }

  /**
   * 원천마다 결측률이 다르므로 표본 수로 가중 평균한다. 단순 평균은 표본이 적은 원천을 과대 반영한다.
   *
   * <p>결측률을 남기지 않은 원천은 분자와 분모 <b>양쪽</b>에서 뺀다. 분모에만 남기면 그 원천을 결측률 0% 로 계산한 것과 같아져, 모르는 값이 전체 결측률을
   * 실제보다 낮게 보이게 만든다.
   */
  private Double weightedMissingRate(List<SourceCoverage> successful) {
    List<SourceCoverage> measured =
        successful.stream().filter(c -> c.salaryMissingRatePercent() != null).toList();
    int totalSamples = measured.stream().mapToInt(SourceCoverage::sampleCount).sum();
    if (totalSamples == 0) {
      return null;
    }
    BigDecimal weighted = BigDecimal.ZERO;
    for (SourceCoverage coverage : measured) {
      weighted =
          weighted.add(
              coverage
                  .salaryMissingRatePercent()
                  .multiply(BigDecimal.valueOf(coverage.sampleCount())));
    }
    return weighted.divide(BigDecimal.valueOf(totalSamples), 2, RoundingMode.HALF_UP).doubleValue();
  }

  /** 화면은 조건 문구를 하나만 보여준다. 표본이 가장 큰 원천의 안착 조건을 대표로 쓴다. */
  private PeerGroup peerGroup(List<SourceCoverage> successful, SnapshotRationale rationale) {
    Optional<SourceCoverage> representative =
        successful.stream().max(Comparator.comparingInt(SourceCoverage::sampleCount));
    if (representative.isEmpty()) {
      return null;
    }
    List<ExcludedSkill> excluded =
        rationale.excludedSkills().stream().map(name -> new ExcludedSkill(name, null)).toList();

    return new PeerGroup(
        representative.get().fallbackLevel(), representative.get().appliedConditions(), excluded);
  }

  /**
   * 저장된 근거 일곱 중 화면이 쓰는 셋만 낸다 (API 규격 §5.1). 나머지 넷(동료집단 표본 수, 완화 단계, 조건 문구, 기준 모수 개방일수)은 저장만 하고 소비자가
   * 생길 때 연다.
   *
   * <p>OWI 완화는 MVI 완화와 별개라 {@code metadata.peerGroup} 에 섞지 않는다 — 섞으면 어느 지수의 조건인지 화면이 구분하지 못한다.
   */
  private DashboardSummaryResponse.OpeningWindowSummary openingWindow(SnapshotRationale rationale) {
    SnapshotRationale.OpeningWindowEvidence evidence = rationale.openingWindow();
    if (evidence == null) {
      return null;
    }
    return new DashboardSummaryResponse.OpeningWindowSummary(
        evidence.medianWindowDays(),
        evidence.openEndedRatePercent() == null
            ? null
            : evidence.openEndedRatePercent().doubleValue(),
        evidence.deadlineSampleSize());
  }

  private DashboardSummaryResponse empty() {
    IndexDetail unavailable =
        new IndexDetail(null, null, null, IndexStatus.NOT_AVAILABLE.name(), null);

    return new DashboardSummaryResponse(
        unavailable,
        unavailable,
        unavailable,
        new DecisionSignal(
            DecisionSignalCode.INSUFFICIENT_DATA.name(),
            DecisionSignalCode.INSUFFICIENT_DATA.label(),
            List.of(),
            null),
        new SummaryMetadata(
            null, null, null, false, List.of(), null, null, null, null, null, null, null));
  }
}
