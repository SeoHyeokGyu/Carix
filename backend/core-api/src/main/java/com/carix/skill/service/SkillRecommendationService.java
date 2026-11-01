package com.carix.skill.service;

import com.carix.config.IndexThresholdProperties;
import com.carix.datasource.ReadOnlyRouting;
import com.carix.datasource.ReadOnlyRoutingContext;
import com.carix.index.enums.SkillDemandStatus;
import com.carix.index.mvi.PeerGroupFallbackResolver;
import com.carix.index.mvi.SkillDemandCalculator;
import com.carix.index.mvi.SkillDemandCalculator.SkillDemandResult;
import com.carix.jobposting.JobPosting;
import com.carix.jobposting.JobPostingSkill;
import com.carix.jobposting.JobPostingSkillRepository;
import com.carix.market.MarketAggregateRepository;
import com.carix.peergroup.PeerSalaryDistributionResolver;
import com.carix.peergroup.PeerSalaryDistributionResolver.ResolvedPeerPostings;
import com.carix.profile.domain.CareerProfile;
import com.carix.profile.repository.CareerProfileRepository;
import com.carix.skill.domain.Skill;
import com.carix.skill.domain.UserSkill;
import com.carix.skill.dto.SkillRecommendationsResponse;
import com.carix.skill.dto.SkillRecommendationsResponse.CoOccurrenceInfo;
import com.carix.skill.dto.SkillRecommendationsResponse.PeerGroupInfo;
import com.carix.skill.dto.SkillRecommendationsResponse.SkillRecommendationItem;
import com.carix.skill.repository.UserSkillRepository;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 동료집단 기반 스킬 보강 추천 서비스 (API 규격 §5.6, 기능 명세 US-D6).
 *
 * <p>사용자의 동료집단 공고에서 수요율이 높으나 아직 보유하지 않은 스킬을 추천(기본 10개, 최대 30개)하고, 사용자의 기존 보유 스킬 중 공고 표본이 가장 풍부한 기준
 * 스킬과의 동시출현율(Co-occurrence Rate)을 근거로 제시합니다.
 */
@Slf4j
@Service
@ReadOnlyRouting
@RequiredArgsConstructor
public class SkillRecommendationService {

  public static final int DEFAULT_LIMIT = 10;
  public static final int MAX_LIMIT = 30;

  /**
   * 동시출현율 조건을 충족하지 못하거나 기준 스킬이 부족할 때의 기본 안내 문구.
   *
   * <p>수요율 숫자는 넣지 않는다 — 화면이 같은 행에 「수요율 N%」 배지로 이미 보여 같은 숫자가 두 번 나왔다.
   */
  static final String DEFAULT_DEMAND_REASON = "동료집단 공고에서 자주 요구되는 핵심 스택입니다.";

  private static final double DEFAULT_MIN_CO_OCCURRENCE_RATE = 0.20;

  private static final String TREND_UP = "UP";
  private static final String TREND_DOWN = "DOWN";
  private static final String TREND_STABLE = "STABLE";

  private static final double MVI_UP_THRESHOLD = 105.0;
  private static final double MVI_DOWN_THRESHOLD = 95.0;

  private static final String UNCONFIGURED_PEER_GROUP_MESSAGE = "동료집단 미설정";

  private final CareerProfileRepository careerProfileRepository;
  private final UserSkillRepository userSkillRepository;
  private final JobPostingSkillRepository jobPostingSkillRepository;
  private final PeerSalaryDistributionResolver peerSalaryDistributionResolver;
  private final PeerGroupFallbackResolver peerGroupFallbackResolver;
  private final SkillDemandCalculator skillDemandCalculator;
  private final MarketAggregateRepository marketAggregateRepository;
  private final IndexThresholdProperties thresholds;

  /**
   * 동료집단 통계 신뢰도를 보장하기 위한 최소 공고 표본 수를 반환합니다.
   *
   * @return 최소 표본 공고 수
   */
  private int minSampleSize() {
    return thresholds != null
        ? thresholds.getPeerGroupMinSampleSize()
        : PeerGroupFallbackResolver.MIN_SAMPLE_SIZE;
  }

