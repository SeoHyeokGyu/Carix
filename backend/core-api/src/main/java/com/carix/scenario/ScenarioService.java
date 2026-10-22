package com.carix.scenario;

import com.carix.index.CurrentSalaryProvider;
import com.carix.index.dto.CurrentSalary;
import com.carix.index.mvi.PeerGroupFallbackResolver;
import com.carix.peergroup.PeerSalaryDistribution;
import com.carix.peergroup.PeerSalaryDistributionResolver;
import com.carix.profile.domain.CareerProfile;
import com.carix.profile.repository.CareerProfileRepository;
import com.carix.scenario.domain.CareerScenario;
import com.carix.scenario.dto.LatestScenarioResponse;
import com.carix.scenario.dto.SimulationRequest;
import com.carix.scenario.dto.SimulationResponse;
import com.carix.snapshot.YearMonths;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 잔류 vs 이직 시나리오 시뮬레이션 (API 규격 §6.1·§6.2).
 *
 * <p>실현 가능성은 {@link PeerSalaryDistributionResolver} 가 낸 실제 동료집단 공고 분포에서 나온다. 종전 구현은 {@code min=45M,
 * p25=58M, median=65M, p75=74M, max=95M} 을 코드에 박고 보간했는데, 그 다섯 값의 출처가 없어 백분위와 피드백 문구가 근거 없는 수치였다 —
 * 규격 §6.1 은 이 값이 §5.2 동료집단 공고 분포에서 와야 한다고 명시한다.
 */
@Service
@RequiredArgsConstructor
public class ScenarioService {

  static final String STATUS_OK = "OK";
  static final String STATUS_INSUFFICIENT_SAMPLE = "INSUFFICIENT_SAMPLE";

  private static final String WARNING_NOTE = "이 계산에는 세금, 성과급, 퇴직금 등이 포함되지 않은 단순 세전 기준 시뮬레이션입니다.";

  private static final int GRADE_REALISTIC_FROM = 30;
  private static final int GRADE_CHALLENGING_FROM = 60;
  private static final int GRADE_VERY_HIGH_FROM = 85;
  private static final int TOP_TIER_FROM = 75;

  private final CareerProfileRepository careerProfileRepository;
  private final PeerSalaryDistributionResolver peerSalaryDistributionResolver;
  private final CurrentSalaryProvider currentSalaryProvider;
  private final ScenarioCalculator calculator;
  private final CareerScenarioRepository repository;

  /** 계산하고 저장한다. 규격 §6.1 이 "계산 성공 시 입력 가정과 결과를 저장합니다" 를 요구한다. */
  @Transactional
  public SimulationResponse simulate(UUID userId, SimulationRequest request) {
    long currentSalary = resolveCurrentSalary(userId, request.currentGrossSalary());

    ScenarioOutcome outcome =
        calculator.calculate(
            currentSalary,
            request.stayExpectedRaiseRate(),
            request.moveTargetGrossSalary(),
            request.moveOneTimeCost(),
            request.expectedUnemploymentMonths(),
            request.isEligibleForUnemploymentBenefit());

    Feasibility feasibility = resolveFeasibility(userId, request.moveTargetGrossSalary());
    String name = normalizeName(request.scenarioName());

    CareerScenario saved =
        repository.save(
            CareerScenario.builder()
                .userId(userId)
                .scenarioName(name)
                .currentGrossSalary(currentSalary)
                .stayExpectedRaiseRate(BigDecimal.valueOf(request.stayExpectedRaiseRate()))
                .moveTargetGrossSalary(request.moveTargetGrossSalary())
                .moveOneTimeCost(request.moveOneTimeCost())
                .expectedUnemploymentMonths(request.expectedUnemploymentMonths())
                .eligibleForUnemploymentBenefit(request.isEligibleForUnemploymentBenefit())
                .stayCompensation12m(outcome.stayCompensation12m())
                .moveCompensation12m(outcome.moveCompensation12m())
                .estimatedUnemploymentBenefit(outcome.estimatedUnemploymentBenefit())
                .difference(outcome.difference())
                .feasibilityStatus(feasibility.status())
                .feasibilityPercentile(feasibility.percentile())
                .feasibilitySampleSize(feasibility.sampleSize())
                .build());

    return toResponse(saved.getId(), name, outcome, feasibility);
  }

  /** 저장된 시나리오가 없으면 empty 다 — 컨트롤러가 204 로 옮긴다 (규격 §6.2). */
  @Transactional(readOnly = true)
  public Optional<LatestScenarioResponse> latest(UUID userId) {
    return repository.findFirstByUserIdOrderByCalculatedAtDescIdDesc(userId).map(this::toLatest);
  }

