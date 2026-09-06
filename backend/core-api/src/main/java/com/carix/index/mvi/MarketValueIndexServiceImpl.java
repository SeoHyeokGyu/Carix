package com.carix.index.mvi;

import com.carix.index.MarketValueIndexProvider;
import com.carix.index.dto.ExcludedSkill;
import com.carix.index.dto.MviResult;
import com.carix.index.dto.SkillContribution;
import com.carix.index.dto.SourceCoverage;
import com.carix.index.dto.WageBaseline;
import com.carix.index.enums.IndexStatus;
import com.carix.index.enums.SkillDemandStatus;
import com.carix.index.enums.SkillExclusionReason;
import com.carix.profile.domain.CareerProfile;
import com.carix.profile.repository.CareerProfileRepository;
import com.carix.skill.domain.Skill;
import com.carix.skill.domain.UserSkill;
import com.carix.skill.repository.UserSkillRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시장가치 지수(MVI, Market Value Index) 산출 엔진 구현체.
 *
 * <p>사용자의 커리어 프로필과 보유 스킬을 바탕으로 플랫폼 원천별 채용공고 동료집단 완화 집계 데이터를 조회하고, 각 스킬의 수요 지수와 개인별 숙련도 가중치를 결합하여
 * 개인화된 시장가치 지수를 도출합니다.
 *
 * <h3>계산 절차 요약</h3>
 *
 * <ol>
 *   <li>사용자 프로필 및 보유 스킬({@code core.user_skill}) 조회
 *   <li>원천별({@code JOBKOREA}, {@code SARAMIN}) 5단계 동료집단 완화 알고리즘 수행
 *   <li>보유 스킬별 수요 지수 산출 및 제외 스킬({@code NEW_OBSERVATION}, {@code CUSTOM_SKILL_NO_STATS}) 분류
 *   <li>유효 스킬에 대해 숙련도/최근성 기반 가중치(\(W_{skill}\)) 100% 재정규화
 *   <li>원천별 MVI 가중합 계산: \( \text{MVI}_{src} = \sum W_i \times \text{SkillDemandIndex}_i(src) \)
 *   <li>원천 간 표본수(\(N\)) 가중 결합: \( \text{MVI} = \frac{\sum N_{src} \cdot \text{MVI}_{src}}{\sum
 *       N_{src}} \)
 *   <li>공식 임금통계 기준선({@code WageBaseline}) 첨부 후 {@link MviResult} 반환
 * </ol>
 *
 * <p>Virtual Threads(가상 스레드)와 동시 DB I/O 제한(Semaphore)을 적용하여 원천별 완화, 임금 기준선 사전 조회, 스킬별 수요 지수 계산을 고성능
 * 병렬로 처리합니다.
 */
@Slf4j
@Service
@Primary
@RequiredArgsConstructor
public class MarketValueIndexServiceImpl implements MarketValueIndexProvider {

  /** 지원하는 채용공고 원천 플랫폼 목록 */
  public static final List<String> SUPPORTED_SOURCES = List.of("JOBKOREA", "SARAMIN");

  /**
   * DB 커넥션 풀 고갈(Connection Starvation) 방지를 위한 동시 DB I/O 제한 허가 수.
   *
   * <p>가상 스레드(Virtual Threads)는 수백~수천 개를 경량으로 동시 생성할 수 있지만, 물리적인 RDBMS 커넥션 풀(HikariCP, 통상 10~30개)의
   * 크기는 제한되어 있습니다. 단일 사용자의 MVI 계산 요청에서 수십 개의 가상 스레드가 동시에 커넥션을 점유하려 들면 다른 웹 요청 처리를 위한 커넥션이 고갈되거나
   * {@code ConnectionTimeoutException}이 발생할 수 있습니다.
   *
   * <p>따라서 단일 MVI 엔진 인스턴스 내에서 동시에 DB I/O를 수행할 수 있는 최대 동시 작업 수를 8개로 제한하여 안정적인 커넥션 획득과 빠른 반납을 유도합니다.
   */
  private static final int DEFAULT_MAX_CONCURRENT_DB_TASKS = 8;

  /**
   * MVI 비동기 병렬 계산 시 DB 커넥션을 점유하는 가상 스레드의 동시성을 제어하는 세마포어.
   *
   * <p>허가증(Permit)을 획득하지 못한 가상 스레드는 대기하는 동안 캐리어 스레드(OS 스레드)에서 마운트 해제(unmount)되어 대기하므로, 시스템 리소스를
   * 낭비하지 않으면서도 DB 커넥션 풀을 안전하게 보호합니다.
   */
  private final Semaphore dbConcurrencyLimiter = new Semaphore(DEFAULT_MAX_CONCURRENT_DB_TASKS);

  private final CareerProfileRepository careerProfileRepository;
  private final UserSkillRepository userSkillRepository;
  private final PeerGroupFallbackResolver peerGroupFallbackResolver;
  private final SkillDemandCalculator skillDemandCalculator;
  private final SkillWeightCalculator skillWeightCalculator;
  private final WageBaselineResolver wageBaselineResolver;

