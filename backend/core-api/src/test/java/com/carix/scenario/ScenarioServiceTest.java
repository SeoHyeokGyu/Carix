package com.carix.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.carix.peergroup.PeerSalaryDistribution;
import com.carix.peergroup.PeerSalaryDistributionResolver;
import com.carix.profile.domain.CareerProfile;
import com.carix.profile.repository.CareerProfileRepository;
import com.carix.scenario.domain.CareerScenario;
import com.carix.scenario.dto.EvaluateRequest;
import com.carix.scenario.dto.EvaluateRequest.Move;
import com.carix.scenario.dto.EvaluateRequest.Stay;
import com.carix.scenario.dto.EvaluationResponse;
import com.carix.scenario.dto.EvaluationResponse.MoveView;
import com.carix.scenario.dto.EvaluationResponse.StayView;
import com.carix.scenario.dto.ScenarioListItem;
import com.carix.scenario.dto.SimulationResponse;
import com.carix.scenario.inflation.InflationBasis;
import com.carix.scenario.projection.ProjectionResult.FirstYear;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** 시나리오 저장·초안·목록과 실현 가능성 (API 규격 §6.1~§6.3). */
class ScenarioServiceTest {

  private static final UUID USER_ID = UUID.randomUUID();
  private static final UUID OTHER_USER_ID = UUID.randomUUID();

  private CareerProfileRepository careerProfileRepository;
  private PeerSalaryDistributionResolver distributionResolver;
  private CareerScenarioRepository repository;
  private final List<CareerScenario> store = new ArrayList<>();
  private ScenarioService service;

  @BeforeEach
  void setUp() {
    careerProfileRepository = mock(CareerProfileRepository.class);
    distributionResolver = mock(PeerSalaryDistributionResolver.class);
    repository = mock(CareerScenarioRepository.class);
    given(repository.save(any(CareerScenario.class))).willAnswer(call -> add(call.getArgument(0)));
    given(repository.saveAndFlush(any(CareerScenario.class)))
        .willAnswer(call -> add(call.getArgument(0)));
    given(repository.findFirstByUserIdAndDraftTrue(any(UUID.class)))
        .willAnswer(
            call ->
                store.stream()
                    .filter(s -> s.isDraft() && s.getUserId().equals(call.getArgument(0)))
                    .findFirst());
    given(repository.findByUserIdAndDraftFalseOrderByCalculatedAtDescIdDesc(any(UUID.class)))
        .willAnswer(
            call ->
                store.stream()
                    .filter(s -> !s.isDraft() && s.getUserId().equals(call.getArgument(0)))
                    .toList());

    service =
        new ScenarioService(
            careerProfileRepository,
            distributionResolver,
            new ScenarioCalculator(new UnemploymentBenefitAssumption(66_000L, 60_120L, "2026-01")),
            repository,
            JsonMapper.builder().build());
  }

  private CareerScenario add(CareerScenario scenario) {
    if (!store.contains(scenario)) {
      store.add(scenario);
    }
    return scenario;
  }

  // --- 저장·초안 ---

  @Test
  @DisplayName("저장하면 요청과 결과를 payload 한 덩어리로 담고 목록에서 그대로 되살린다")
  void 저장() {
    EvaluateRequest request = request(3.5);
    EvaluationResponse result = result();

    ScenarioListItem saved = service.save(USER_ID, "  A사 오퍼  ", request, result);

    CareerScenario row = store.get(0);
    assertThat(row.getEngineVersion()).isEqualTo("2");
    assertThat(row.isDraft()).isFalse();
    assertThat(row.getPayload()).contains("\"request\"").contains("\"result\"");
    assertThat(saved.name()).isEqualTo("A사 오퍼");
    assertThat(saved.engineVersion()).isEqualTo(2);
    assertThat(saved.legacy()).isNull();

    ScenarioListItem listed = service.list(USER_ID).get(0);
    assertThat(listed.request()).isEqualTo(request);
    assertThat(listed.result().move().stayCumulative()).containsExactly(1L, 2L);
    assertThat(listed.result().move().firstYear()).isEqualTo(result.move().firstYear());
    assertThat(listed.result().inflation()).isEqualTo(result.inflation());
  }

