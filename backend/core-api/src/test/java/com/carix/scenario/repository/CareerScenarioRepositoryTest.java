package com.carix.scenario.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.carix.scenario.CareerScenarioRepository;
import com.carix.scenario.domain.CareerScenario;
import com.carix.scenario.dto.ScenarioPayload;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import tools.jackson.databind.json.JsonMapper;

/** 시나리오 저장 (ERD 적용 이력·SECURITY §3.1). */
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
  @DisplayName("목록은 calculated_at 내림차순이고 본인 행만 담으며 초안은 뺀다")
  void 목록_정렬() {
    UUID userId = UUID.randomUUID();
    CareerScenario older = repository.saveAndFlush(scenario(userId, 61_000_000L));
    CareerScenario newer = repository.saveAndFlush(scenario(userId, 63_000_000L));
    repository.saveAndFlush(scenario(UUID.randomUUID(), 64_000_000L));
    repository.saveAndFlush(draft(userId, "{}"));
    testEntityManager.clear();

    List<CareerScenario> list =
        repository.findByUserIdAndDraftFalseOrderByCalculatedAtDescIdDesc(userId);

    assertThat(list)
        .extracting(CareerScenario::getId)
        .containsExactly(newer.getId(), older.getId());
  }

  @Test
  @DisplayName("저장하면 결과가 함께 암호화 저장된다 - payload 컬럼에 연봉 평문이 없다")
  void 저장하면_결과가_함께_암호화_저장된다() {
    UUID userId = UUID.randomUUID();
    String json =
        "{\"request\":{\"stay\":{\"currentGrossOverride\":60000000},"
            + "\"move\":{\"grossSalary\":72000000}},"
            + "\"result\":{\"firstYear\":{\"total\":-1140000}}}";
    CareerScenario saved =
        repository.saveAndFlush(
            CareerScenario.builder()
                .userId(userId)
                .engineVersion(CareerScenario.ENGINE_STAY_OR_MOVE)
                .payload(json)
                .build());
    testEntityManager.clear();

    Object stored =
        testEntityManager
            .getEntityManager()
            .createNativeQuery("select payload_encrypted from core.career_scenario where id = ?1")
            .setParameter(1, saved.getId())
            .getSingleResult();
    assertThat(stored.toString())
        .doesNotContain("72000000")
        .doesNotContain("60000000")
        .doesNotContain("grossSalary")
        .contains(":");

    assertThat(repository.findById(saved.getId()).orElseThrow().getPayload()).isEqualTo(json);
  }

  @Test
  @DisplayName("세 시점 블록 이전의 퇴직금 경고 모양이 든 저장분도 목록에서 읽힌다")
  void 옛_퇴직금_경고가_든_저장분도_읽힌다() {
    UUID userId = UUID.randomUUID();
    // 2026-11 이전 저장분은 result.severance 가 {monthsToAnniversary, estimatedAmount} 였다
    String json =
        "{\"request\":{\"stay\":{\"raisePercent\":3.0}},"
            + "\"result\":{\"severance\":{\"monthsToAnniversary\":2,\"estimatedAmount\":5000000}}}";
    repository.saveAndFlush(
        CareerScenario.builder()
            .userId(userId)
            .engineVersion(CareerScenario.ENGINE_STAY_OR_MOVE)
            .payload(json)
            .build());
    testEntityManager.clear();

    JsonMapper mapper = JsonMapper.builder().build();
    List<ScenarioPayload> payloads =
        repository.findByUserIdAndDraftFalseOrderByCalculatedAtDescIdDesc(userId).stream()
            .map(s -> mapper.readValue(s.getPayload(), ScenarioPayload.class))
            .toList();

    assertThat(payloads).hasSize(1);
    assertThat(payloads.get(0).result().severance().status()).isNull();
    assertThat(payloads.get(0).result().severance().points()).isEmpty();
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

  private CareerScenario draft(UUID userId, String payload) {
    return CareerScenario.builder()
        .userId(userId)
        .engineVersion(CareerScenario.ENGINE_STAY_OR_MOVE)
        .draft(true)
        .payload(payload)
        .build();
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