  /** 저장된 시나리오 전부, 최근 순 (규격 §6.3). 응답에 복호화된 금액이 여러 건 실리므로 로그에 남기지 않는다 (완료 기준 10번). */
  @Transactional(readOnly = true)
  public List<LatestScenarioResponse> list(UUID userId) {
    return repository.findByUserIdOrderByCalculatedAtDescIdDesc(userId).stream()
        .map(this::toLatest)
        .toList();
  }

  /**
   * 이름은 선택이다. 비었거나 공백뿐이면 이름 없이 저장한다 — 목록이 계산 시각으로 라벨을 만든다 (기능 명세 US-S1). 종전에는 "기본 시나리오" 를 채워 넣어 이름
   * 없는 행이 생기지 않았다.
   */
  private static String normalizeName(String requested) {
    if (requested == null || requested.isBlank()) {
      return null;
    }
    return requested.strip();
  }

  // --- 연봉 ---

  /**
   * 시뮬레이션에 쓸 현재 연봉.
   *
   * <p>입력값이 있으면 그것이다 — 규격 §6.1 이 "가상 시뮬레이션을 위해 임의의 세전 연봉을 지정" 하도록 허용한다. 없으면 소득 이력에서 온다.
   *
   * <p>둘 다 없으면 400 이다. 데이터 부재 상태가 아니라, 호출자가 {@code currentGrossSalary} 를 넣으면 바로 풀리는 입력 문제다.
   */
  private long resolveCurrentSalary(UUID userId, Long requested) {
    if (requested != null) {
      return requested;
    }
    Optional<CurrentSalary> stored =
        currentSalaryProvider.currentGrossSalary(userId, YearMonths.current());
    if (stored.isEmpty()) {
      throw new IllegalArgumentException("소득 이력이 없습니다. currentGrossSalary 를 직접 입력해 주세요.");
    }
    return stored.get().grossSalary();
  }

  // --- 실현 가능성 ---

  /**
   * 목표 연봉의 동료집단 내 백분위.
   *
   * <p>두 기준을 모두 넘어야 산출한다 — 동료집단 공고 {@code >= MIN_SAMPLE_SIZE}, 그리고 그중 <b>공개연봉도</b> {@code >=
   * MIN_SAMPLE_SIZE}. 규격 §5.2 의 최소 표본은 동료집단에만 걸리는데, 그쪽 5수치는 규격이 참고 표시 전용으로 두는 반면 백분위는 분포 모양에 대한
   * 주장이라 공개연봉 한두 건 위에서 내면 안 된다.
   */
  private Feasibility resolveFeasibility(UUID userId, long targetSalary) {
    Optional<CareerProfile> profile = careerProfileRepository.findByUserId(userId);
    if (profile.isEmpty()) {
      // 온보딩 미완료. 동료집단 조건 자체가 없다.
      return Feasibility.insufficient(0);
    }

    PeerSalaryDistribution distribution = peerSalaryDistributionResolver.resolve(profile.get());
    if (!distribution.hasSufficientPeers() || !distribution.hasSufficientSalaries()) {
      return Feasibility.insufficient(distribution.disclosedSampleSize());
    }

    return new Feasibility(
        STATUS_OK, distribution.percentileOf(targetSalary), distribution.disclosedSampleSize());
  }

  /** 저장하지 않고 목표 연봉의 실현 가능성만 구한다 — 시뮬레이터의 목표 연봉 입력 옆 표시용. */
  @Transactional(readOnly = true)
  public SimulationResponse.FeasibilityDetail feasibilityDetail(UUID userId, long targetSalary) {
    return toFeasibilityDetail(resolveFeasibility(userId, targetSalary));
  }

  private SimulationResponse.FeasibilityDetail toFeasibilityDetail(Feasibility feasibility) {
    if (feasibility.percentile() == null) {
      // 백분위가 없으면 상태도 OK 일 수 없다. 저장분이 어긋나 있어도 화면에 `OK + 전부 null` 을
      // 내보내지 않는다 — 지수와 같은 규약이다
      return new SimulationResponse.FeasibilityDetail(
          STATUS_INSUFFICIENT_SAMPLE,
          feasibility.sampleSize(),
          PeerGroupFallbackResolver.MIN_SAMPLE_SIZE,
          null,
          null,
          null,
          null);
    }
    int percentile = feasibility.percentile();
    return new SimulationResponse.FeasibilityDetail(
        feasibility.status(),
        feasibility.sampleSize(),
        null,
        100 - percentile,
        percentile,
        grade(percentile),
        feedback(percentile));
  }