  @Test
  @DisplayName("공백뿐인 이름은 이름 없음이다")
  void 이름_공백() {
    service.save(USER_ID, "   ", request(3.5), result());

    assertThat(store.get(0).getScenarioName()).isNull();
  }

  @Test
  @DisplayName("초안은 사용자당 한 건이다 - 다시 저장하면 덮어쓴다")
  void 초안은_사용자당_한_건() {
    service.saveDraft(USER_ID, request(3.5));
    service.saveDraft(USER_ID, request(7.0));

    assertThat(store).filteredOn(CareerScenario::isDraft).hasSize(1);
    assertThat(service.draft(USER_ID)).contains(request(7.0));
  }

  @Test
  @DisplayName("초안은 결과를 담지 않는다")
  void 초안_목록_제외() {
    service.saveDraft(USER_ID, request(3.5));

    assertThat(store.get(0).getPayload()).doesNotContain("\"stayCumulative\"");
  }

  @Test
  @DisplayName("남의 초안은 안 보인다")
  void 남의_초안은_안_보인다() {
    service.saveDraft(OTHER_USER_ID, request(3.5));

    assertThat(service.draft(USER_ID)).isEmpty();

    service.saveDraft(USER_ID, request(7.0));

    assertThat(store).filteredOn(CareerScenario::isDraft).hasSize(2);
    assertThat(service.draft(OTHER_USER_ID)).contains(request(3.5));
  }

  // --- 엔진 1 목록 (규격 §6.3) ---

  @Test
  @DisplayName("엔진 1 행은 저장한 값 그대로 legacy 로 나온다 - 분포를 다시 구하지 않는다")
  void 엔진1_행은_legacy로_재계산_없이_나온다() {
    store.add(legacyScenario("A사 오퍼 제안", "OK", 69));

    ScenarioListItem item = service.list(USER_ID).get(0);

    assertThat(item.engineVersion()).isEqualTo(1);
    assertThat(item.request()).isNull();
    assertThat(item.result()).isNull();
    assertThat(item.name()).isEqualTo("A사 오퍼 제안");
    assertThat(item.legacy().inputs().currentGrossSalary()).isEqualTo(60_000_000L);
    assertThat(item.legacy().inputs().stayExpectedRaiseRate()).isEqualTo(3.5);
    assertThat(item.legacy().result().difference()).isEqualTo(-1_140_000L);
    assertThat(item.legacy().result().type()).isEqualTo("ESTIMATED_OPPORTUNITY_COST");
    assertThat(item.legacy().result().unemploymentBenefitNote())
        .isEqualTo("가정 기반 추정 (기준 시점: 2026-01)");
    SimulationResponse.FeasibilityDetail feasibility = item.legacy().result().feasibility();
    assertThat(feasibility.targetPercentile()).isEqualTo(69);
    assertThat(feasibility.difficultyGrade()).isEqualTo("CHALLENGING");
    assertThat(feasibility.feedbackMessage()).contains("상위 31%");
    org.mockito.Mockito.verifyNoInteractions(distributionResolver, careerProfileRepository);
  }

  @Test
  @DisplayName("저장분이 OK 인데 백분위가 없으면 OK 라고 말하지 않는다")
  void 엔진1_상태불일치() {
    store.add(legacyScenario("어긋난 저장분", "OK", null));

    SimulationResponse.FeasibilityDetail feasibility =
        service.list(USER_ID).get(0).legacy().result().feasibility();

    assertThat(feasibility.status()).isEqualTo("INSUFFICIENT_SAMPLE");
    assertThat(feasibility.difficultyGrade()).isNull();
  }

  // --- 실현 가능성 ---

