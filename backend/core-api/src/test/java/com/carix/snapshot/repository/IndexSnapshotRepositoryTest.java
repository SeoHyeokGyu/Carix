package com.carix.snapshot.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.carix.snapshot.DecisionSignalCode;
import com.carix.snapshot.domain.IndexSnapshot;
import com.carix.snapshot.domain.SnapshotRationale;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class IndexSnapshotRepositoryTest {

  @Autowired private IndexSnapshotRepository indexSnapshotRepository;

  private static SnapshotRationale rationale() {
    return new SnapshotRationale(
        List.of("연봉 격차 15% 이상", "보유 스킬 시장수요 상승세"),
        new BigDecimal("0.200000"),
        List.of("CustomSkillX"),
        LocalDate.of(2026, 7, 1),
        LocalDate.of(2026, 7, 1),
        "202606",
        "20260701093000-000");
  }

  @Test
  @DisplayName("rationale 이 JSON 으로 저장되고 그대로 되읽힌다")
  void rationale_roundTripsThroughJsonColumn() {
    UUID userId = UUID.randomUUID();
    indexSnapshotRepository.saveAndFlush(
        new IndexSnapshot(
            userId,
            "202607",
            "20260701093000-000",
            "1.0.0",
            new BigDecimal("94.20"),
            new BigDecimal("117.50"),
            DecisionSignalCode.SEEK_JOB,
            rationale()));

    IndexSnapshot found =
        indexSnapshotRepository
            .findByUserIdAndBaseYearMonthAndDataVersionAndRuleVersion(
                userId, "202607", "20260701093000-000", "1.0.0")
            .orElseThrow();

    assertThat(found.getRationale().rulesApplied())
        .containsExactly("연봉 격차 15% 이상", "보유 스킬 시장수요 상승세");
    assertThat(found.getRationale().salaryGapRatio()).isEqualByComparingTo("0.2");
    assertThat(found.getRationale().runId()).isEqualTo("20260701093000-000");
  }

  @Test
  @DisplayName("지수 컬럼은 NULL 을 허용한다 — 계산 불가를 0 이나 100 으로 채우지 않는다")
  void indexColumns_allowNull() {
    UUID userId = UUID.randomUUID();
    indexSnapshotRepository.saveAndFlush(
        new IndexSnapshot(
            userId,
            "202607",
            "20260701093000-000",
            "1.0.0",
            null,
            null,
            DecisionSignalCode.INSUFFICIENT_DATA,
            new SnapshotRationale(
                List.of("현재 연봉 미확보"), null, List.of(), null, null, null, "20260701093000-000")));

    IndexSnapshot found =
        indexSnapshotRepository
            .findByUserIdAndBaseYearMonthAndDataVersionAndRuleVersion(
                userId, "202607", "20260701093000-000", "1.0.0")
            .orElseThrow();

    assertThat(found.getPurchasingPowerIndex()).isNull();
    assertThat(found.getMarketValueIndex()).isNull();
    assertThat(found.getDecisionSignal()).isEqualTo(DecisionSignalCode.INSUFFICIENT_DATA);
  }

  @Test
  @DisplayName("같은 4-튜플 키로 다시 저장하면 행이 대체되고 늘어나지 않는다")
  void sameKey_replacesRow() {
    UUID userId = UUID.randomUUID();
    IndexSnapshot first =
        indexSnapshotRepository.saveAndFlush(
            new IndexSnapshot(
                userId,
                "202607",
                "20260701093000-000",
                "1.0.0",
                new BigDecimal("94.20"),
                new BigDecimal("117.50"),
                DecisionSignalCode.SEEK_JOB,
                rationale()));

    Optional<IndexSnapshot> existing =
        indexSnapshotRepository.findByUserIdAndBaseYearMonthAndDataVersionAndRuleVersion(
            userId, "202607", "20260701093000-000", "1.0.0");
    existing
        .orElseThrow()
        .replaceWith(
            new BigDecimal("91.00"),
            new BigDecimal("120.00"),
            DecisionSignalCode.NEGOTIATE,
            rationale());
    indexSnapshotRepository.flush();

    List<IndexSnapshot> all = indexSnapshotRepository.findByUserIdOrderByBaseYearMonthDesc(userId);
    assertThat(all).hasSize(1);
    assertThat(all.get(0).getId()).isEqualTo(first.getId());
    assertThat(all.get(0).getDecisionSignal()).isEqualTo(DecisionSignalCode.NEGOTIATE);
  }

  @Test
  @DisplayName("조회 짝 선택 - 먼저 최신 data_version, 그 안에서 최신 rule_version 을 고른다")
  void latest_picksNewestDataVersionThenNewestRuleVersion() {
    UUID userId = UUID.randomUUID();
    // 오래된 데이터 버전에 더 높은 규칙 버전이 달려 있어도, 데이터 버전이 먼저다
    indexSnapshotRepository.saveAndFlush(
        snapshot(userId, "20260701093000-000", "1.10.0", DecisionSignalCode.UPSKILL));
    indexSnapshotRepository.saveAndFlush(
        snapshot(userId, "20260801093000-000", "1.9.0", DecisionSignalCode.STAY));
    indexSnapshotRepository.saveAndFlush(
        snapshot(userId, "20260801093000-000", "1.10.0", DecisionSignalCode.NEGOTIATE));

    Optional<IndexSnapshot> latest =
        IndexSnapshotSelector.latest(
            indexSnapshotRepository.findByUserIdAndBaseYearMonth(userId, "202607"));

    // 사전순이면 1.9.0 이 1.10.0 보다 커서 STAY 가 뽑힌다 — 숫자 비교라 NEGOTIATE 다
    assertThat(latest).isPresent();
    assertThat(latest.get().getDecisionSignal()).isEqualTo(DecisionSignalCode.NEGOTIATE);
  }

  private static IndexSnapshot snapshot(
      UUID userId, String dataVersion, String ruleVersion, DecisionSignalCode signal) {
    return new IndexSnapshot(
        userId,
        "202607",
        dataVersion,
        ruleVersion,
        new BigDecimal("94.20"),
        new BigDecimal("117.50"),
        signal,
        rationale());
  }
}