  /**
   * 기준 스킬과 후보 기술 간의 유의미한 연관성으로 판단하기 위한 최소 동시출현율 임계값을 반환합니다.
   *
   * @return 최소 동시출현율 임계값 (예: 0.20)
   */
  private double minCoOccurrenceRate() {
    return thresholds != null
        ? thresholds.getCoOccurrenceMinRate()
        : DEFAULT_MIN_CO_OCCURRENCE_RATE;
  }

  /**
   * 로그인 사용자의 동료집단 기반 스킬 보강 추천 목록을 조회합니다. (기본 최대 10개)
   *
   * @param userId 사용자 ID
   * @return 스킬 추천 응답 (조건 미달 시 빈 목록 반환)
   */
  @Transactional(readOnly = true)
  public SkillRecommendationsResponse getRecommendations(UUID userId) {
    return getRecommendations(userId, DEFAULT_LIMIT);
  }

  /**
   * 로그인 사용자의 동료집단 기반 스킬 보강 추천 목록을 limit 개수만큼 조회합니다.
   *
   * @param userId 사용자 ID
   * @param limit 최대 추천 개수 (기본 10, 최대 30)
   * @return 스킬 추천 응답 (조건 미달 시 빈 목록 반환)
   */
  @Transactional(readOnly = true)
  public SkillRecommendationsResponse getRecommendations(UUID userId, Integer limit) {
    int effectiveLimit = normalizeLimit(limit);
    Optional<CareerProfile> profileOpt = careerProfileRepository.findByUserId(userId);
    if (profileOpt.isEmpty()) {
      return new SkillRecommendationsResponse(
          new PeerGroupInfo(UNCONFIGURED_PEER_GROUP_MESSAGE), List.of());
    }

    CareerProfile profile = profileOpt.get();

    // 1. 사용자 스킬 및 동료집단 공고 병렬 조회
    InitialData initialData = fetchInitialData(userId, profile);
    PeerGroupInfo peerGroupInfo = new PeerGroupInfo(initialData.resolvedPeer().appliedConditions());
    List<JobPosting> peerPostings = initialData.resolvedPeer().postings();

    // 2. 동료집단 표본 수 검증 (최소 표본 미달 시 임의 스킬 채움 방지, US-D6)
    if (isPeerSampleInsufficient(peerPostings)) {
      return new SkillRecommendationsResponse(peerGroupInfo, List.of());
    }

    // 3. 사용자 보유 스킬 ID 추출 및 동료집단 공고 스킬 매핑
    Set<UUID> ownedSkillIds = extractOwnedSkillIds(initialData.userSkills());
    PeerSkillMapping peerSkillMapping = buildPeerSkillMapping(peerPostings);

    // 4. 미보유 후보 스킬 출현 빈도 집계
    Map<UUID, Integer> candidateCounts =
        aggregateCandidateCounts(
            peerPostings,
            peerSkillMapping.postingToSkillIds(),
            peerSkillMapping.skillCatalog(),
            ownedSkillIds);

    if (candidateCounts.isEmpty()) {
      return new SkillRecommendationsResponse(peerGroupInfo, List.of());
    }

    // 5. 기준 스킬(Base Skill) 도출
    BaseSkillInfo baseSkillInfo =
        resolveBaseSkill(initialData.userSkills(), peerSkillMapping.postingToSkillIds());

    // 6. 후보 스킬별 성장 추세 병렬 평가 및 다중 기준 랭킹 (b23: 수요 상승률 우선, 표본 부족 시 수요율 폴백)
    List<CandidateRanking> rankedCandidates =
        rankCandidatesWithGrowth(
            candidateCounts,
            peerSkillMapping.skillCatalog(),
            peerPostings.size(),
            profile,
            effectiveLimit);

    // 7. 동시출현율 계산 및 추천 아이템 조립
    List<SkillRecommendationItem> items =
        buildRecommendationItems(
            rankedCandidates, baseSkillInfo, peerSkillMapping.postingToSkillIds());

    return new SkillRecommendationsResponse(peerGroupInfo, items);
  }

