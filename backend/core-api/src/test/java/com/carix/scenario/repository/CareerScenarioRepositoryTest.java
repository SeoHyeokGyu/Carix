package com.carix.scenario.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.carix.scenario.CareerScenarioRepository;
import com.carix.scenario.domain.CareerScenario;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

/** 시나리오 저장 (개발 계획 §5-O·§5-P). */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CareerScenarioRepositoryTest {

  @Autowired private CareerScenarioRepository repository;
  @Autowired private TestEntityManager testEntityManager;

  @Test
  @DisplayName("금액 컬럼은 암호문으로 저장되고 엔티티로는 원래 값이 돌아온다")
  void 금액_암호화_왕복() {
    UUID userId = UUID.randomUUID();
    CareerScenario saved = repository.saveAndFlush(scenario(userId, 62_100_000L));
    testEntityManager.clear();

    // 저장된 컬럼 값 자체는 숫자가 아니다 — 평문이면 stay_expected_raise_rate 와 나눠 연봉이 복원된다
    Object stored =
        testEntityManager
            .getEntityManager()
            .createNativeQuery(
                "select stay_compensation_12m_encrypted from core.career_scenario where id = ?1")
            .setParameter(1, saved.getId())
            .getSingleResult();
    assertThat(stored.toString()).isNotEqualTo("62100000");
    assertThat(stored.toString()).doesNotContain("62100000");

    CareerScenario reloaded = repository.findById(saved.getId()).orElseThrow();
    assertThat(reloaded.getStayCompensation12m()).isEqualTo(62_100_000L);
    assertThat(reloaded.getCurrentGrossSalary()).isEqualTo(60_000_000L);
    assertThat(reloaded.getMoveTargetGrossSalary()).isEqualTo(72_000_000L);
    assertThat(reloaded.getDifference()).isEqualTo(-1_140_000L);
  }

  @Test
  @DisplayName("최근 1건은 calculated_at 이 가장 늦은 행이다")
  void 최근_1건() {
    UUID userId = UUID.randomUUID();
    CareerScenario older = repository.saveAndFlush(scenario(userId, 61_000_000L));
    CareerScenario newer = repository.saveAndFlush(scenario(userId, 63_000_000L));

    // 시각을 엔티티가 채우므로 두 행이 실제로 벌어진다. DB 기본값에 맡겼다면 H2 스키마에 기본값이 없어
    // 둘 다 NULL 이 되고, 이 정렬은 검증되지 않은 채 운영에서만 돌았을 것이다
    assertThat(older.getCalculatedAt()).isBefore(newer.getCalculatedAt());
    testEntityManager.clear();

    CareerScenario latest =
        repository.findFirstByUserIdOrderByCalculatedAtDescIdDesc(userId).orElseThrow();

    assertThat(latest.getId()).isEqualTo(newer.getId());
    assertThat(latest.getStayCompensation12m()).isEqualTo(63_000_000L);
  }

  @Test
  @DisplayName("다른 사용자의 시나리오는 보이지 않는다")
  void 사용자_격리() {
    repository.saveAndFlush(scenario(UUID.randomUUID(), 62_100_000L));

    assertThat(repository.findFirstByUserIdOrderByCalculatedAtDescIdDesc(UUID.randomUUID()))
        .isEmpty();
  }

  @Test
  @DisplayName("표본이 부족했던 시나리오는 백분위가 null 로 남는다")
  void 백분위_없음() {
    UUID userId = UUID.randomUUID();
    CareerScenario saved =
        repository.saveAndFlush(
            CareerScenario.builder()
                .userId(userId)
                .scenarioName("표본 부족")
                .currentGrossSalary(60_000_000L)
                .stayExpectedRaiseRate(new BigDecimal("3.50"))
                .moveTargetGrossSalary(72_000_000L)
                .moveOneTimeCost(0L)
                .expectedUnemploymentMonths(0)
                .eligibleForUnemploymentBenefit(false)
                .stayCompensation12m(62_100_000L)
                .moveCompensation12m(72_000_000L)
                .estimatedUnemploymentBenefit(0L)
                .difference(9_900_000L)
                .feasibilityStatus("INSUFFICIENT_SAMPLE")
                .feasibilitySampleSize(3)
                .build());
    testEntityManager.clear();

    CareerScenario reloaded = repository.findById(saved.getId()).orElseThrow();
    assertThat(reloaded.getFeasibilityPercentile()).isNull();
    assertThat(reloaded.getFeasibilityStatus()).isEqualTo("INSUFFICIENT_SAMPLE");
  }

  @Test
  @DisplayName("인상률은 numeric(5,2) 라 소수점 두 자리까지만 남는다")
  void 인상률_정밀도() {
    UUID userId = UUID.randomUUID();
    CareerScenario saved =
        repository.saveAndFlush(scenarioWithRate(userId, BigDecimal.valueOf(3.456)));
    testEntityManager.clear();

    CareerScenario reloaded = repository.findById(saved.getId()).orElseThrow();

    assertThat(reloaded.getStayExpectedRaiseRate()).isEqualByComparingTo("3.46");
  }

  private CareerScenario scenarioWithRate(UUID userId, BigDecimal rate) {
    return CareerScenario.builder()
        .userId(userId)
        .scenarioName("A사 오퍼 제안")
        .currentGrossSalary(60_000_000L)
        .stayExpectedRaiseRate(rate)
        .moveTargetGrossSalary(72_000_000L)
        .moveOneTimeCost(3_000_000L)
        .expectedUnemploymentMonths(2)
        .eligibleForUnemploymentBenefit(true)
        .stayCompensation12m(62_100_000L)
        .moveCompensation12m(60_960_000L)
        .estimatedUnemploymentBenefit(3_960_000L)
        .difference(-1_140_000L)
        .feasibilityStatus("OK")
        .feasibilityPercentile(69)
        .feasibilitySampleSize(42)
        .build();
  }

  private CareerScenario scenario(UUID userId, long stay12m) {
    return CareerScenario.builder()
        .userId(userId)
        .scenarioName("A사 오퍼 제안")
        .currentGrossSalary(60_000_000L)
        .stayExpectedRaiseRate(new BigDecimal("3.50"))
        .moveTargetGrossSalary(72_000_000L)
        .moveOneTimeCost(3_000_000L)
        .expectedUnemploymentMonths(2)
        .eligibleForUnemploymentBenefit(true)
        .stayCompensation12m(stay12m)
        .moveCompensation12m(60_960_000L)
        .estimatedUnemploymentBenefit(3_960_000L)
        .difference(-1_140_000L)
        .feasibilityStatus("OK")
        .feasibilityPercentile(69)
        .feasibilitySampleSize(42)
        .build();
  }
}
