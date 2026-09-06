package com.carix.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.carix.index.CurrentSalaryProvider;
import com.carix.index.dto.CurrentSalary;
import com.carix.peergroup.PeerSalaryDistribution;
import com.carix.peergroup.PeerSalaryDistributionResolver;
import com.carix.profile.domain.CareerProfile;
import com.carix.profile.repository.CareerProfileRepository;
import com.carix.scenario.domain.CareerScenario;
import com.carix.scenario.dto.LatestScenarioResponse;
import com.carix.scenario.dto.SimulationRequest;
import com.carix.scenario.dto.SimulationResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 시나리오 계산·저장·복원 (API 규격 §6.1·§6.2). */
class ScenarioServiceTest {

  private static final UUID USER_ID = UUID.randomUUID();

  private CareerProfileRepository careerProfileRepository;
  private PeerSalaryDistributionResolver distributionResolver;
  private CurrentSalaryProvider currentSalaryProvider;
  private CareerScenarioRepository repository;
  private ScenarioService service;

  @BeforeEach
  void setUp() {
    careerProfileRepository = mock(CareerProfileRepository.class);
    distributionResolver = mock(PeerSalaryDistributionResolver.class);
    currentSalaryProvider = mock(CurrentSalaryProvider.class);
    repository = mock(CareerScenarioRepository.class);
    given(repository.save(any(CareerScenario.class))).willAnswer(call -> call.getArgument(0));

    service =
        new ScenarioService(
            careerProfileRepository,
            distributionResolver,
            currentSalaryProvider,
            new ScenarioCalculator(new UnemploymentBenefitAssumption(66_000L, 60_120L, "2026-01")),
            repository);
  }

  // --- 연봉 결정 ---

  @Test
  @DisplayName("연봉을 직접 넣으면 소득 이력을 보지 않는다 — 가상 시뮬레이션 (규격 §6.1)")
  void 가상연봉_입력() {
    givenProfileWithDistribution(distribution(30, 20, 50_000_000L));

    SimulationResponse response = service.simulate(USER_ID, request(80_000_000L));

    // 80,000,000 × 1.035
    assertThat(response.stayCompensation12m()).isEqualTo(82_800_000L);
  }

  @Test
  @DisplayName("연봉을 생략하면 소득 이력의 최신 연봉을 쓴다")
  void 소득이력_사용() {
    givenProfileWithDistribution(distribution(30, 20, 50_000_000L));
    given(currentSalaryProvider.currentGrossSalary(any(), any()))
        .willReturn(Optional.of(new CurrentSalary(60_000_000L, LocalDate.of(2026, 1, 1))));

    SimulationResponse response = service.simulate(USER_ID, request(null));

    assertThat(response.stayCompensation12m()).isEqualTo(62_100_000L);
  }