  private String grade(int percentile) {
    if (percentile < GRADE_REALISTIC_FROM) {
      return "EASY";
    }
    if (percentile < GRADE_CHALLENGING_FROM) {
      return "REALISTIC";
    }
    return percentile < GRADE_VERY_HIGH_FROM ? "CHALLENGING" : "VERY_HIGH_CHALLENGE";
  }

  private String gradeLabel(int percentile) {
    if (percentile < GRADE_REALISTIC_FROM) {
      return "무난하게 달성 가능한";
    }
    if (percentile < GRADE_CHALLENGING_FROM) {
      return "실현 가능한 적정선 수준의";
    }
    return percentile < GRADE_VERY_HIGH_FROM ? "도전적인" : "매우 공격적인 최상위권";
  }

  private String feedback(int percentile) {
    return "목표 연봉은 동료집단 내 상위 %d%% 수준의 %s 목표입니다. %s"
        .formatted(
            100 - percentile,
            gradeLabel(percentile),
            percentile >= TOP_TIER_FROM
                ? "동료집단 내에서 상위권 연봉에 속하며 충분한 스킬 요건을 증명해야 할 수 있습니다."
                : "시장 평균 범위 내에 위치하여 원만히 조율 가능한 수준입니다.");
  }

  // --- 매핑 ---

  private SimulationResponse toResponse(
      UUID id, String name, ScenarioOutcome outcome, Feasibility feasibility) {
    return new SimulationResponse(
        id,
        name,
        outcome.stayCompensation12m(),
        outcome.moveCompensation12m(),
        outcome.estimatedUnemploymentBenefit(),
        outcome.unemploymentBenefitNote(),
        outcome.difference(),
        outcome.type(),
        outcome.typeLabel(),
        WARNING_NOTE,
        toFeasibilityDetail(feasibility));
  }

  /**
   * 저장분을 규격 §6.2 형태로 되살린다.
   *
   * <p>실현 가능성은 저장한 백분위에서 등급·점수·문구를 다시 만든다. 백분위 자체를 다시 구하지 않는 이유는 동료집단 분포가 그 시점의 공고에 의존해, 나중에 재계산하면
   * 사용자가 실제로 본 판단이 나오지 않아서다.
   *
   * <p>{@code unemploymentBenefitNote} 의 기준 시점만은 <b>현재</b> 설정값이다. 가정치가 갱신되면 과거 시나리오의 안내도 새 시점을 가리킨다
   * — 금액은 저장분 그대로이므로 숫자가 바뀌지는 않는다.
   */
  private LatestScenarioResponse toLatest(CareerScenario scenario) {
    SimulationRequest inputs =
        new SimulationRequest(
            scenario.getScenarioName(),
            scenario.getCurrentGrossSalary(),
            scenario.getStayExpectedRaiseRate().doubleValue(),
            scenario.getMoveTargetGrossSalary(),
            scenario.getMoveOneTimeCost(),
            scenario.getExpectedUnemploymentMonths(),
            scenario.getEligibleForUnemploymentBenefit());

    long difference = scenario.getDifference();
    SimulationResponse result =
        new SimulationResponse(
            scenario.getId(),
            scenario.getScenarioName(),
            scenario.getStayCompensation12m(),
            scenario.getMoveCompensation12m(),
            scenario.getEstimatedUnemploymentBenefit(),
            calculator.benefitNote(scenario.getEligibleForUnemploymentBenefit()),
            difference,
            ScenarioCalculator.typeOf(difference),
            ScenarioCalculator.typeLabelOf(difference),
            WARNING_NOTE,
            toFeasibilityDetail(
                new Feasibility(
                    scenario.getFeasibilityStatus(),
                    scenario.getFeasibilityPercentile(),
                    scenario.getFeasibilitySampleSize())));

    return new LatestScenarioResponse(
        scenario.getId(),
        scenario.getScenarioName(),
        inputs,
        result,
        scenario.getCalculatedAt() == null ? null : scenario.getCalculatedAt().toInstant());
  }

  /** 산출 결과. {@code percentile} 이 {@code null} 이면 표본이 모자라 매기지 못한 것이다. */
  private record Feasibility(String status, Integer percentile, Integer sampleSize) {

    static Feasibility insufficient(int disclosedSampleSize) {
      return new Feasibility(STATUS_INSUFFICIENT_SAMPLE, null, disclosedSampleSize);
    }
  }
}