  /**
   * 요청받은 추천 개수(limit)를 검증하고 기본값(10) 및 상한(30) 범위 내로 정규화합니다.
   *
   * @param limit 사용자가 요청한 추천 개수
   * @return 보정된 추천 개수
   */
  private int normalizeLimit(Integer limit) {
    return (limit != null && limit > 0) ? Math.min(limit, MAX_LIMIT) : DEFAULT_LIMIT;
  }

  /**
   * 동료집단 공고 표본 수가 최소 기준치에 미달하는지 여부를 확인합니다.
   *
   * @param peerPostings 동료집단 공고 목록
   * @return 표본 부족 여부
   */
  private boolean isPeerSampleInsufficient(List<JobPosting> peerPostings) {
    return peerPostings == null || peerPostings.size() < minSampleSize();
  }

  /**
   * 가상 스레드(Virtual Thread)를 활용하여 사용자의 보유 스킬과 동료집단 공고를 병렬로 조회합니다.
   *
   * @param userId 사용자 ID
   * @param profile 사용자 커리어 프로필
   * @return 사용자 스킬 및 동료집단 공고 정보
   */
  private InitialData fetchInitialData(UUID userId, CareerProfile profile) {
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      Future<List<UserSkill>> skillsFuture =
          submitReadOnly(executor, () -> userSkillRepository.findByUserId(userId));
      Future<ResolvedPeerPostings> peerFuture =
          submitReadOnly(executor, () -> peerSalaryDistributionResolver.resolvePostings(profile));

      return new InitialData(skillsFuture.get(), peerFuture.get());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("스킬 추천 기본 데이터 병렬 조회 중 인터럽트 발생", e);
    } catch (ExecutionException e) {
      throw new RuntimeException("스킬 추천 기본 데이터 병렬 조회 실패", e.getCause());
    }
  }

  /**
   * 가상 스레드 풀에서 읽기 전용 라우팅 컨텍스트를 주입하여 작업을 실행합니다.
   *
   * @param executor 가상 스레드 ExecutorService
   * @param task 실행할 Callable 태스크
   * @param <T> 반환 타입
   * @return 작업 실행 Future
   */
  private <T> Future<T> submitReadOnly(ExecutorService executor, Callable<T> task) {
    return executor.submit(ReadOnlyRoutingContext.withReadOnlyCallable(task));
  }

  /**
   * 사용자 보유 스킬 목록에서 유효한 스킬 ID 세트를 추출합니다.
   *
   * @param userSkills 사용자 보유 스킬 엔티티 목록
   * @return 사용자 보유 스킬 ID Set
   */
  private Set<UUID> extractOwnedSkillIds(List<UserSkill> userSkills) {
    return userSkills.stream()
        .map(UserSkill::getSkill)
        .filter(Objects::nonNull)
        .map(Skill::getId)
        .collect(Collectors.toSet());
  }

  /**
   * 동료집단 공고 목록에 포함된 스킬들을 조회하여 공고별 스킬 매핑 및 스킬 카탈로그 캐시를 생성합니다.
   *
   * @param peerPostings 동료집단 공고 목록
   * @return 공고별 스킬 매핑 및 스킬 카탈로그
   */
  private PeerSkillMapping buildPeerSkillMapping(List<JobPosting> peerPostings) {
    List<UUID> postingIds = peerPostings.stream().map(JobPosting::getId).toList();
    List<JobPostingSkill> jpsList = jobPostingSkillRepository.findByJobPostingIdIn(postingIds);

    Map<UUID, Set<UUID>> postingToSkillIds = new HashMap<>();
    Map<UUID, Skill> skillCatalog = new HashMap<>();
    for (JobPostingSkill jps : jpsList) {
      if (jps.getSkill() == null || jps.getJobPosting() == null) {
        continue;
      }
      postingToSkillIds
          .computeIfAbsent(jps.getJobPosting().getId(), k -> new HashSet<>())
          .add(jps.getSkill().getId());
      skillCatalog.putIfAbsent(jps.getSkill().getId(), jps.getSkill());
    }
    return new PeerSkillMapping(postingToSkillIds, skillCatalog);
  }

  /**
   * 사용자 보유 표준 스킬 중 동료집단 공고에서 가장 빈번하게 등장하는 기준 스킬(Base Skill)을 도출합니다.
   *
   * @param userSkills 사용자 보유 스킬 목록
   * @param postingToSkillIds 공고별 스킬 ID 매핑
   * @return 기준 스킬 및 공고 출현 횟수 정보
   */
  private BaseSkillInfo resolveBaseSkill(
      List<UserSkill> userSkills, Map<UUID, Set<UUID>> postingToSkillIds) {
    List<Skill> ownedStandardSkills =
        userSkills.stream()
            .map(UserSkill::getSkill)
            .filter(s -> s != null && !s.isCustom())
            .toList();

    Skill baseSkill = null;
    int maxBasePostings = 0;

    for (Skill ownedSkill : ownedStandardSkills) {
      int count = countSkillPostings(ownedSkill.getId(), postingToSkillIds);
      if (count > maxBasePostings) {
        maxBasePostings = count;
        baseSkill = ownedSkill;
      }
    }
    return new BaseSkillInfo(baseSkill, maxBasePostings);
  }

  /**
   * 동료집단 공고 중 특정 스킬을 요구하는 공고 수를 계산합니다.
   *
   * @param skillId 스킬 ID
   * @param postingToSkillIds 공고별 스킬 ID 매핑
   * @return 해당 스킬이 포함된 공고 수
   */
  private int countSkillPostings(UUID skillId, Map<UUID, Set<UUID>> postingToSkillIds) {
    int count = 0;
    for (Set<UUID> skillsInPosting : postingToSkillIds.values()) {
      if (skillsInPosting.contains(skillId)) {
        count++;
      }
    }
    return count;
  }

  /**
   * 동료집단 공고를 순회하며 사용자가 미보유한 표준 스킬의 출현 횟수를 집계합니다.
   *
   * @param peerPostings 동료집단 공고 목록
   * @param postingToSkillIds 공고별 스킬 ID 매핑
   * @param skillCatalog 스킬 메타데이터 캐시
   * @param ownedSkillIds 사용자 보유 스킬 ID 세트
   * @return 미보유 표준 스킬 ID별 출현 공고 수 매핑
   */
  private Map<UUID, Integer> aggregateCandidateCounts(
      List<JobPosting> peerPostings,
      Map<UUID, Set<UUID>> postingToSkillIds,
      Map<UUID, Skill> skillCatalog,
      Set<UUID> ownedSkillIds) {
    Map<UUID, Integer> candidateCounts = new HashMap<>();

    for (JobPosting posting : peerPostings) {
      Set<UUID> skillsInPosting = postingToSkillIds.getOrDefault(posting.getId(), Set.of());
      for (UUID sId : skillsInPosting) {
        if (ownedSkillIds.contains(sId)) {
          continue; // 이미 보유한 기술은 추천 후보에서 제외
        }
        Skill skill = skillCatalog.get(sId);
        if (skill == null || skill.isCustom()) {
          continue; // 커스텀 스킬은 동료집단 공통 추천 대상에서 제외
        }
        candidateCounts.put(sId, candidateCounts.getOrDefault(sId, 0) + 1);
      }
    }
    return candidateCounts;
  }

  /**
   * 미보유 후보 스킬들의 성장 추세를 병렬로 평가하고, b23 수요 상승률 기준 다중 우선순위로 랭킹을 산출합니다.
   *
   * <p>정렬 우선순위: 1. Tier 1 (상승 스킬): OK 상태 & 수요 지수 > 100.0 (지수 내림차순 -> 수요율 내림차순 -> 공고수 내림차순 -> 표준명
   * 오름차순) 2. Tier 2 (표본 부족 폴백): INSUFFICIENT_SAMPLE / NEW_OBSERVATION 등 (수요율 내림차순 -> 공고수 -> 표준명) 3.
   * Tier 3 (보합/하락 스킬): OK 상태 & 수요 지수 <= 100.0 또는 NO_RECENT_DEMAND (지수 내림차순 -> 수요율 -> 공고수 -> 표준명)
   *
   * @param candidateCounts 후보 스킬별 공고 수
   * @param skillCatalog 스킬 캐시
   * @param totalPeerCount 동료집단 총 공고 수
   * @param profile 사용자 커리어 프로필
   * @param effectiveLimit 최대 반환 개수
   * @return 다중 기준 정렬 및 슬라이싱된 최종 후보 스킬 목록
   */
  private List<CandidateRanking> rankCandidatesWithGrowth(
      Map<UUID, Integer> candidateCounts,
      Map<UUID, Skill> skillCatalog,
      int totalPeerCount,
      CareerProfile profile,
      int effectiveLimit) {
    String baseYearMonth = resolveBaseYearMonth();
    PeerGroupFallbackResolver.ResolvedPeerGroup resolvedPeerGroup =
        peerGroupFallbackResolver.resolve("ALL", baseYearMonth, profile);

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Map.Entry<CandidatePartial, Future<EvaluatedGrowth>>> futures =
          new ArrayList<>(candidateCounts.size());

      for (Map.Entry<UUID, Integer> entry : candidateCounts.entrySet()) {
        UUID sId = entry.getKey();
        int count = entry.getValue();
        double roundedDemandRate = roundToThreeDecimals((double) count / totalPeerCount);
        CandidatePartial partial =
            new CandidatePartial(skillCatalog.get(sId), count, roundedDemandRate);

        futures.add(
            Map.entry(
                partial,
                submitReadOnly(
                    executor, () -> evaluateGrowth(sId, baseYearMonth, resolvedPeerGroup))));
      }

      List<CandidateRanking> evaluated = new ArrayList<>(futures.size());
      for (var entry : futures) {
        CandidatePartial p = entry.getKey();
        EvaluatedGrowth g = entry.getValue().get();
        evaluated.add(
            new CandidateRanking(
                p.skill(), p.postingCount(), p.demandRate(), g.demandResult(), g.growthTrend()));
      }

      return evaluated.stream().sorted(CANDIDATE_COMPARATOR).limit(effectiveLimit).toList();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("후보 스킬 트렌드 병렬 계산 중 인터럽트 발생", e);
    } catch (ExecutionException e) {
      throw new RuntimeException("후보 스킬 트렌드 병렬 계산 실패", e.getCause());
    }
  }

  /**
   * 단일 스킬의 월별 시장 수요 지수(MVI)를 계산하고 성장 트렌드를 평가합니다.
   *
   * @param skillId 스킬 ID
   * @param baseYearMonth 기준 연월 (yyyyMM)
   * @param resolvedPeerGroup 동료집단 세그먼트
   * @return 계산된 성장 지수 결과 및 트렌드 문자열
   */
  private EvaluatedGrowth evaluateGrowth(
      UUID skillId,
      String baseYearMonth,
      PeerGroupFallbackResolver.ResolvedPeerGroup resolvedPeerGroup) {
    try {
      SkillDemandResult demandResult =
          skillDemandCalculator.calculate(skillId, baseYearMonth, resolvedPeerGroup);
      if (demandResult == null) {
        return new EvaluatedGrowth(null, TREND_STABLE);
      }

      String trend =
          switch (demandResult.getStatus()) {
            case OK -> resolveOkDemandTrend(demandResult.getDemandIndex());
            case NEW_OBSERVATION -> TREND_UP;
            case NO_RECENT_DEMAND -> TREND_DOWN;
            default -> TREND_STABLE;
          };
      return new EvaluatedGrowth(demandResult, trend);
    } catch (Exception e) {
      log.debug("스킬({}) 성장 트렌드 계산 중 예외 발생, 기본값 STABLE 사용: {}", skillId, e.getMessage());
      return new EvaluatedGrowth(null, TREND_STABLE);
    }
  }

  /**
   * 기준 스킬과의 동시출현율(Co-occurrence Rate)을 계산하고 최종 스킬 추천 항목 목록을 조립합니다.
   *
   * @param rankedCandidates 랭킹이 적용된 후보 스킬 목록
   * @param baseSkillInfo 기준 스킬 정보
   * @param postingToSkillIds 공고별 스킬 ID 매핑
   * @return 추천 스킬 항목 목록
   */
  private List<SkillRecommendationItem> buildRecommendationItems(
      List<CandidateRanking> rankedCandidates,
      BaseSkillInfo baseSkillInfo,
      Map<UUID, Set<UUID>> postingToSkillIds) {
    List<SkillRecommendationItem> items = new ArrayList<>();

    for (CandidateRanking candidate : rankedCandidates) {
      Skill candSkill = candidate.skill();
      RecommendationReason reason =
          resolveRecommendationReason(candidate, baseSkillInfo, postingToSkillIds);

      items.add(
          new SkillRecommendationItem(
              candSkill.getStandardName(),
              candSkill.getCategory(),
              candidate.demandRate(),
              candidate.growthTrend(),
              reason.coOccurrence(),
              reason.reason()));
    }
    return items;
  }

  /**
   * 후보 스킬의 동시출현율 또는 수요율을 기반으로 추천 사유를 도출합니다.
   *
   * @param candidate 후보 스킬 랭킹 정보
   * @param baseSkillInfo 기준 스킬 정보
   * @param postingToSkillIds 공고별 스킬 ID 매핑
   * @return 동시출현 정보 및 추천 사유
   */
  private RecommendationReason resolveRecommendationReason(
      CandidateRanking candidate,
      BaseSkillInfo baseSkillInfo,
      Map<UUID, Set<UUID>> postingToSkillIds) {
    // 기준 스킬이 없거나 표본 수가 부족한 경우 기본 수요율 안내 문구 적용
    if (!baseSkillInfo.isSufficient(minSampleSize())) {
      return new RecommendationReason(null, DEFAULT_DEMAND_REASON);
    }

    UUID baseId = baseSkillInfo.skill().getId();
    UUID candId = candidate.skill().getId();
    int bothCount = countCoOccurrences(baseId, candId, postingToSkillIds);

    // 기준 스킬 공고 대비 동시 출현율 산출 (소수점 2자리 반올림)
    double rawRate = (double) bothCount / baseSkillInfo.postingCount();
    double roundedRate = roundToTwoDecimals(rawRate);

    // 동시출현율이 임계값 미만인 경우 전체 공고 수요율 문구로 대체 (US-D6)
    if (roundedRate < minCoOccurrenceRate()) {
      return new RecommendationReason(null, DEFAULT_DEMAND_REASON);
    }

    CoOccurrenceInfo coOccurrence =
        new CoOccurrenceInfo(baseSkillInfo.skill().getStandardName(), roundedRate);
    int percent = (int) Math.round(roundedRate * 100);
    String reason =
        String.format(
            "보유하신 %s 공고의 %d%%가 함께 요구합니다.", baseSkillInfo.skill().getStandardName(), percent);
    return new RecommendationReason(coOccurrence, reason);
  }

  /**
   * 동료집단 공고 중 기준 스킬과 후보 스킬이 동시에 요구된 공고 수를 계산합니다.
   *
   * @param baseId 기준 스킬 ID
   * @param candId 후보 스킬 ID
   * @param postingToSkillIds 공고별 스킬 ID 매핑
   * @return 두 스킬이 동시에 포함된 공고 수
   */
  private int countCoOccurrences(UUID baseId, UUID candId, Map<UUID, Set<UUID>> postingToSkillIds) {
    int bothCount = 0;
    for (Set<UUID> sIds : postingToSkillIds.values()) {
      if (sIds.contains(baseId) && sIds.contains(candId)) {
        bothCount++;
      }
    }
    return bothCount;
  }

  /**
   * 부동소수점 숫자를 소수점 셋째 자리까지 반올림합니다.
   *
   * @param value 원본 값
   * @return 소수점 셋째 자리까지 반올림된 값
   */
  private double roundToThreeDecimals(double value) {
    return Math.round(value * 1000.0) / 1000.0;
  }

  /**
   * 부동소수점 숫자를 소수점 둘째 자리까지 반올림합니다.
   *
   * @param value 원본 값
   * @return 소수점 둘째 자리까지 반올림된 값
   */
  private double roundToTwoDecimals(double value) {
    return Math.round(value * 100.0) / 100.0;
  }

  /**
   * b23 수요 상승률 기반 다중 기준 랭킹 비교자.
   *
   * <p>1. Tier 1 (상승 스킬, OK 상태 & 지수 > 100.0): 지수 내림차순 -> 수요율 내림차순 -> 공고수 내림차순 -> 표준명 오름차순 2. Tier 2
   * (표본 부족 폴백): 수요율 내림차순 -> 공고수 내림차순 -> 표준명 오름차순 3. Tier 3 (보합/하락 스킬): 지수 내림차순 -> 수요율 내림차순 -> 공고수
   * 내림차순 -> 표준명 오름차순
   */
  private static final Comparator<CandidateRanking> CANDIDATE_COMPARATOR =
      (c1, c2) -> {
        int t1 = c1.tier();
        int t2 = c2.tier();
        if (t1 != t2) {
          return Integer.compare(t1, t2);
        }
        if (t1 == 1) {
          int cmp = Double.compare(c2.effectiveDemandIndex(), c1.effectiveDemandIndex());
          if (cmp != 0) return cmp;
          cmp = Double.compare(c2.demandRate(), c1.demandRate());
          if (cmp != 0) return cmp;
          cmp = Integer.compare(c2.postingCount(), c1.postingCount());
          if (cmp != 0) return cmp;
          return c1.skill().getStandardName().compareTo(c2.skill().getStandardName());
        } else if (t1 == 2) {
          int cmp = Double.compare(c2.demandRate(), c1.demandRate());
          if (cmp != 0) return cmp;
          cmp = Integer.compare(c2.postingCount(), c1.postingCount());
          if (cmp != 0) return cmp;
          return c1.skill().getStandardName().compareTo(c2.skill().getStandardName());
        } else {
          int cmp = Double.compare(c2.effectiveDemandIndex(), c1.effectiveDemandIndex());
          if (cmp != 0) return cmp;
          cmp = Double.compare(c2.demandRate(), c1.demandRate());
          if (cmp != 0) return cmp;
          cmp = Integer.compare(c2.postingCount(), c1.postingCount());
          if (cmp != 0) return cmp;
          return c1.skill().getStandardName().compareTo(c2.skill().getStandardName());
        }
      };

  /**
   * 정상(OK) 상태인 MVI 수요 지수 값에 따라 성장 추세(UP, DOWN, STABLE)를 판정합니다.
   *
   * @param demandIndex MVI 수요 지수
   * @return 성장 추세 문자열
   */
  private String resolveOkDemandTrend(double demandIndex) {
    if (demandIndex > MVI_UP_THRESHOLD) {
      return TREND_UP;
    }
    if (demandIndex < MVI_DOWN_THRESHOLD) {
      return TREND_DOWN;
    }
    return TREND_STABLE;
  }

  /**
   * 시장 통계 집계 데이터의 최신 기준 연월(yyyyMM)을 조회합니다.
   *
   * @return 기준 연월 문자열
   */
  private String resolveBaseYearMonth() {
    String currentYm = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
    // 원천 조건 없는 기준월은 통합(ALL) 행이 있는 최신 월로 잡는다
    return marketAggregateRepository
        .findLatestAvailableYearMonthForSource("ALL", currentYm)
        .orElse(currentYm);
  }

  /**
   * 가상 스레드 병렬 조회로 로드된 사용자 보유 스킬 목록과 동료집단 공고 정보 묶음.
   *
   * @param userSkills 사용자 보유 스킬 목록
   * @param resolvedPeer 동료집단 공고 및 적용 조건 정보
   */
  private record InitialData(List<UserSkill> userSkills, ResolvedPeerPostings resolvedPeer) {}

  /**
   * 동료집단 공고별 매핑된 스킬 ID 세트와 스킬 엔티티 카탈로그 캐시.
   *
   * @param postingToSkillIds 공고 ID별 매핑된 스킬 ID 집합
   * @param skillCatalog 스킬 ID별 스킬 엔티티 매핑
   */
  private record PeerSkillMapping(
      Map<UUID, Set<UUID>> postingToSkillIds, Map<UUID, Skill> skillCatalog) {}

  /**
   * 사용자 보유 스킬 중 동료집단 공고 수가 가장 많은 기준 스킬(Base Skill) 정보.
   *
   * @param skill 기준 스킬 엔티티
   * @param postingCount 동료집단 내 해당 스킬이 요구된 공고 수
   */
  private record BaseSkillInfo(Skill skill, int postingCount) {
    /**
     * 기준 스킬이 유효하고 동료집단 공고 표본이 최소 기준치 이상인지 확인합니다.
     *
     * @param minSample 최소 표본 공고 수
     * @return 표본 충족 여부
     */
    boolean isSufficient(int minSample) {
      return skill != null && postingCount >= minSample;
    }
  }

  /**
   * 동료집단 공고 분석 후 성장 지표가 평가된 미보유 후보 스킬 정보.
   *
   * @param skill 후보 스킬 엔티티
   * @param postingCount 해당 스킬이 요구된 동료집단 공고 수
   * @param demandRate 동료집단 전체 대비 수요율 (0.000 ~ 1.000)
   * @param demandResult 3개월 대 12개월 수요 지수 결과
   * @param growthTrend 성장 추세 (UP / DOWN / STABLE)
   */
  private record CandidateRanking(
      Skill skill,
      int postingCount,
      double demandRate,
      SkillDemandResult demandResult,
      String growthTrend) {

    int tier() {
      if (demandResult != null
          && demandResult.getStatus() == SkillDemandStatus.OK
          && demandResult.getDemandIndex() > 100.0) {
        return 1; // 최근 3개월 대 이전 12개월 수요 상승 스킬
      }
      if (demandResult == null
          || demandResult.getStatus() == SkillDemandStatus.INSUFFICIENT_SAMPLE
          || demandResult.getStatus() == SkillDemandStatus.NEW_OBSERVATION
          || demandResult.getStatus() == SkillDemandStatus.NO_RECENT_COVERAGE) {
        return 2; // 표본 부족 / 15개월 수집 이력 미달 / 신규 관측 폴백 (수요율 기준 정렬)
      }
      return 3; // 보합 또는 하락 스킬 (demandIndex <= 100.0 또는 NO_RECENT_DEMAND)
    }

    double effectiveDemandIndex() {
      if (demandResult != null && demandResult.getStatus() == SkillDemandStatus.OK) {
        return demandResult.getDemandIndex();
      }
      return 0.0;
    }
  }

  /** 성장 추세 평가 전 후보 스킬의 초기 집계 정보. */
  private record CandidatePartial(Skill skill, int postingCount, double demandRate) {}

  /** 후보 스킬의 성장 지표 평가 결과. */
  private record EvaluatedGrowth(SkillDemandResult demandResult, String growthTrend) {}

  /**
   * 추천 사유 생성 결과 (동시출현 정보 및 사유 안내 문구).
   *
   * @param coOccurrence 동시출현율 정보 (기준 미달 시 null)
   * @param reason 사용자 안내 문구
   */
  private record RecommendationReason(CoOccurrenceInfo coOccurrence, String reason) {}
}