  @Test
  @DisplayName("실현 가능성은 실제 동료집단 공개연봉 분포에서 나온다")
  void 실현가능성_실데이터() {
    // 5,000만 ~ 9,900만 100건. 목표 7,200만보다 낮은 건 44건 → 44%
    givenProfileWithDistribution(distribution(100, 100, 50_000_000L));

    SimulationResponse.FeasibilityDetail feasibility =
        service.feasibilityDetail(USER_ID, 72_000_000L);

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
        service.feasibilityDetail(USER_ID, 72_000_000L);

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
        service.feasibilityDetail(USER_ID, 72_000_000L);

    assertThat(feasibility.status()).isEqualTo("INSUFFICIENT_SAMPLE");
    assertThat(feasibility.targetPercentile()).isNull();
    assertThat(feasibility.sampleSize()).isEqualTo(3);
  }

  @Test
  @DisplayName("온보딩 전이면 동료집단 조건이 없어 실현 가능성도 없다")
  void 실현가능성_프로필_없음() {
    given(careerProfileRepository.findByUserId(USER_ID)).willReturn(Optional.empty());

    SimulationResponse.FeasibilityDetail feasibility =
        service.feasibilityDetail(USER_ID, 72_000_000L);

    assertThat(feasibility.status()).isEqualTo("INSUFFICIENT_SAMPLE");
    assertThat(feasibility.sampleSize()).isZero();
  }

  // --- 픽스처 ---

  private EvaluateRequest request(double raisePercent) {
    return new EvaluateRequest(
        new Stay(raisePercent, 60_000_000L, null, null, null),
        new Move(72_000_000L, 2, 3_000_000L, true, false, null, null, null, null));
  }

  private EvaluationResponse result() {
    return new EvaluationResponse(
        new StayView(1.2, 0.8, 4.1, 3.7, 5.5),
        new MoveView(
            new long[] {1L, 2L},
            new long[] {3L, 4L},
            7,
            new FirstYear(12_000_000L, -5_000_000L, -3_000_000L, 3_960_000L, 7_000_000L),
            new EvaluationResponse.Range(0L, 1_000_000L),
            null,
            6.1,
            new SimulationResponse.FeasibilityDetail(
                "OK", 42, null, 31, 69, "CHALLENGING", "도전적인 목표입니다.")),
        new InflationBasis(
            new BigDecimal("2.1"),
            new BigDecimal("2.4"),
            new BigDecimal("0.3"),
            null,
            false,
            "2026-10",
            "한국은행"),
        List.of(new EvaluationResponse.Assumption("TAX", "본인 1인 기준")),
        List.of("STOCK"),
        null,
        "옮기면 추정 이득");
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

  @Test
  @DisplayName("오퍼 위치 문장은 백분위 50 부터 상위, 그 아래는 하위로 읽고 양 끝은 1% 미만이다")
  void 오퍼_위치_문장의_상위_하위_경계() {
    assertThat(feedbackFor(50)).startsWith("새 회사 연봉은 동료집단 상위 50% 수준");
    assertThat(feedbackFor(49)).startsWith("새 회사 연봉은 동료집단 하위 49% 수준");
    assertThat(feedbackFor(0)).startsWith("새 회사 연봉은 동료집단 하위 1% 미만 수준");
    assertThat(feedbackFor(100)).startsWith("새 회사 연봉은 동료집단 상위 1% 미만 수준");
  }

  private String feedbackFor(int percentile) {
    store.clear();
    store.add(legacyScenario("경계", "OK", percentile));
    return service.list(USER_ID).get(0).legacy().result().feasibility().feedbackMessage();
  }

  private CareerScenario legacyScenario(String name, String status, Integer percentile) {
    return CareerScenario.builder()
        .id(UUID.randomUUID())
        .userId(USER_ID)
        .scenarioName(name)
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
        .feasibilityStatus(status)
        .feasibilityPercentile(percentile)
        .feasibilitySampleSize(42)
        .calculatedAt(OffsetDateTime.of(2026, 7, 18, 17, 0, 0, 0, ZoneOffset.UTC))
        .build();
  }
}