  @Test
  @DisplayName("연봉도 소득 이력도 없으면 400 이다 — 입력으로 바로 풀리는 문제라 상태값이 아니다")
  void 연봉_없음() {
    givenProfileWithDistribution(distribution(30, 20, 50_000_000L));
    given(currentSalaryProvider.currentGrossSalary(any(), any())).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.simulate(USER_ID, request(null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("currentGrossSalary");
  }

  // --- 실현 가능성 ---

  @Test
  @DisplayName("실현 가능성은 실제 동료집단 공개연봉 분포에서 나온다")
  void 실현가능성_실데이터() {
    // 5,000만 ~ 9,900만 100건. 목표 7,200만보다 낮은 건 44건 → 44%
    givenProfileWithDistribution(distribution(100, 100, 50_000_000L));

    SimulationResponse.FeasibilityDetail feasibility =
        service.simulate(USER_ID, request(60_000_000L)).feasibility();

    assertThat(feasibility.status()).isEqualTo("OK");
    assertThat(feasibility.targetPercentile()).isEqualTo(44);
    assertThat(feasibility.feasibilityScore()).isEqualTo(56);
    assertThat(feasibility.difficultyGrade()).isEqualTo("REALISTIC");
    assertThat(feasibility.sampleSize()).isEqualTo(100);
    assertThat(feasibility.minRequired()).isNull();
  }

  @Test
  @DisplayName("동료집단 표본이 부족하면 백분위를 지어내지 않는다")
  void 실현가능성_동료집단_부족() {
    givenProfileWithDistribution(distribution(4, 4, 50_000_000L));

    SimulationResponse.FeasibilityDetail feasibility =
        service.simulate(USER_ID, request(60_000_000L)).feasibility();

    assertThat(feasibility.status()).isEqualTo("INSUFFICIENT_SAMPLE");
    assertThat(feasibility.targetPercentile()).isNull();
    assertThat(feasibility.feasibilityScore()).isNull();
    assertThat(feasibility.difficultyGrade()).isNull();
    assertThat(feasibility.feedbackMessage()).isNull();
    assertThat(feasibility.minRequired()).isEqualTo(10);
  }

  @Test
  @DisplayName("동료집단은 충분해도 공개연봉이 10건 미만이면 백분위를 내지 않는다")
  void 실현가능성_공개연봉_부족() {
    // 동료집단 40건인데 그중 연봉이 공개된 건 3건뿐이다
    givenProfileWithDistribution(distribution(40, 3, 50_000_000L));

    SimulationResponse.FeasibilityDetail feasibility =
        service.simulate(USER_ID, request(60_000_000L)).feasibility();

    assertThat(feasibility.status()).isEqualTo("INSUFFICIENT_SAMPLE");
    assertThat(feasibility.targetPercentile()).isNull();
    assertThat(feasibility.sampleSize()).isEqualTo(3);
  }

  @Test
  @DisplayName("온보딩 전이면 동료집단 조건이 없어 실현 가능성도 없다")
  void 실현가능성_프로필_없음() {
    given(careerProfileRepository.findByUserId(USER_ID)).willReturn(Optional.empty());

    SimulationResponse.FeasibilityDetail feasibility =
        service.simulate(USER_ID, request(60_000_000L)).feasibility();

    assertThat(feasibility.status()).isEqualTo("INSUFFICIENT_SAMPLE");
    assertThat(feasibility.sampleSize()).isZero();
  }

  // --- 저장 ---

  @Test
  @DisplayName("계산 성공 시 입력 가정과 결과를 저장한다 (규격 §6.1)")
  void 저장() {
    givenProfileWithDistribution(distribution(100, 100, 50_000_000L));

    service.simulate(USER_ID, request(60_000_000L));

    org.mockito.ArgumentCaptor<CareerScenario> captor =
        org.mockito.ArgumentCaptor.forClass(CareerScenario.class);
    org.mockito.Mockito.verify(repository).save(captor.capture());
    CareerScenario saved = captor.getValue();

    assertThat(saved.getUserId()).isEqualTo(USER_ID);
    assertThat(saved.getScenarioName()).isEqualTo("A사 오퍼 제안");
    assertThat(saved.getCurrentGrossSalary()).isEqualTo(60_000_000L);
    assertThat(saved.getStayCompensation12m()).isEqualTo(62_100_000L);
    assertThat(saved.getEstimatedUnemploymentBenefit()).isEqualTo(3_960_000L);
    assertThat(saved.getDifference()).isEqualTo(-1_140_000L);
    assertThat(saved.getFeasibilityStatus()).isEqualTo("OK");
    assertThat(saved.getFeasibilityPercentile()).isEqualTo(44);
  }

  @Test
  @DisplayName("이름을 생략하면 기본명을 붙인다")
  void 기본_시나리오명() {
    givenProfileWithDistribution(distribution(30, 20, 50_000_000L));

    SimulationResponse response =
        service.simulate(
            USER_ID,
            new SimulationRequest(null, 60_000_000L, 3.5, 72_000_000L, 3_000_000L, 2, true));

    assertThat(response.scenarioName()).isEqualTo("기본 시나리오");
  }

  // --- 복원 ---

  @Test
  @DisplayName("저장된 시나리오가 없으면 empty 다 — 컨트롤러가 204 로 옮긴다")
  void 최근_없음() {
    given(repository.findFirstByUserIdOrderByCalculatedAtDescIdDesc(USER_ID))
        .willReturn(Optional.empty());

    assertThat(service.latest(USER_ID)).isEmpty();
  }

  @Test
  @DisplayName("최근 시나리오는 입력과 결과를 저장분 그대로 되살린다")
  void 최근_복원() {
    given(repository.findFirstByUserIdOrderByCalculatedAtDescIdDesc(USER_ID))
        .willReturn(Optional.of(storedScenario()));

    LatestScenarioResponse latest = service.latest(USER_ID).orElseThrow();

    assertThat(latest.scenarioName()).isEqualTo("A사 오퍼 제안");
    assertThat(latest.inputs().currentGrossSalary()).isEqualTo(60_000_000L);
    assertThat(latest.inputs().stayExpectedRaiseRate()).isEqualTo(3.5);
    assertThat(latest.result().difference()).isEqualTo(-1_140_000L);
    assertThat(latest.result().type()).isEqualTo("ESTIMATED_OPPORTUNITY_COST");
    assertThat(latest.calculatedAt()).isNotNull();
  }

  @Test
  @DisplayName("복원한 실현 가능성은 저장한 백분위에서 등급과 문구를 다시 만든다 — 분포를 재계산하지 않는다")
  void 최근_실현가능성_복원() {
    given(repository.findFirstByUserIdOrderByCalculatedAtDescIdDesc(USER_ID))
        .willReturn(Optional.of(storedScenario()));

    SimulationResponse.FeasibilityDetail feasibility =
        service.latest(USER_ID).orElseThrow().result().feasibility();

    assertThat(feasibility.targetPercentile()).isEqualTo(69);
    assertThat(feasibility.difficultyGrade()).isEqualTo("CHALLENGING");
    assertThat(feasibility.feedbackMessage()).contains("상위 31%");
    // 저장분만 읽으므로 분포 조회가 일어나지 않는다
    org.mockito.Mockito.verifyNoInteractions(distributionResolver);
  }

  @Test
  @DisplayName("저장분이 OK 인데 백분위가 없으면 OK 라고 말하지 않는다")
  void 최근_상태불일치() {
    CareerScenario broken =
        CareerScenario.builder()
            .userId(USER_ID)
            .scenarioName("어긋난 저장분")
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
            .feasibilityStatus("OK")
            .feasibilitySampleSize(3)
            .build();
    given(repository.findFirstByUserIdOrderByCalculatedAtDescIdDesc(USER_ID))
        .willReturn(Optional.of(broken));

    SimulationResponse.FeasibilityDetail feasibility =
        service.latest(USER_ID).orElseThrow().result().feasibility();

    assertThat(feasibility.status()).isEqualTo("INSUFFICIENT_SAMPLE");
    assertThat(feasibility.difficultyGrade()).isNull();
  }

  // --- 픽스처 ---

  private SimulationRequest request(Long currentGrossSalary) {
    return new SimulationRequest(
        "A사 오퍼 제안", currentGrossSalary, 3.5, 72_000_000L, 3_000_000L, 2, true);
  }

  /** 공개연봉을 {@code from} 부터 50만원씩 올려 {@code disclosed} 건 만든다. */
  private PeerSalaryDistribution distribution(int peerSize, int disclosed, long from) {
    List<Long> salaries = new ArrayList<>();
    for (int i = 0; i < disclosed; i++) {
      salaries.add(from + i * 500_000L);
    }
    return new PeerSalaryDistribution(salaries, peerSize, 3, "서울 전체 / 경력 MID / 회사규모 무관");
  }

  private void givenProfileWithDistribution(PeerSalaryDistribution distribution) {
    CareerProfile profile = CareerProfile.builder().userId(USER_ID).build();
    given(careerProfileRepository.findByUserId(USER_ID)).willReturn(Optional.of(profile));
    given(distributionResolver.resolve(profile)).willReturn(distribution);
  }

  private CareerScenario storedScenario() {
    return CareerScenario.builder()
        .id(UUID.randomUUID())
        .userId(USER_ID)
        .scenarioName("A사 오퍼 제안")
        .currentGrossSalary(60_000_000L)
        .stayExpectedRaiseRate(new BigDecimal("3.50"))
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
        .calculatedAt(OffsetDateTime.of(2026, 7, 18, 17, 0, 0, 0, ZoneOffset.UTC))
        .build();
  }
}