  /**
   * 세마포어({@link #dbConcurrencyLimiter}) 허가증을 획득하고 작업을 실행한 후 반드시 반납하도록 보장하는 함수형 헬퍼 메서드.
   *
   * <p>가상 스레드 실행 블록 내에서 DB I/O 호출을 감싸는 데 사용되며, 예외 발생 여부와 무관하게 {@code finally} 블록에서 허가증을 즉시 반환하여 커넥션
   * 및 허가증 누수를 방지합니다.
   *
   * @param task 실행할 DB I/O 작업 (Callable)
   * @param <T> 작업 결과 반환 타입
   * @return 세마포어 허가증 획득/반납이 보장된 래핑된 작업
   */
  private <T> Callable<T> withDbPermit(Callable<T> task) {
    return () -> {
      dbConcurrencyLimiter.acquire();
      try {
        return task.call();
      } finally {
        dbConcurrencyLimiter.release();
      }
    };
  }

  // =========================================================================
  // 1. MVI 산출 (메인 계산 파이프라인)
  // =========================================================================

  /**
   * 사용자의 커리어 프로필과 보유 스킬을 바탕으로 개인화된 시장가치 지수(MVI)를 산출합니다.
   *
   * <h3>메서드 실행 파이프라인 구성</h3>
   *
   * <p>본 메서드는 다음과 같은 6단계의 파이프라인을 거쳐 MVI를 도출합니다:
   *
   * <ol>
   *   <li><b>사용자 데이터 로드 ({@link #loadUserData}):</b> 사용자 식별자 유효성을 검증하고, 프로필과 보유 스킬을 가상 스레드에서 병렬로
   *       조회합니다. 보유 스킬이 없는 경우 조기 종료({@link IndexStatus#INSUFFICIENT_SAMPLE})합니다.
   *   <li><b>동료집단 완화 및 표본수 검증 ({@link #resolvePeerGroups}):</b> 원천 플랫폼({@code JOBKOREA}, {@code
   *       SARAMIN})별 5단계 동료집단 완화 알고리즘을 병렬 수행하고, 모든 원천의 총 표본수(\(N\))가 최소 기준치({@value
   *       PeerGroupFallbackResolver#MIN_SAMPLE_SIZE})에 미달하는지 검증합니다.
   *   <li><b>스킬 수요 지수 병렬 산출 ({@link #fetchSkillDemandResults}):</b> 원천별 전체 공고 모수({@code
   *       TotalPostings})를 1회 선조회한 후, 표준 스킬에 대해 (원천 × 스킬) 조합의 수요 지수를 가상 스레드 풀에서 병렬 계산합니다.
   *   <li><b>스킬 상태 분류 및 도메인 규칙 적용 ({@link #classifySkills}):</b> 커스텀 스킬({@code
   *       CUSTOM_SKILL_NO_STATS}), 신규 관측({@code NEW_OBSERVATION}), 표본 미달({@code
   *       INSUFFICIENT_SAMPLE}), 최근 수요 없음({@code NO_RECENT_DEMAND})을 분류합니다. 다중 원천 교차 검증을 통해 한 원천이라도
   *       유효한 스킬은 제외하지 않고 유지합니다.
   *   <li><b>가중치 정규화 및 최종 결합 ({@link #calculateCombinedMvi}):</b> 제외되지 않은 유효 스킬들에 대해 숙련도/최근성 기반
   *       100% 가중치 재정규화를 수행한 뒤, 원천 간 표본수(\(N_{src}\))를 가중치로 결합하여 최종 단일 MVI 지수를 산출합니다.
   *   <li><b>결과 조립 ({@link #buildFinalMviResult}):</b> 사전 조회된 공식 임금통계 기준선({@link WageBaseline})과
   *       원천별 커버리지, 스킬 통계를 결합하여 {@link MviResult}를 반환합니다.
   * </ol>
   *
   * @param userId 대상 사용자 식별자 (null인 경우 {@link MviResult#notAvailable} 반환)
   * @param baseYearMonth 기준년월 (YYYYMM 6자리 형식)
   * @return 산출된 시장가치 지수 결과 객체 (계산 불가 시 사유 및 상태코드 포함)
   * @throws RuntimeException 가상 스레드 병렬 실행 중 I/O 실패 또는 인터럽트 발생 시
   */
  @Override
  @Transactional(readOnly = true)
  public MviResult calculate(UUID userId, String baseYearMonth) {
    if (userId == null) {
      return MviResult.notAvailable(baseYearMonth);
    }

    // 1. 사용자 프로필 및 보유 스킬 병렬 조회
    UserData userData = loadUserData(userId, "사용자 데이터 로드 실패");
    if (!userData.hasSkills()) {
      log.debug("User {} has no registered skills. Returning INSUFFICIENT_SAMPLE", userId);
      return buildEmptySkillsResult(baseYearMonth);
    }

    CareerProfile profile = userData.profileOrDefault(userId);

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      // 2. 임금 기준선(Wage Baseline) 백그라운드 사전 조회(Prefetch)
      Future<WageBaseline> baselineFuture = prefetchWageBaseline(executor, profile);

      // 3. 원천별 동료집단 완화 병렬 수행
      PeerGroupResolution peerGroupResolution = resolvePeerGroups(executor, profile, baseYearMonth);

      // 모든 원천에서 표본이 부족한 경우 (N < 10)
      if (!peerGroupResolution.hasSufficientSample()) {
        log.debug("Insufficient sample count across all sources for user {}", userId);
        return buildInsufficientSampleResult(
            baseYearMonth, peerGroupResolution.coverages(), baselineFuture.get());
      }

      // 4. 원천별 전체 공고 수(모수) 병렬 조회 (중복 쿼리 제거)
      Map<String, SkillDemandCalculator.TotalPostings> totalPostingsMap =
          fetchTotalPostings(executor, peerGroupResolution.peerGroups(), baseYearMonth);

      // 5. 스킬 수요 지수 병렬 계산 및 상태 분류
      Map<UUID, Map<String, SkillDemandCalculator.SkillDemandResult>> demandResults =
          fetchSkillDemandResults(
              executor,
              userData.skills(),
              peerGroupResolution.peerGroups(),
              totalPostingsMap,
              baseYearMonth);

      SkillClassification classification = classifySkills(userData.skills(), demandResults);

      // 6. 유효 스킬들에 대해 숙련도 가중치 재정규화 (합 = 1.0)
      Map<UUID, BigDecimal> normalizedWeights =
          skillWeightCalculator.calculateWeights(
              userData.skills(), baseYearMonth, classification.excludedSkillIds());

      WageBaseline wageBaseline = baselineFuture.get();

      if (normalizedWeights.isEmpty()) {
        log.debug("No valid skills remaining after exclusion for user {}", userId);
        return buildNoValidSkillsResult(
            baseYearMonth, peerGroupResolution.coverages(), classification, wageBaseline);
      }

      // 7. 원천별 MVI 산출 및 표본수 가중 결합
      BigDecimal finalMviValue =
          calculateCombinedMvi(
              normalizedWeights,
              peerGroupResolution.peerGroups(),
              classification.sourceSkillDemandIndices());

      List<SkillContribution> skillContributions =
          buildSkillContributions(
              userData.skills(),
              normalizedWeights,
              classification.sourceSkillDemandIndices(),
              peerGroupResolution.peerGroups());

      return buildFinalMviResult(
          baseYearMonth,
          finalMviValue,
          peerGroupResolution.coverages(),
          classification,
          skillContributions,
          wageBaseline);

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("MVI 산출 중 인터럽트 발생", e);
    } catch (ExecutionException e) {
      throw new RuntimeException("MVI 병렬 연산 실패", e.getCause());
    }
  }

  // =========================================================================
  // 2. 대시보드 스킬별 상세 수요 조회
  // =========================================================================

  /**
   * 사용자의 스킬별 상세 수요 정보 및 가중치 목록을 계산합니다 (대시보드 S-14 연동용).
   *
   * <h3>메서드 실행 파이프라인 구성</h3>
   *
   * <ol>
   *   <li><b>사용자 데이터 로드 ({@link #loadUserData}):</b> 프로필과 보유 스킬을 병렬 로드합니다.
   *   <li><b>동료집단 완화 ({@link #resolvePeerGroups}):</b> 원천별 동료집단 완화 및 커버리지를 산출합니다.
   *   <li><b>원천별 모수 및 스킬 수요 병렬 조회 ({@link #fetchTotalPostings}, {@link
   *       #fetchSkillDemandResults}):</b> 전체 모수와 스킬별 상세 수요 지표를 병렬 조회합니다.
   *   <li><b>대시보드 상세 DTO 조립 ({@link #buildSkillDemandDetails}):</b> 원천별 표본수 가중치를 적용한 종합 지수, 최근 수요율,
   *       기준선 수요율을 조합하여 반환합니다.
   * </ol>
   *
   * @param userId 사용자 식별자
   * @param baseYearMonth 기준년월 (YYYYMM)
   * @return 스킬별 수요 계산 상세 목록
   */
  @Transactional(readOnly = true)
  public List<SkillDemandDetailData> getSkillDemandDetails(UUID userId, String baseYearMonth) {
    if (userId == null) {
      return Collections.emptyList();
    }

    UserData userData = loadUserData(userId, "스킬 상세 수요 데이터 로드 실패");
    if (!userData.hasSkills()) {
      return Collections.emptyList();
    }

    CareerProfile profile = userData.profileOrDefault(userId);

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      // 1. 원천별 완화 수행 (가상 스레드 병렬)
      PeerGroupResolution peerGroupResolution = resolvePeerGroups(executor, profile, baseYearMonth);

      // 2. 제외 스킬(커스텀 스킬) 식별 및 가중치 계산
      Set<UUID> customSkillIds = identifyCustomSkillIds(userData.skills());
      Map<UUID, BigDecimal> weights =
          skillWeightCalculator.calculateWeights(userData.skills(), baseYearMonth, customSkillIds);

      // 3. 원천별 전체 모수 1회 병렬 조회
      Map<String, SkillDemandCalculator.TotalPostings> totalPostingsMap =
          fetchTotalPostings(executor, peerGroupResolution.peerGroups(), baseYearMonth);

      // 4. 스킬별 수요 통계 병렬 조회 (공통 메서드 재사용)
      Map<UUID, Map<String, SkillDemandCalculator.SkillDemandResult>> demandResults =
          fetchSkillDemandResults(
              executor,
              userData.skills(),
              peerGroupResolution.peerGroups(),
              totalPostingsMap,
              baseYearMonth);

      // 5. 결과 리스트 구성 (순서 보장 userSkills 기준)
      return buildSkillDemandDetails(
          userData.skills(), customSkillIds, weights, peerGroupResolution, demandResults);

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("스킬 상세 수요 집계 중 인터럽트 발생", e);
    } catch (ExecutionException e) {
      throw new RuntimeException("스킬 상세 수요 병렬 연산 실패", e.getCause());
    }
  }

  // =========================================================================
  // 3. 비동기 데이터 파이프라인 헬퍼 메서드
  // =========================================================================

  /**
   * 사용자 프로필과 보유 스킬을 가상 스레드에서 병렬로 조회합니다.
   *
   * @param userId 사용자 식별자
   * @param errorMessage 조회 실패 시 래핑할 예외 메시지
   * @return 사용자 프로필 및 보유 스킬 래퍼 객체
   */
  private UserData loadUserData(UUID userId, String errorMessage) {
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      Future<Optional<CareerProfile>> profileFuture =
          executor.submit(withDbPermit(() -> careerProfileRepository.findByUserId(userId)));
      Future<List<UserSkill>> skillsFuture =
          executor.submit(withDbPermit(() -> userSkillRepository.findByUserId(userId)));

      CareerProfile profile = profileFuture.get().orElse(null);
      List<UserSkill> userSkills = skillsFuture.get();
      return new UserData(profile, userSkills);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("MVI 산출 중 인터럽트 발생", e);
    } catch (ExecutionException e) {
      throw new RuntimeException(errorMessage, e.getCause());
    }
  }

  /**
   * 임금 기준선(Wage Baseline) 조회를 백그라운드 가상 스레드에서 사전 실행(Prefetch)합니다.
   *
   * @param executor 가상 스레드 실행자
   * @param profile 사용자 커리어 프로필
   * @return 임금 기준선 Future
   */
  private Future<WageBaseline> prefetchWageBaseline(
      ExecutorService executor, CareerProfile profile) {
    return executor.submit(withDbPermit(() -> wageBaselineResolver.resolveBaseline(profile)));
  }

  /**
   * 지원하는 모든 채용공고 원천에 대해 동료집단 완화 알고리즘을 가상 스레드에서 병렬 수행합니다.
   *
   * @param executor 가상 스레드 실행자
   * @param profile 사용자 커리어 프로필
   * @param baseYearMonth 기준년월
   * @return 원천별 동료집단 완화 결과 및 총 표본수 집계 객체
   * @throws ExecutionException 비동기 작업 실패 시
   * @throws InterruptedException 대기 중 인터럽트 발생 시
   */
  private PeerGroupResolution resolvePeerGroups(
      ExecutorService executor, CareerProfile profile, String baseYearMonth)
      throws ExecutionException, InterruptedException {

    Map<String, Future<PeerGroupFallbackResolver.ResolvedPeerGroup>> peerGroupFutures =
        new LinkedHashMap<>();
    for (String source : SUPPORTED_SOURCES) {
      peerGroupFutures.put(
          source,
          executor.submit(
              withDbPermit(
                  () ->
                      peerGroupFallbackResolver.resolveForSource(source, profile, baseYearMonth))));
    }

    List<SourceCoverage> coverages = new ArrayList<>();
    Map<String, PeerGroupFallbackResolver.ResolvedPeerGroup> resolvedPeerGroups = new HashMap<>();
    int totalOverallSamples = 0;

    for (String source : SUPPORTED_SOURCES) {
      PeerGroupFallbackResolver.ResolvedPeerGroup resolved = peerGroupFutures.get(source).get();
      resolvedPeerGroups.put(source, resolved);
      coverages.add(resolved.getCoverage());
      if (resolved.isSuccessful()) {
        totalOverallSamples += resolved.getSampleCount();
      }
    }

    return new PeerGroupResolution(resolvedPeerGroups, coverages, totalOverallSamples);
  }

  /**
   * 원천별 전체 공고 수(모수)를 스킬 루프 전에 1회만 병렬 조회합니다 (중복 쿼리 제거 최적화).
   *
   * @param executor 가상 스레드 실행자
   * @param resolvedPeerGroups 원천별 동료집단 완화 결과
   * @param baseYearMonth 기준년월
   * @return 원천별 전체 공고 수 매핑
   * @throws ExecutionException 비동기 작업 실패 시
   * @throws InterruptedException 대기 중 인터럽트 발생 시
   */
  private Map<String, SkillDemandCalculator.TotalPostings> fetchTotalPostings(
      ExecutorService executor,
      Map<String, PeerGroupFallbackResolver.ResolvedPeerGroup> resolvedPeerGroups,
      String baseYearMonth)
      throws ExecutionException, InterruptedException {

    Map<String, Future<SkillDemandCalculator.TotalPostings>> totalPostingsFutures = new HashMap<>();
    for (String source : SUPPORTED_SOURCES) {
      PeerGroupFallbackResolver.ResolvedPeerGroup peerGroup = resolvedPeerGroups.get(source);
      totalPostingsFutures.put(
          source,
          executor.submit(
              withDbPermit(
                  () ->
                      skillDemandCalculator.calculateTotalPostings(
                          source, peerGroup, baseYearMonth))));
    }
    Map<String, SkillDemandCalculator.TotalPostings> totalPostingsMap = new HashMap<>();
    for (String source : SUPPORTED_SOURCES) {
      totalPostingsMap.put(source, totalPostingsFutures.get(source).get());
    }
    return totalPostingsMap;
  }

  /**
   * 표준 스킬에 대해 (원천 × 스킬) 가상 스레드를 생성하여 스킬 수요 지수를 병렬로 계산합니다.
   *
   * @param executor 가상 스레드 실행자
   * @param userSkills 사용자 보유 스킬 목록
   * @param peerGroups 원천별 완화된 동료집단 매핑
   * @param totalPostingsMap 원천별 전체 모수 매핑
   * @param baseYearMonth 기준년월
   * @return 스킬 ID별, 원천별 수요 지수 계산 결과 매핑
   * @throws ExecutionException 비동기 작업 실패 시
   * @throws InterruptedException 대기 중 인터럽트 발생 시
   */
  private Map<UUID, Map<String, SkillDemandCalculator.SkillDemandResult>> fetchSkillDemandResults(
      ExecutorService executor,
      List<UserSkill> userSkills,
      Map<String, PeerGroupFallbackResolver.ResolvedPeerGroup> peerGroups,
      Map<String, SkillDemandCalculator.TotalPostings> totalPostingsMap,
      String baseYearMonth)
      throws ExecutionException, InterruptedException {

    List<SkillSourceTask> tasks = new ArrayList<>();

    for (UserSkill us : userSkills) {
      Skill skill = us.getSkill();
      UUID skillId = skill.getId();

      // 표준 스킬이 아닌 사용자 정의(Custom) 스킬은 통계 조회 대상에서 제외
      if (skill.isCustom()) {
        continue;
      }

      for (String source : SUPPORTED_SOURCES) {
        PeerGroupFallbackResolver.ResolvedPeerGroup peerGroup = peerGroups.get(source);
        SkillDemandCalculator.TotalPostings totalPostings = totalPostingsMap.get(source);

        Future<SkillDemandCalculator.SkillDemandResult> future =
            executor.submit(
                withDbPermit(
                    () -> {
                      if (totalPostings != null) {
                        return skillDemandCalculator.calculateDemandIndex(
                            source, skillId, peerGroup, baseYearMonth, totalPostings);
                      } else {
                        return skillDemandCalculator.calculateDemandIndex(
                            source, skillId, peerGroup, baseYearMonth);
                      }
                    }));
        tasks.add(new SkillSourceTask(skillId, source, future));
      }
    }

    Map<UUID, Map<String, SkillDemandCalculator.SkillDemandResult>> demandResults = new HashMap<>();
    for (SkillSourceTask task : tasks) {
      demandResults
          .computeIfAbsent(task.skillId(), k -> new HashMap<>())
          .put(task.source(), task.future().get());
    }
    return demandResults;
  }

  // =========================================================================
  // 4. 도메인 분류 및 계산 로직
  // =========================================================================

  /**
   * 스킬 수요 산출 결과에 비즈니스 도메인 규칙을 적용하여 상태(제외/0수요/정상)를 분류합니다.
   *
   * <h3>도메인 분류 규칙</h3>
   *
   * <ul>
   *   <li><b>커스텀 스킬:</b> {@code CUSTOM_SKILL_NO_STATS}로 제외
   *   <li><b>신규 관측 스킬:</b> 분모가 0인 경우 {@code NEW_OBSERVATION}으로 원천 무관 제외
   *   <li><b>표본 미달 스킬:</b> 모든 원천에서 표본이 미달인 경우에만 {@code INSUFFICIENT_SAMPLE}로 최종 제외 (한 원천이라도 유효하면 해당
   *       원천으로 결합 유지)
   *   <li><b>최근 수요 없음:</b> 분자가 0인 경우 지수 0으로 가중합에 반영하되 별도 표시
   * </ul>
   *
   * @param userSkills 사용자 보유 스킬 목록
   * @param demandResults 스킬별/원천별 수요 산출 결과
   * @return 스킬 상태 분류 결과 객체
   */
  private SkillClassification classifySkills(
      List<UserSkill> userSkills,
      Map<UUID, Map<String, SkillDemandCalculator.SkillDemandResult>> demandResults) {

    Map<UUID, SkillExclusionReason> excludedSkillMap = new HashMap<>();
    Set<UUID> zeroDemandSkillIds = new HashSet<>();
    Set<UUID> underSampledSkillIds = new HashSet<>();
    Map<String, Map<UUID, Double>> sourceSkillDemandIndices = new HashMap<>();
    for (String source : SUPPORTED_SOURCES) {
      sourceSkillDemandIndices.put(source, new HashMap<>());
    }

    for (UserSkill us : userSkills) {
      Skill skill = us.getSkill();
      UUID skillId = skill.getId();

      // 1. 표준 스킬이 아닌 사용자 정의 스킬 여부 검사 (도메인 정의서 §4.2)
      if (skill.isCustom()) {
        excludedSkillMap.put(skillId, SkillExclusionReason.CUSTOM_SKILL_NO_STATS);
        continue;
      }

      Map<String, SkillDemandCalculator.SkillDemandResult> sourceMap =
          demandResults.getOrDefault(skillId, Collections.emptyMap());

      for (String source : SUPPORTED_SOURCES) {
        SkillDemandCalculator.SkillDemandResult demandResult = sourceMap.get(source);
        if (demandResult == null) {
          continue;
        }

        SkillDemandStatus demandStatus = demandResult.getStatus();
        if (demandStatus == SkillDemandStatus.NEW_OBSERVATION) {
          // 분모 0인 신규 관측 스킬 -> 가중합 제외 (기능 명세 §4.1)
          excludedSkillMap.put(skillId, SkillExclusionReason.NEW_OBSERVATION);
          continue;
        }
        if (demandStatus == SkillDemandStatus.INSUFFICIENT_SAMPLE) {
          // 이 원천에서만 미달일 수 있다. 지수를 넣지 않으면 이 원천의 가중합에서 자동으로 빠진다.
          underSampledSkillIds.add(skillId);
          continue;
        }
        if (demandStatus == SkillDemandStatus.NO_RECENT_DEMAND) {
          // 분자 0 -> 지수 0 으로 가중합에 반영하되 화면에서 구분한다 (기능 명세 §4.1)
          zeroDemandSkillIds.add(skillId);
        }
        sourceSkillDemandIndices.get(source).put(skillId, demandResult.getDemandIndex());
      }
    }

    // 모든 원천에서 미달인 스킬만 제외한다 — 한 원천이라도 지수를 냈으면 그 원천으로 결합한다 (기술 명세 §2.1).
    for (UUID underSampled : underSampledSkillIds) {
      boolean usableInAnySource =
          sourceSkillDemandIndices.values().stream().anyMatch(m -> m.containsKey(underSampled));
      if (!usableInAnySource && !excludedSkillMap.containsKey(underSampled)) {
        excludedSkillMap.put(underSampled, SkillExclusionReason.INSUFFICIENT_SAMPLE);
      }
    }

    // 제외 스킬 목록 DTO 구성
    Set<UUID> excludedSkillIds = excludedSkillMap.keySet();
    List<ExcludedSkill> excludedSkills = buildExcludedSkills(userSkills, excludedSkillMap);
    List<ExcludedSkill> zeroDemandSkills =
        buildZeroDemandSkills(userSkills, zeroDemandSkillIds, excludedSkillIds);

    return new SkillClassification(
        excludedSkillMap,
        zeroDemandSkillIds,
        sourceSkillDemandIndices,
        excludedSkills,
        zeroDemandSkills);
  }

  /**
   * 사용자 스킬 목록에서 커스텀 스킬 식별자 집합을 추출합니다.
   *
   * @param userSkills 사용자 보유 스킬 목록
   * @return 커스텀 스킬 UUID 집합
   */
  private Set<UUID> identifyCustomSkillIds(List<UserSkill> userSkills) {
    Set<UUID> customSkillIds = new HashSet<>();
    for (UserSkill us : userSkills) {
      if (us.getSkill().isCustom()) {
        customSkillIds.add(us.getSkill().getId());
      }
    }
    return customSkillIds;
  }

  /**
   * 정규화된 스킬 가중치와 원천별 스킬 수요 지수를 결합하여 최종 MVI 지수를 산출합니다.
   *
   * @param normalizedWeights 유효 스킬 정규화 가중치 매핑
   * @param resolvedPeerGroups 원천별 완화된 동료집단 매핑
   * @param sourceSkillDemandIndices 원천별 스킬 수요 지수 매핑
   * @return 가중 결합된 최종 MVI (산출 불가 시 null)
   */
  private BigDecimal calculateCombinedMvi(
      Map<UUID, BigDecimal> normalizedWeights,
      Map<String, PeerGroupFallbackResolver.ResolvedPeerGroup> resolvedPeerGroups,
      Map<String, Map<UUID, Double>> sourceSkillDemandIndices) {

    double weightedMviSum = 0.0;
    int validSampleSum = 0;

    for (String source : SUPPORTED_SOURCES) {
      PeerGroupFallbackResolver.ResolvedPeerGroup peerGroup = resolvedPeerGroups.get(source);
      if (!peerGroup.isSuccessful() || peerGroup.getSampleCount() <= 0) {
        continue;
      }

      Map<UUID, Double> skillIndices = sourceSkillDemandIndices.get(source);
      if (skillIndices == null) {
        continue;
      }
      double weightedSum = 0.0;
      double availableWeight = 0.0;

      for (Map.Entry<UUID, BigDecimal> entry : normalizedWeights.entrySet()) {
        Double demandIndex = skillIndices.get(entry.getKey());
        if (demandIndex == null) {
          // 이 원천에서 표본이 미달인 스킬. 중립값으로 채우지 않는다 — 숫자를 지어내는 것이기 때문이다.
          continue;
        }
        double weight = entry.getValue().doubleValue();
        weightedSum += weight * demandIndex;
        availableWeight += weight;
      }

      if (availableWeight <= 0.0) {
        // 이 원천에는 기준을 통과한 스킬이 하나도 없다 -> 결합에서 뺀다 (기술 명세 §2.1)
        continue;
      }

      // 이 원천에서 쓸 수 있는 스킬만으로 가중치를 100% 로 다시 맞춘다.
      double sourceMvi = weightedSum / availableWeight;

      int sampleCount = peerGroup.getSampleCount();
      weightedMviSum += sourceMvi * sampleCount;
      validSampleSum += sampleCount;
    }

    if (validSampleSum > 0) {
      double combinedMvi = weightedMviSum / validSampleSum;
      return BigDecimal.valueOf(combinedMvi).setScale(1, RoundingMode.HALF_UP);
    }
    return null;
  }

  // =========================================================================
  // 5. DTO 및 결과 빌더 헬퍼
  // =========================================================================

  private MviResult buildEmptySkillsResult(String baseYearMonth) {
    return new MviResult(
        baseYearMonth,
        null,
        IndexStatus.INSUFFICIENT_SAMPLE,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        null,
        LocalDate.now());
  }

  private MviResult buildInsufficientSampleResult(
      String baseYearMonth, List<SourceCoverage> coverages, WageBaseline wageBaseline) {
    return new MviResult(
        baseYearMonth,
        null,
        IndexStatus.INSUFFICIENT_SAMPLE,
        coverages,
        List.of(),
        List.of(),
        List.of(),
        wageBaseline,
        LocalDate.now());
  }

  private MviResult buildNoValidSkillsResult(
      String baseYearMonth,
      List<SourceCoverage> coverages,
      SkillClassification classification,
      WageBaseline wageBaseline) {
    return new MviResult(
        baseYearMonth,
        null,
        IndexStatus.INSUFFICIENT_SAMPLE,
        coverages,
        classification.excludedSkills(),
        classification.zeroDemandSkills(),
        List.of(),
        wageBaseline,
        LocalDate.now());
  }

  private MviResult buildFinalMviResult(
      String baseYearMonth,
      BigDecimal finalMviValue,
      List<SourceCoverage> coverages,
      SkillClassification classification,
      List<SkillContribution> skillContributions,
      WageBaseline wageBaseline) {
    IndexStatus status = (finalMviValue != null) ? IndexStatus.OK : IndexStatus.INSUFFICIENT_SAMPLE;

    // TODO: [Member B/C] aggregateAsOf에 market_aggregate 최신 적재 일자 또는 배치 완료 시점 동적 매핑
    return new MviResult(
        baseYearMonth,
        finalMviValue,
        status,
        coverages,
        classification.excludedSkills(),
        classification.zeroDemandSkills(),
        skillContributions,
        wageBaseline,
        LocalDate.now());
  }

  /**
   * 스킬별 지수 기여도를 만든다 (개발 계획 §5-M).
   *
   * <p>수요 지수는 MVI 결합과 같은 방식으로 접는다 — 원천별 지수를 그 원천의 표본 수로 가중 평균한다. 두 경로가 다른 방식을 쓰면 기여도의 합이 지수와 어긋난다.
   * 어느 원천에서도 지수를 내지 못한 스킬은 담지 않는다: 0 으로 채우면 기여가 없었다는 뜻이 되는데 실제로는 표본이 없었던 것이다.
   */
  private List<SkillContribution> buildSkillContributions(
      List<UserSkill> userSkills,
      Map<UUID, BigDecimal> normalizedWeights,
      Map<String, Map<UUID, Double>> sourceSkillDemandIndices,
      Map<String, PeerGroupFallbackResolver.ResolvedPeerGroup> resolvedPeerGroups) {

    List<SkillContribution> contributions = new ArrayList<>();

    for (UserSkill userSkill : userSkills) {
      UUID skillId = userSkill.getSkill().getId();
      BigDecimal weight = normalizedWeights.get(skillId);
      if (weight == null) {
        continue;
      }

      double weightedIndexSum = 0.0;
      int sampleSum = 0;
      for (String source : SUPPORTED_SOURCES) {
        PeerGroupFallbackResolver.ResolvedPeerGroup peerGroup = resolvedPeerGroups.get(source);
        Double demandIndex = sourceSkillDemandIndices.get(source).get(skillId);
        if (demandIndex == null || peerGroup == null || !peerGroup.isSuccessful()) {
          continue;
        }
        weightedIndexSum += demandIndex * peerGroup.getSampleCount();
        sampleSum += peerGroup.getSampleCount();
      }
      if (sampleSum == 0) {
        continue;
      }

      BigDecimal demandIndex =
          BigDecimal.valueOf(weightedIndexSum / sampleSum).setScale(1, RoundingMode.HALF_UP);
      contributions.add(
          new SkillContribution(
              userSkill.getSkill().getStandardName(),
              weight,
              demandIndex,
              weight.multiply(demandIndex).setScale(2, RoundingMode.HALF_UP)));
    }
    return contributions;
  }

  private List<SkillDemandDetailData> buildSkillDemandDetails(
      List<UserSkill> userSkills,
      Set<UUID> customSkillIds,
      Map<UUID, BigDecimal> weights,
      PeerGroupResolution peerGroupResolution,
      Map<UUID, Map<String, SkillDemandCalculator.SkillDemandResult>> demandResults) {

    List<SkillDemandDetailData> resultList = new ArrayList<>();
    int totalSamples = peerGroupResolution.totalSampleCount();

    for (UserSkill us : userSkills) {
      Skill skill = us.getSkill();
      UUID skillId = skill.getId();
      String standardName = skill.getStandardName();
      double weight = weights.getOrDefault(skillId, BigDecimal.ZERO).doubleValue();

      if (customSkillIds.contains(skillId)) {
        resultList.add(
            SkillDemandDetailData.builder()
                .skillId(skillId)
                .standardName(standardName)
                .demandIndex(0.0)
                .recentDemandRate(0.0)
                .baselineDemandRate(0.0)
                .weight(0.0)
                .status("CUSTOM_SKILL_NO_STATS")
                .sources(List.of())
                .build());
        continue;
      }

      double combinedDemandIndex = 0.0;
      double combinedRecentRate = 0.0;
      double combinedBaselineRate = 0.0;
      List<SkillSourceStatData> sourceStats = new ArrayList<>();
      String overallStatus = "OK";

      Map<String, SkillDemandCalculator.SkillDemandResult> sourceMap =
          demandResults.getOrDefault(skillId, Collections.emptyMap());

      for (String source : SUPPORTED_SOURCES) {
        PeerGroupFallbackResolver.ResolvedPeerGroup peerGroup =
            peerGroupResolution.peerGroups().get(source);
        SkillDemandCalculator.SkillDemandResult demandRes = sourceMap.get(source);
        if (demandRes == null) {
          continue;
        }

        int sampleSize = peerGroup.getSampleCount();
        sourceStats.add(new SkillSourceStatData(source, demandRes.getDemandIndex(), sampleSize));

        if (demandRes.getStatus() == SkillDemandStatus.NEW_OBSERVATION) {
          overallStatus = SkillDemandStatus.NEW_OBSERVATION.name();
        }

        if (totalSamples > 0 && peerGroup.isSuccessful()) {
          double share = (double) sampleSize / totalSamples;
          combinedDemandIndex += demandRes.getDemandIndex() * share;
          combinedRecentRate += demandRes.getRecentDemandRate() * share;
          combinedBaselineRate += demandRes.getBaselineDemandRate() * share;
        }
      }

      resultList.add(
          SkillDemandDetailData.builder()
              .skillId(skillId)
              .standardName(standardName)
              .demandIndex(
                  BigDecimal.valueOf(combinedDemandIndex)
                      .setScale(1, RoundingMode.HALF_UP)
                      .doubleValue())
              .recentDemandRate(
                  BigDecimal.valueOf(combinedRecentRate)
                      .setScale(4, RoundingMode.HALF_UP)
                      .doubleValue())
              .baselineDemandRate(
                  BigDecimal.valueOf(combinedBaselineRate)
                      .setScale(4, RoundingMode.HALF_UP)
                      .doubleValue())
              .weight(weight)
              .status(overallStatus)
              .sources(sourceStats)
              .build());
    }

    return resultList;
  }

  private List<ExcludedSkill> buildExcludedSkills(
      List<UserSkill> userSkills, Map<UUID, SkillExclusionReason> excludedSkillMap) {
    List<ExcludedSkill> excludedSkills = new ArrayList<>();
    for (UserSkill us : userSkills) {
      UUID skillId = us.getSkill().getId();
      SkillExclusionReason reason = excludedSkillMap.get(skillId);
      if (reason != null) {
        excludedSkills.add(new ExcludedSkill(skillId, us.getSkill().getStandardName(), reason));
      }
    }
    return excludedSkills;
  }

  // 0 으로 반영된 스킬 목록. 제외가 우선하므로 겹치면 제외 쪽에만 남긴다 — 제외 스킬은 가중치 배분에서
  // 아예 빠지고, 0 반영 스킬은 가중치를 그대로 받는다.
  private List<ExcludedSkill> buildZeroDemandSkills(
      List<UserSkill> userSkills, Set<UUID> zeroDemandSkillIds, Set<UUID> excludedSkillIds) {
    List<ExcludedSkill> zeroDemandSkills = new ArrayList<>();
    for (UserSkill us : userSkills) {
      UUID skillId = us.getSkill().getId();
      if (zeroDemandSkillIds.contains(skillId) && !excludedSkillIds.contains(skillId)) {
        zeroDemandSkills.add(
            new ExcludedSkill(
                skillId, us.getSkill().getStandardName(), SkillExclusionReason.NO_RECENT_DEMAND));
      }
    }
    return zeroDemandSkills;
  }

  // =========================================================================
  // 6. 내부 데이터 홀더 레코드
  // =========================================================================

  /**
   * 사용자 식별자로부터 조회된 프로필 및 보유 스킬 데이터 홀더.
   *
   * @param profile 커리어 프로필 (미등록 시 null일 수 있음)
   * @param skills 사용자가 등록한 보유 스킬 목록
   */
  private record UserData(CareerProfile profile, List<UserSkill> skills) {
    boolean hasSkills() {
      return skills != null && !skills.isEmpty();
    }

    CareerProfile profileOrDefault(UUID userId) {
      return profile != null ? profile : new CareerProfile(userId);
    }
  }

  /**
   * 원천별 동료집단 완화 결과 및 총 표본수 집계 홀더.
   *
   * @param peerGroups 원천별 동료집단 완화 결과 매핑
   * @param coverages 원천별 커버리지 목록
   * @param totalSampleCount 모든 원천의 유효 표본수 총합
   */
  private record PeerGroupResolution(
      Map<String, PeerGroupFallbackResolver.ResolvedPeerGroup> peerGroups,
      List<SourceCoverage> coverages,
      int totalSampleCount) {

    boolean hasSufficientSample() {
      return totalSampleCount >= PeerGroupFallbackResolver.MIN_SAMPLE_SIZE;
    }
  }

  /** (원천 × 스킬) 비동기 수요 지수 계산 태스크 홀더. */
  private record SkillSourceTask(
      UUID skillId, String source, Future<SkillDemandCalculator.SkillDemandResult> future) {}

  /**
   * 스킬 분류 결과 및 가중합용 수요 지수 홀더.
   *
   * @param excludedSkillMap 제외 사유 매핑 (skillId -> 사유 열거형)
   * @param zeroDemandSkillIds 최근 수요가 0인 스킬 식별자 집합
   * @param sourceSkillDemandIndices 원천별 스킬 수요 지수 매핑
   * @param excludedSkills 제외 스킬 DTO 목록
   * @param zeroDemandSkills 0 반영 스킬 DTO 목록
   */
  private record SkillClassification(
      Map<UUID, SkillExclusionReason> excludedSkillMap,
      Set<UUID> zeroDemandSkillIds,
      Map<String, Map<UUID, Double>> sourceSkillDemandIndices,
      List<ExcludedSkill> excludedSkills,
      List<ExcludedSkill> zeroDemandSkills) {

    Set<UUID> excludedSkillIds() {
      return excludedSkillMap.keySet();
    }
  }

  @lombok.Getter
  @lombok.Builder
  public static class SkillDemandDetailData {
    private final UUID skillId;
    private final String standardName;
    private final double demandIndex;
    private final double recentDemandRate;
    private final double baselineDemandRate;
    private final double weight;
    private final String status;
    private final List<SkillSourceStatData> sources;
  }

  public record SkillSourceStatData(String source, double demandIndex, int sampleSize) {}
}
