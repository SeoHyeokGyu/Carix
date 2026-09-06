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
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class IndexSnapshotRepositoryTest {

  @Autowired private IndexSnapshotRepository indexSnapshotRepository;
  @Autowired private TestEntityManager testEntityManager;

  private static SnapshotRationale rationale() {
    return new SnapshotRationale(
        List.of("연봉 격차 15% 이상", "보유 스킬 시장수요 상승세"),
        "BELOW_MARKET_15_PLUS",
        List.of(),
        List.of(),
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
    SnapshotRationale original = rationale();
    indexSnapshotRepository.saveAndFlush(
        new IndexSnapshot(
            userId,
            "202607",
            "20260701093000-000",
            "1.0.0",
            new BigDecimal("94.20"),
            new BigDecimal("117.50"),
            DecisionSignalCode.SEEK_JOB,
            original));

    // saveAndFlush 만으로는 영속성 컨텍스트에 같은 인스턴스가 남아 있어 이후 조회가 캐시를 그대로 반환한다 —
    // clear() 로 컨텍스트를 비워야 JSON 컬럼이 실제로 DB 에서 역직렬화된다.
    testEntityManager.clear();

    IndexSnapshot found =
        indexSnapshotRepository
            .findByUserIdAndBaseYearMonthAndDataVersionAndRuleVersion(
                userId, "202607", "20260701093000-000", "1.0.0")
            .orElseThrow();

    SnapshotRationale reread = found.getRationale();
    assertThat(reread.rulesApplied()).containsExactly("연봉 격차 15% 이상", "보유 스킬 시장수요 상승세");
    assertThat(reread.salaryGapBucket()).isEqualTo("BELOW_MARKET_15_PLUS");
    assertThat(reread.runId()).isEqualTo("20260701093000-000");

    // 되읽은 rationale 이 원본과 값으로 동일한지 필드별로 확인한다. BigDecimal 은 JSON 왕복에서 scale 이
    // 바뀔 수 있어(레코드의 기본 equals 는 scale 까지 비교) isEqualByComparingTo 로 따로 비교한다.
    assertThat(reread.rulesApplied()).isEqualTo(original.rulesApplied());
    assertThat(reread.salaryGapBucket()).isEqualTo(original.salaryGapBucket());
    assertThat(reread.excludedSkills()).isEqualTo(original.excludedSkills());
    assertThat(reread.cpiAsOf()).isEqualTo(original.cpiAsOf());
    assertThat(reread.aggregateAsOf()).isEqualTo(original.aggregateAsOf());
    assertThat(reread.wageStatVintage()).isEqualTo(original.wageStatVintage());
    assertThat(reread.runId()).isEqualTo(original.runId());
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
                List.of("현재 연봉 미확보"),
                null,
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                null,
                "20260701093000-000")));

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
    // 오래된 데이터 버전에 더 높은 규칙 버전이 달려 있어도, 데이터 버전이 먼저다 — 1.11.0 은 최신
    // data_version 그룹의 어떤 rule_version 보다도 커서, 우선순위가 뒤바뀌면 이 행이 뽑힌다
    indexSnapshotRepository.saveAndFlush(
        snapshot(userId, "20260701093000-000", "1.11.0", DecisionSignalCode.UPSKILL));
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
