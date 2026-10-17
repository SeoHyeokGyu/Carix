package com.carix.index.owi;

import com.carix.config.IndexThresholdProperties;
import com.carix.datasource.ReadOnlyRoutingContext;
import com.carix.index.OpeningWindowIndexProvider;
import com.carix.index.dto.OwiResult;
import com.carix.index.enums.IndexStatus;
import com.carix.index.mvi.PeerGroupFallbackResolver;
import com.carix.index.mvi.SourceCombineWeights;
import com.carix.market.MarketAggregate;
import com.carix.market.MarketAggregateRepository;
import com.carix.profile.domain.CareerProfile;
import com.carix.profile.repository.CareerProfileRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 채용 활발도(Opening Window Index, OWI) 산출 서비스 구현체 (기능 명세 §4.5, 개발 계획 §2 b22).
 *
 * <p><b>도메인 배경 및 재정의 경위 (b22):</b><br>
 * 초기 OWI(b6)는 채용공고의 게시 기간(중앙 개방일수 비)을 측정했으나, 포털 기본 마감일(30일·60일)에 값이 집중되어 지수가 100 부근에 고착되고, 단기 마감
 * 공고가 수집 이전에 닫혀 누락되는 생존 편향(Survivorship Bias) 문제가 있었습니다. 이에 따라 게시 기간 지표를 폐기하고, <b>전체 신규 수집 공고(유량) 중
 * 사용자의 동료집단(직군 × 경력 구간) 공고가 차지하는 채용 비중의 추이</b>로 OWI를 재정의하였습니다.
 *
 * <ul>
 *   <li><b>MVI와의 역할 분담:</b> 시장가치 지수(MVI)가 「동료집단 안의 내 보유 스킬 수요 비중 추이」를 측정한다면, 채용 활발도(OWI)는 「전체 채용 시장
 *       안의 내 동료집단 공고 비중 추이」를 측정하여 서로 독립된 축을 담당합니다.
 *   <li><b>유량(Flow) 모수 및 규모 변동 상쇄:</b> 분자(동료집단 공고 수)와 분모(전체 시장 공고 수) 모두 해당 월 신규 수집 공고 유량을 사용하므로, 월별
 *       크롤링 총량 변동이 비중(Ratio) 계산 과정에서 자연 상쇄됩니다.
 *   <li><b>지수 100 기준선 해석:</b>
 *       <ul>
 *         <li><b>지수 = 100:</b> 최근 3개월 동료집단 채용 비중이 이전 12개월(4~15개월 전) 평균 비중과 동일함 (보합)
 *         <li><b>지수 &gt; 100 (비중 확대):</b> 전체 시장 내 해당 직군·경력대 채용 비중이 과거 1년보다 활발해짐
 *         <li><b>지수 &lt; 100 (비중 축소):</b> 전체 시장 내 해당 직군·경력대 채용 비중이 과거 1년보다 위축됨
 *       </ul>
 *   <li><b>명칭 규약:</b> 사용자 화면 표시명은 <b>「채용 활발도」</b>로 표기하되, 하위 호환성 및 스키마 일관성을 위해 내부 코드·DB 컬럼·API 프로퍼티
 *       명칭({@code OWI}, {@code opening_window_index}, {@code openingWindowIndex}, {@code
 *       openingWindow})은 그대로 유지합니다.
 * </ul>
 *
 * <h3>핵심 산출 공식 및 파이프라인 요약</h3>
 *
 * <pre>
 * 원천별 최근 3개월 비중(%)  = (최근 3개월 동료집단 공고 수 ÷ 최근 3개월 전체시장 공고 수) × 100
 * 원천별 이전 12개월 비중(%) = (이전 12개월 동료집단 공고 수 ÷ 이전 12개월 전체시장 공고 수) × 100
 * 원천별 OWI                 = clamp((최근 3개월 비중 ÷ 이전 12개월 비중) × 100, [25.00, 400.00])
 * 통합 OWI                   = clamp(∑(w_src × 원천별 OWI) ÷ ∑w_src, [25.00, 400.00])
 * </pre>
 *
 * <ol>
 *   <li><b>기간 창(Window) 분리:</b> MVI와 동일하게 기준월({@code baseYearMonth}) 당월은 부분 수집월이므로 제외하고, 직전
 *       3개월({@code [targetYm - 3m, targetYm - 1m]})을 최근 창으로, 그 이전 12개월({@code [targetYm - 15m,
 *       targetYm - 4m]}, 비중첩)을 기준선 창으로 설정합니다.
 *   <li><b>사전 집계 원천 활용:</b> 원 공고({@code job_posting}) 직접 집계 대신 {@code core.market_aggregate}의 스킬 무관
 *       전체 공고 행({@code skill_id IS NULL})을 원천별({@code JOBKOREA}, {@code SARAMIN}, {@code WANTED},
 *       {@code JUMPIT})로 조회합니다.
 *   <li><b>경력 축 2단계 동료집단 완화(Fallback Ladder):</b>
 *       <ul>
 *         <li><b>Level 1 (본인 경력 구간):</b> 사용자의 연차를 3구간({@code JUNIOR}/{@code MID}/{@code SENIOR})으로
 *             매핑한 뒤, 수집기 6구간 행({@code ENTRY+JUNIOR}, {@code MID}, {@code SENIOR+LEAD})을 합산합니다.
 *         <li><b>Level 2 (인접 경력 구간 확장):</b> Level 1에서 최근 3개월 또는 이전 12개월 표본이 최소 기준({@code N >= 10})에
 *             미달하면 인접 경력 구간까지 넓혀 재조회합니다.
 *       </ul>
 *   <li><b>원천별 결합 가중치 ({@link SourceCombineWeights}):</b> 무작위 표본 원천({@code JOBKOREA}, {@code
 *       WANTED})은 최근 창 동료집단 표본 수를 그대로 가중치로 쓰고, 정기 수집 원천({@code SARAMIN}, {@code JUMPIT})은 합계가 표본 원천
 *       최대 표본 수를 넘지 않도록 비례 축소하여 특정 포털 목록 크기에 지수가 끌려가는 현상을 방지합니다.
 *   <li><b>표본 부족 처리 및 부분 근거 보존:</b> 수집 이력이 15개월 미만이거나 모든 원천에서 이전 12개월 창 표본이 부족하면 {@link
 *       IndexStatus#INSUFFICIENT_SAMPLE}과 {@code value = null}을 반환합니다. 단, 최근 3개월 창 표본이 기준 이상인 원천이
 *       존재하면 최근 비중({@code recentSharePercent})은 산출하여 화면 배지로 노출할 수 있도록 보존합니다.
 * </ol>
 *
 * <p>Java 21 가상 스레드(Virtual Threads)와 동시 DB I/O 세마포어({@link #dbConcurrencyLimiter}), 읽기 전용 데이터소스
 * 라우팅({@link ReadOnlyRoutingContext})을 결합하여 다중 원천 집계를 병렬로 안전하게 처리합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpeningWindowIndexServiceImpl implements OpeningWindowIndexProvider {

  /**
   * 채용 활발도 산출에 참여하는 원천 플랫폼 목록.
   *
   * <p>MVI와 동일한 4개 원천을 사용하며, 각 원천은 고유한 측정 프레임 안에서만 최근 창과 이전 창의 비중을 비교합니다(원천 간 모수를 직접 합산하여 비중을 내지
   * 않고, 원천별 지수·비중을 먼저 구한 뒤 가중 평균으로 결합).
   */
  public static final List<String> SUPPORTED_SOURCES =
      List.of("JOBKOREA", "SARAMIN", "WANTED", "JUMPIT");

  /**
   * 유효 기준월({@code effectiveYearMonth}) 탐색의 기준이 되는 대표 표본 원천.
   *
   * <p>과거 12개월 백필 표본이 적재되어 있는 {@code JOBKOREA} 집계의 최신·최초 가용 월을 기준으로 삼아 당월 미수집 시점을 보정합니다.
   */
  static final String SAMPLED_SOURCE = "JOBKOREA";

  /** 지수 하한 클램프 기본값 (기능 명세 §4.5: 25.00) */
  private static final BigDecimal DEFAULT_MIN_CLAMP = new BigDecimal("25.00");

  /** 지수 상한 클램프 기본값 (기능 명세 §4.5: 400.00) */
  private static final BigDecimal DEFAULT_MAX_CLAMP = new BigDecimal("400.00");

  /** 기준년월 파싱 및 포맷팅에 사용하는 6자리 연월 포맷터 (uuuuMM) */
  private static final DateTimeFormatter YM_FORMATTER = DateTimeFormatter.ofPattern("uuuuMM");

  /**
   * DB 커넥션 풀 고갈(Connection Starvation) 방지를 위한 최대 동시 DB I/O 허용 수.
   *
   * <p>가상 스레드 팬아웃 시 읽기 전용 풀({@code CarixReadOnlyPool})의 커넥션을 과도하게 점유하지 않도록 인스턴스당 동시 쿼리 실행 수를 8개로
   * 제한합니다(개발 계획 {@code b17}).
   */
  private static final int DEFAULT_MAX_CONCURRENT_DB_TASKS = 8;

  /** 가상 스레드 병렬 집계 시 동시 DB 접근 수를 제어하는 세마포어 */
  private final Semaphore dbConcurrencyLimiter = new Semaphore(DEFAULT_MAX_CONCURRENT_DB_TASKS);

  private final CareerProfileRepository careerProfileRepository;
  private final MarketAggregateRepository marketAggregateRepository;
  private final PeerGroupFallbackResolver peerGroupFallbackResolver;
  private final IndexThresholdProperties thresholds;

  /**
   * 동료집단 유효 표본 최소 기준치(\(N\))를 반환합니다.
   *
   * <p>Config Server 설정({@code carix.index.thresholds.peer-group-min-sample-size})이 주입된 경우 해당 값을
   * 사용하고, 테스트 등 미주입 환경에서는 기본값({@value PeerGroupFallbackResolver#MIN_SAMPLE_SIZE})으로 폴백합니다.
   */
  private int minSampleSize() {
    return thresholds == null
        ? PeerGroupFallbackResolver.MIN_SAMPLE_SIZE
        : thresholds.getPeerGroupMinSampleSize();
  }

  /** 지수 하한 클램프 설정값 반환 (기본값 25.00) */
  private BigDecimal minClamp() {
    return thresholds == null
        ? DEFAULT_MIN_CLAMP
        : BigDecimal.valueOf(thresholds.getSkillDemandMinClamp()).setScale(2, RoundingMode.HALF_UP);
  }

  /** 지수 상한 클램프 설정값 반환 (기본값 400.00) */
  private BigDecimal maxClamp() {
    return thresholds == null
        ? DEFAULT_MAX_CLAMP
        : BigDecimal.valueOf(thresholds.getSkillDemandMaxClamp()).setScale(2, RoundingMode.HALF_UP);
  }

  // =========================================================================
  // 1. OWI 산출 메인 파이프라인
  // =========================================================================

  /**
   * 지정된 사용자와 기준년월에 대해 채용 활발도(OWI)를 산출합니다.
   *
   * <h3>단계별 처리 흐름</h3>
   *
   * <ol>
   *   <li>입력 파라미터({@code userId}, {@code baseYearMonth}) 및 날짜 포맷(`YYYYMM` 또는 `YYYY-MM`) 검증 — 유효하지
   *       않으면 즉시 {@link OwiResult#notAvailable(String)} 반환
   *   <li>사용자 커리어 프로필({@link CareerProfile}) 조회 — 온보딩 미완료 등으로 프로필이 없으면 {@code NOT_AVAILABLE} 반환
   *   <li>기준월 보정({@link #resolveEffectiveYearMonth}) 및 최근 3개월·이전 12개월(비중첩) 기간 창 리스트 도출
   *   <li>사용자 직군·연차 기반 2단계 동료집단 완화 명세({@link PeerGroupLadderSpec}) 생성
   *   <li>4대 원천({@link #SUPPORTED_SOURCES})에 대해 가상 스레드 병렬로 전체 시장 모수 및 단계별 동료집단 표본 수 집계({@link
   *       #fetchSourceStats})
   *   <li>원천별 통계를 {@link SourceCombineWeights} 가중치로 결합하여 최종 지수 및 최근·이전 비중 산출({@link
   *       #combineSourceStats})
   * </ol>
   *
   * @param userId 대상 사용자 식별자
   * @param baseYearMonth 기준년월 (`YYYYMM` 또는 `YYYY-MM`)
   * @return 채용 활발도 산출 결과 {@link OwiResult} (응답의 {@code baseYearMonth}는 요청된 기준월 문자열을 정규화하여 유지)
   */
  @Override
  @Transactional(readOnly = true)
  public OwiResult calculate(UUID userId, String baseYearMonth) {
    // 1. 필수 파라미터 검증
    if (userId == null || baseYearMonth == null || baseYearMonth.isBlank()) {
      return OwiResult.notAvailable(baseYearMonth);
    }

    // 하이픈(YYYY-MM) 표기를 6자리(YYYYMM)로 정규화하고 유효한 연월인지 파싱 검증
    String normalizedYm = baseYearMonth.replace("-", "").trim();
    try {
      YearMonth.parse(normalizedYm, YM_FORMATTER);
    } catch (DateTimeParseException e) {
      log.warn("Invalid baseYearMonth format for OWI: {}", baseYearMonth);
      return OwiResult.notAvailable(baseYearMonth);
    }

    // 2. 커리어 프로필 조회 (직무군·경력 연차 확보)
    Optional<CareerProfile> profileOpt = careerProfileRepository.findByUserId(userId);
    if (profileOpt.isEmpty()) {
      return OwiResult.notAvailable(normalizedYm);
    }

    CareerProfile profile = profileOpt.get();

    // 3. 실제 집계가 존재하는 유효 기준월 해석 및 비교 기간 창(최근 3개월 vs 이전 12개월) 구성
    //    기준월 당월(targetYm)은 월중 부분 수집 상태일 수 있으므로 창에서 제외하고 직전 월(targetYm - 1m)부터 역산한다.
    String effectiveYearMonth = resolveEffectiveYearMonth(normalizedYm);
    YearMonth targetYm = YearMonth.parse(effectiveYearMonth, YM_FORMATTER);

    List<String> recentMonths = getMonthsRange(targetYm.minusMonths(3), 3);
    List<String> baselineMonths = getMonthsRange(targetYm.minusMonths(15), 12);

    // 4. 프로필 연차 기반 Level 1(본인 구간) / Level 2(인접 확장 구간) 조회 명세 구성
    PeerGroupLadderSpec spec = buildLadderSpec(profile);

    // 5. 원천별 전체시장 모수 및 동료집단 표본 수 병렬 조회
    Map<String, SourceShareStats> sourceStats =
        fetchSourceStats(spec, recentMonths, baselineMonths);

    // 6. 원천별 결합 가중치 적용 및 최종 OwiResult 조립 (결과 기준월은 요청월 normalizedYm 유지)
    return combineSourceStats(normalizedYm, spec, sourceStats);
  }

  // =========================================================================
  // 2. 원천별 병렬 집계 및 동료집단 2단계 완화
  // =========================================================================

  /**
   * 지원하는 모든 원천({@link #SUPPORTED_SOURCES})에 대해 가상 스레드로 {@link #evaluateSource}를 병렬 실행합니다.
   *
   * <p>각 가상 스레드 작업은 {@link #withDbPermit(Callable)}으로 감싸 세마포어 허가증을 획득하고 읽기 전용 라우팅 컨텍스트({@link
   * ReadOnlyRoutingContext})를 활성화한 상태에서 DB 조회를 수행합니다. 결과 맵은 {@link #SUPPORTED_SOURCES} 선언 순서를
   * 보존합니다.
   *
   * @param spec 동료집단 완화 조회 명세
   * @param recentMonths 최근 3개월 연월 목록 (`[targetYm - 3m, targetYm - 1m]`)
   * @param baselineMonths 이전 12개월 연월 목록 (`[targetYm - 15m, targetYm - 4m]`)
   * @return 원천 코드를 키로 하는 {@link SourceShareStats} 순서 보존 맵
   */
  private Map<String, SourceShareStats> fetchSourceStats(
      PeerGroupLadderSpec spec, List<String> recentMonths, List<String> baselineMonths) {
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      Map<String, Future<SourceShareStats>> futures = new LinkedHashMap<>();
      for (String source : SUPPORTED_SOURCES) {
        futures.put(
            source,
            executor.submit(
                withDbPermit(() -> evaluateSource(source, spec, recentMonths, baselineMonths))));
      }

      Map<String, SourceShareStats> results = new LinkedHashMap<>();
      for (String source : SUPPORTED_SOURCES) {
        results.put(source, futures.get(source).get());
      }
      return results;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("OWI calculation interrupted", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("OWI calculation failed in virtual thread", e.getCause());
    }
  }

  /**
   * 단일 원천에 대해 전체 시장 모수와 동료집단 공고 수를 조회하고, 2단계 경력 완화 사다리를 적용합니다.
   *
   * <h3>완화(Fallback) 판정 규칙</h3>
   *
   * <ul>
   *   <li><b>전체 시장 모수 조회:</b> 직무({@code job_family = 'ALL'})와 경력({@code experience_bracket =
   *       'ALL'})을 포함한 전 차원 {@code 'ALL'} 행에서 최근 3개월·이전 12개월 총 공고 수를 먼저 구합니다.
   *   <li><b>Level 1 (본인 경력 구간):</b> 최근 3개월 동료집단 공고 수와 이전 12개월 동료집단 공고 수가 <b>모두</b> 최소 표본 기준({@code
   *       minSample}) 이상이면 Level 1에 안착합니다.
   *   <li><b>Level 2 (인접 경력 구간 확장):</b> Level 1에서 두 창 중 하나라도 표본이 부족하면 인접 경력 구간을 포함한 브래킷 목록으로 확장하여
   *       재조회합니다. 확장 후 두 창 모두 기준 이상이면 Level 2에 안착합니다.
   *   <li><b>양 단계 미달 시(예: 수집 이력 15개월 미만으로 이전 창이 비어 있는 경우):</b> 지수 산출은 불가하나 최근 3개월 비중 배지를 보여줄 수 있어야
   *       합니다. 만약 Level 1만으로도 최근 3개월 표본이 충분하다면 불필요하게 비교 조건을 넓히지 않도록 Level 1 통계를 반환하고, 그렇지 않으면 더 넓은
   *       표본을 담은 Level 2 통계를 반환합니다.
   * </ul>
   *
   * @param source 원천 플랫폼 코드 (`JOBKOREA`, `SARAMIN`, `WANTED`, `JUMPIT`)
   * @param spec 동료집단 완화 조회 명세
   * @param recentMonths 최근 3개월 연월 목록
   * @param baselineMonths 이전 12개월 연월 목록
   * @return 해당 원천의 안착 단계·표본 수·전체시장 모수를 담은 {@link SourceShareStats}
   */
  private SourceShareStats evaluateSource(
      String source,
      PeerGroupLadderSpec spec,
      List<String> recentMonths,
      List<String> baselineMonths) {

    int minSample = minSampleSize();

    // 1. 전체 시장 모수(분모): 직무·경력·지역·고용형태·회사규모가 모두 'ALL'인 전체 공고 집계 행 합산
    int recentMarket =
        sumPostings(
            source,
            recentMonths,
            PeerGroupFallbackResolver.DIMENSION_ALL,
            List.of(PeerGroupFallbackResolver.BRACKET_ALL));
    int baselineMarket =
        sumPostings(
            source,
            baselineMonths,
            PeerGroupFallbackResolver.DIMENSION_ALL,
            List.of(PeerGroupFallbackResolver.BRACKET_ALL));

    // 2. Level 1: 사용자 본인 경력 구간(collector 6구간 매핑) 동료집단 공고 수 합산
    SourceShareStats level1 =
        evaluateLadderStep(
            source,
            1,
            spec.ownCondition(),
            spec.jobFamily(),
            spec.ownCollectorBrackets(),
            recentMonths,
            baselineMonths,
            recentMarket,
            baselineMarket,
            minSample);

    // 최근 창과 이전 창 모두 최소 표본 기준을 충족하면 Level 2 조회 없이 Level 1에 즉시 안착
    if (level1.meetsBothSampleThresholds()) {
      return level1;
    }

    // 3. Level 2: 인접 경력 구간까지 확장하여 동료집단 공고 수 합산
    SourceShareStats level2 =
        evaluateLadderStep(
            source,
            2,
            spec.widenedCondition(),
            spec.jobFamily(),
            spec.widenedCollectorBrackets(),
            recentMonths,
            baselineMonths,
            recentMarket,
            baselineMarket,
            minSample);

    // 4. Level 2가 양쪽 창 기준을 충족했거나, Level 1에서 최근 창 표본조차 미달이면 확장된 Level 2를 채택하고,
    //    양쪽 다 이전 창이 부족하지만 Level 1만으로도 최근 창 표본(N >= 10)이 충분하면 불필요한 조건 확장을 피하기 위해 Level 1을 유지한다.
    return (level2.meetsBothSampleThresholds() || !level1.meetsRecentSampleThreshold())
        ? level2
        : level1;
  }

  /**
   * 특정 완화 단계(Level 1 또는 Level 2)의 브래킷 조건으로 최근 3개월·이전 12개월 동료집단 공고 수를 집계하여 {@link SourceShareStats}를
   * 생성합니다.
   */
  private SourceShareStats evaluateLadderStep(
      String source,
      int fallbackLevel,
      String conditionText,
      String jobFamily,
      List<String> collectorBrackets,
      List<String> recentMonths,
      List<String> baselineMonths,
      int recentMarket,
      int baselineMarket,
      int minSample) {
    int recentPeer = sumPostings(source, recentMonths, jobFamily, collectorBrackets);
    int baselinePeer = sumPostings(source, baselineMonths, jobFamily, collectorBrackets);
    return new SourceShareStats(
        source,
        fallbackLevel,
        conditionText,
        recentPeer,
        baselinePeer,
        recentMarket,
        baselineMarket,
        minSample);
  }

  // =========================================================================
  // 3. 다중 원천 가중 결합 및 결과 조립
  // =========================================================================

  /**
   * 원천별 집계 통계({@link SourceShareStats})를 결합하여 최종 {@link OwiResult}를 생성합니다.
   *
   * <h3>원천 결합 규칙</h3>
   *
   * <ul>
   *   <li><b>표본 수 합산:</b> 응답에 담길 {@code peerSampleSize}, {@code baselineSampleSize}, {@code
   *       marketSampleSize}는 전체 원천의 실제 관측 공고 수 총합입니다.
   *   <li><b>유효 원천 필터링:</b> 최근 3개월과 이전 12개월 모두에서 동료집단 표본이 최소 기준({@code N >= 10}) 이상이고 전체 시장 모수가 0보다
   *       큰 원천({@link SourceShareStats#usableForIndex()})만 지수 결합에 참여합니다.
   *   <li><b>표본 부족 분기:</b> 지수를 낼 수 있는 원천이 하나도 없으면 {@link IndexStatus#INSUFFICIENT_SAMPLE}과 {@code
   *       value = null}을 반환합니다. 이때 최근 창만 유효한 원천이 있으면 {@link #combineRecentShareOnly}로 최근 비중만 계산해
   *       내려줍니다.
   *   <li><b>가중 평균 결합:</b> 유효 원천들의 최근 동료집단 표본 수를 {@link SourceCombineWeights#of(Map)}에 전달해 원천별 결합
   *       가중치(\(w_{src}\))를 구합니다. 각 원천의 지수를 먼저 {@code [25.00, 400.00]}로 클램프한 뒤 가중 평균하고, 최종 통합 지수도
   *       소수점 둘째 자리 반올림 후 다시 한 번 클램프합니다.
   * </ul>
   *
   * @param normalizedYm 요청 기준년월 (YYYYMM)
   * @param spec 동료집단 완화 명세 (대표 원천 부재 시 기본 조건 문구 제공용)
   * @param sourceStats 원천별 집계 통계 맵
   * @return 최종 채용 활발도 산출 결과 {@link OwiResult}
   */
  private OwiResult combineSourceStats(
      String normalizedYm, PeerGroupLadderSpec spec, Map<String, SourceShareStats> sourceStats) {

    // 전 원천 표본 수 총합 (근거 메타데이터 기록용)
    int totalRecentPeer =
        sourceStats.values().stream().mapToInt(SourceShareStats::recentPeerPostings).sum();
    int totalBaselinePeer =
        sourceStats.values().stream().mapToInt(SourceShareStats::baselinePeerPostings).sum();
    int totalRecentMarket =
        sourceStats.values().stream().mapToInt(SourceShareStats::recentMarketPostings).sum();

    // 대표 완화 단계 및 적용 조건 문구를 제공할 대표 원천 선택
    SourceShareStats representative = selectRepresentative(sourceStats, spec);

    // 최근·이전 두 창 모두 표본 기준을 통과한 원천만 추출 (키: 원천명, 값: 최근 3개월 동료집단 표본 수)
    Map<String, Integer> usableSampleCounts = new LinkedHashMap<>();
    for (SourceShareStats s : sourceStats.values()) {
      if (s.usableForIndex()) {
        usableSampleCounts.put(s.source(), s.recentPeerPostings());
      }
    }

    // 지수를 산출할 수 있는 원천이 하나도 없는 경우 -> INSUFFICIENT_SAMPLE 반환
    // 단, 최근 3개월 창만이라도 기준을 넘긴 원천이 있으면 recentSharePercent를 산출해 카드 배지에 노출한다.
    if (usableSampleCounts.isEmpty()) {
      BigDecimal recentShare = combineRecentShareOnly(sourceStats);
      BigDecimal baselineShare = combineBaselineShareOnly(sourceStats);
      return new OwiResult(
          normalizedYm,
          null,
          IndexStatus.INSUFFICIENT_SAMPLE,
          totalRecentPeer,
          totalBaselinePeer,
          recentShare,
          baselineShare,
          representative.fallbackLevel(),
          representative.appliedConditions(),
          totalRecentMarket);
    }

    // 표본 원천(JOBKOREA·WANTED) 기준 정기 수집 원천(SARAMIN·JUMPIT) 상한이 적용된 결합 가중치 도출
    Map<String, Double> weights = SourceCombineWeights.of(usableSampleCounts);
    double weightedIndexSum = 0.0;
    double weightedRecentShareSum = 0.0;
    double weightedBaselineShareSum = 0.0;
    double weightSum = 0.0;

    for (Map.Entry<String, Double> entry : weights.entrySet()) {
      SourceShareStats s = sourceStats.get(entry.getKey());
      double w = entry.getValue();
      double recentSharePct = s.recentSharePercent();
      double baselineSharePct = s.baselineSharePercent();

      // 원천별 비중 추이 지수 = (최근 3개월 비중 ÷ 이전 12개월 비중) × 100 후 개별 클램프 적용
      double rawSourceIndex = (recentSharePct / baselineSharePct) * 100.0;
      double clampedSourceIndex =
          clamp(BigDecimal.valueOf(rawSourceIndex).setScale(2, RoundingMode.HALF_UP)).doubleValue();

      weightedIndexSum += w * clampedSourceIndex;
      weightedRecentShareSum += w * recentSharePct;
      weightedBaselineShareSum += w * baselineSharePct;
      weightSum += w;
    }

    // 가중 평균 산출 및 소수점 둘째 자리 반올림
    BigDecimal finalValue =
        clamp(BigDecimal.valueOf(weightedIndexSum / weightSum).setScale(2, RoundingMode.HALF_UP));
    BigDecimal combinedRecentShare =
        BigDecimal.valueOf(weightedRecentShareSum / weightSum).setScale(2, RoundingMode.HALF_UP);
    BigDecimal combinedBaselineShare =
        BigDecimal.valueOf(weightedBaselineShareSum / weightSum).setScale(2, RoundingMode.HALF_UP);

    return new OwiResult(
        normalizedYm,
        finalValue,
        IndexStatus.OK,
        totalRecentPeer,
        totalBaselinePeer,
        combinedRecentShare,
        combinedBaselineShare,
        representative.fallbackLevel(),
        representative.appliedConditions(),
        totalRecentMarket);
  }

  /**
   * 지수 산출이 불가능한(`INSUFFICIENT_SAMPLE`) 상황에서, 최근 3개월 창 표본 기준만 충족한 원천들의 최근 동료집단 비중(%)을 가중 결합합니다.
   *
   * <p>수집 이력이 15개월 미만이어서 이전 12개월 창이 비어 있더라도, 최근 3개월 표본이 충분하면 화면에 「최근 비중 X.X%」 배지를 보여주기 위해 사용합니다.
   */
  private BigDecimal combineRecentShareOnly(Map<String, SourceShareStats> sourceStats) {
    return combineSingleWindowShare(
        sourceStats,
        SourceShareStats::usableForRecentShare,
        SourceShareStats::recentPeerPostings,
        SourceShareStats::recentSharePercent);
  }

  /** 지수 산출이 불가능한(`INSUFFICIENT_SAMPLE`) 상황에서, 이전 12개월 창 표본 기준만 충족한 원천들의 이전 동료집단 비중(%)을 가중 결합합니다. */
  private BigDecimal combineBaselineShareOnly(Map<String, SourceShareStats> sourceStats) {
    return combineSingleWindowShare(
        sourceStats,
        SourceShareStats::usableForBaselineShare,
        SourceShareStats::baselinePeerPostings,
        SourceShareStats::baselineSharePercent);
  }

  /** 단일 기간 창(최근 3개월 또는 이전 12개월)에 대해 유효 원천들의 비중(%)을 {@link SourceCombineWeights}로 가중 평균합니다. */
  private BigDecimal combineSingleWindowShare(
      Map<String, SourceShareStats> sourceStats,
      Predicate<SourceShareStats> usableFilter,
      ToIntFunction<SourceShareStats> sampleCountExtractor,
      ToDoubleFunction<SourceShareStats> sharePercentExtractor) {
    Map<String, Integer> counts = new LinkedHashMap<>();
    for (SourceShareStats s : sourceStats.values()) {
      if (usableFilter.test(s)) {
        counts.put(s.source(), sampleCountExtractor.applyAsInt(s));
      }
    }
    if (counts.isEmpty()) {
      return null;
    }
    Map<String, Double> weights = SourceCombineWeights.of(counts);
    double sum = 0.0;
    double weightSum = 0.0;
    for (Map.Entry<String, Double> entry : weights.entrySet()) {
      SourceShareStats s = sourceStats.get(entry.getKey());
      sum += entry.getValue() * sharePercentExtractor.applyAsDouble(s);
      weightSum += entry.getValue();
    }
    return weightSum > 0
        ? BigDecimal.valueOf(sum / weightSum).setScale(2, RoundingMode.HALF_UP)
        : null;
  }

  /**
   * 다중 원천 중 스냅샷 근거(`fallbackLevel`, `appliedConditions`)를 대표할 원천을 선택합니다.
   *
   * <p>선택 우선순위:
   *
   * <ol>
   *   <li>지수 산출이 가능한 원천({@link SourceShareStats#usableForIndex()}) 중 최근 3개월 동료집단 표본 수가 가장 큰 원천
   *   <li>최근 3개월 비중 산출이 가능한 원천({@link SourceShareStats#usableForRecentShare()}) 중 최근 표본 수가 가장 큰 원천
   *   <li>관측된 동료집단 공고가 1건이라도 있는 원천 중 (최근 + 이전) 표본 합계가 가장 큰 원천
   *   <li>모든 원천이 0건이면 Level 2(인접 확장 조건) 기본값 반환
   * </ol>
   */
  private SourceShareStats selectRepresentative(
      Map<String, SourceShareStats> sourceStats, PeerGroupLadderSpec spec) {
    Optional<SourceShareStats> usableRep =
        sourceStats.values().stream()
            .filter(SourceShareStats::usableForIndex)
            .max(Comparator.comparingInt(SourceShareStats::recentPeerPostings));
    if (usableRep.isPresent()) {
      return usableRep.get();
    }

    Optional<SourceShareStats> recentRep =
        sourceStats.values().stream()
            .filter(SourceShareStats::usableForRecentShare)
            .max(Comparator.comparingInt(SourceShareStats::recentPeerPostings));
    if (recentRep.isPresent()) {
      return recentRep.get();
    }

    Optional<SourceShareStats> anyRep =
        sourceStats.values().stream()
            .filter(s -> s.recentPeerPostings() > 0 || s.baselinePeerPostings() > 0)
            .max(Comparator.comparingInt(s -> s.recentPeerPostings() + s.baselinePeerPostings()));
    return anyRep.orElseGet(
        () ->
            new SourceShareStats(
                SAMPLED_SOURCE, 2, spec.widenedCondition(), 0, 0, 0, 0, minSampleSize()));
  }

  // =========================================================================
  // 4. 데이터 조회 및 유틸리티 헬퍼
  // =========================================================================

  /**
   * {@code core.market_aggregate}에서 특정 원천·기간 창·직무군·경력 브래킷 목록에 해당하는 전체 공고 행({@code skill_id IS
   * NULL})의 공고 수 합계를 반환합니다.
   *
   * <p>지역·고용형태·회사규모 차원은 현재 수집기가 {@code 'ALL'}로만 집계하므로 {@link
   * PeerGroupFallbackResolver#DIMENSION_ALL}로 고정 조회합니다(도메인 정의서 §2).
   */
  private int sumPostings(
      String source, List<String> months, String jobFamily, List<String> brackets) {
    List<MarketAggregate> rows =
        marketAggregateRepository.findTotalPostingAggregates(
            source,
            months,
            jobFamily,
            PeerGroupFallbackResolver.DIMENSION_ALL,
            brackets,
            PeerGroupFallbackResolver.DIMENSION_ALL,
            PeerGroupFallbackResolver.DIMENSION_ALL);
    if (rows == null || rows.isEmpty()) {
      return 0;
    }
    return rows.stream().mapToInt(MarketAggregate::getPostingCount).sum();
  }

  /**
   * 사용자의 커리어 프로필로부터 Level 1(본인 경력 구간)과 Level 2(인접 경력 구간 포함) 조회에 사용할 수집기 6구간 브래킷 목록과 한국어 비교 조건 문구를
   * 생성합니다.
   *
   * <ul>
   *   <li>{@code JUNIOR} (0~3년) ➡️ 수집기 {@code [ENTRY, JUNIOR]}, 인접 확장 시 {@code [ENTRY, JUNIOR,
   *       MID]}
   *   <li>{@code MID} (4~7년) ➡️ 수집기 {@code [MID]}, 인접 확장 시 {@code [ENTRY, JUNIOR, MID, SENIOR,
   *       LEAD]}
   *   <li>{@code SENIOR} (8년 이상) ➡️ 수집기 {@code [SENIOR, LEAD]}, 인접 확장 시 {@code [MID, SENIOR, LEAD]}
   * </ul>
   */
  private PeerGroupLadderSpec buildLadderSpec(CareerProfile profile) {
    String jobFamily = orDefault(profile.getJobFamily(), "DEVELOPER");
    int years = profile.getYearsOfExperience() != null ? profile.getYearsOfExperience() : 3;
    String ownTier = peerGroupFallbackResolver.mapExperienceBracket(years);

    List<String> ownTiers = List.of(ownTier);
    List<String> ownCollectorBrackets = peerGroupFallbackResolver.toCollectorBrackets(ownTiers);
    String ownCondition = peerGroupFallbackResolver.formatNationwideConditionText(ownTiers);

    List<String> widenedTiers = peerGroupFallbackResolver.getAdjacentBrackets(ownTier);
    List<String> widenedCollectorBrackets =
        peerGroupFallbackResolver.toCollectorBrackets(widenedTiers);
    String widenedCondition = peerGroupFallbackResolver.formatNationwideConditionText(widenedTiers);

    return new PeerGroupLadderSpec(
        jobFamily, ownCollectorBrackets, ownCondition, widenedCollectorBrackets, widenedCondition);
  }

  /**
   * 채용 활발도 산출에 사용할 유효 기준년월({@code effectiveYearMonth})을 해석합니다.
   *
   * <p>잡코리아 무작위 표본은 달이 끝난 뒤 지난달 분을 백필하므로 당월에는 집계가 비어 있을 수 있습니다(데이터 아키텍처 §1.3). 따라서 요청 월({@code
   * normalizedYm}) 이하에서 {@link #SAMPLED_SOURCE} 집계가 존재하는 가장 최신 월을 우선 사용하고, 과거 집계가 전혀 없는 초기 월 요청이면
   * 가장 가까운 미래 집계 월로, DB에 집계가 아예 없으면 요청 월 그대로 폴백합니다.
   */
  private String resolveEffectiveYearMonth(String normalizedYm) {
    if (marketAggregateRepository == null) {
      return normalizedYm;
    }
    Optional<String> backward =
        marketAggregateRepository.findLatestAvailableYearMonthForSource(
            SAMPLED_SOURCE, normalizedYm);
    if (backward != null && backward.isPresent()) {
      return backward.get();
    }
    Optional<String> forward =
        marketAggregateRepository.findEarliestAvailableYearMonthForSource(
            SAMPLED_SOURCE, normalizedYm);
    if (forward != null && forward.isPresent()) {
      return forward.get();
    }
    return normalizedYm;
  }

  /** 시작 연월({@code startYm})로부터 연속된 {@code count}개의 연월 문자열(`YYYYMM`) 리스트를 오름차순으로 생성합니다. */
  private List<String> getMonthsRange(YearMonth startYm, int count) {
    List<String> months = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      months.add(startYm.plusMonths(i).format(YM_FORMATTER));
    }
    return months;
  }

  /** 산출된 지수값을 설정된 하한·상한 범위(기본 {@code [25.00, 400.00]})로 클램핑합니다 (기능 명세 §4.5). */
  private BigDecimal clamp(BigDecimal value) {
    BigDecimal min = minClamp();
    BigDecimal max = maxClamp();
    if (value.compareTo(min) < 0) {
      return min;
    }
    if (value.compareTo(max) > 0) {
      return max;
    }
    return value;
  }

  private String orDefault(String value, String fallback) {
    return (value != null && !value.isBlank()) ? value : fallback;
  }

  /**
   * 가상 스레드에서 실행할 DB 작업을 감싸 동시성 제한 세마포어({@link #dbConcurrencyLimiter}) 획득·반납과 읽기 전용 데이터소스 라우팅 컨텍스트
   * 설정·해제를 보장합니다.
   */
  private <T> Callable<T> withDbPermit(Callable<T> task) {
    return () -> {
      dbConcurrencyLimiter.acquire();
      ReadOnlyRoutingContext.setReadOnly(true);
      try {
        return task.call();
      } finally {
        ReadOnlyRoutingContext.clear();
        dbConcurrencyLimiter.release();
      }
    };
  }

  // =========================================================================
  // 5. 내부 값 객체 (Value Objects)
  // =========================================================================

  /**
   * 사용자의 프로필로부터 도출된 2단계 동료집단 완화 조회 명세.
   *
   * @param jobFamily 조회할 직무군 코드 (예: {@code DEVELOPER})
   * @param ownCollectorBrackets Level 1(본인 경력 구간)에서 조회할 수집기 6구간 브래킷 목록
   * @param ownCondition Level 1 안착 시 노출할 비교 조건 문구 (예: {@code "전국 / 4~7년 / 회사규모 무관 / 고용형태 무관"})
   * @param widenedCollectorBrackets Level 2(인접 경력 구간 확장)에서 조회할 수집기 6구간 브래킷 목록
   * @param widenedCondition Level 2 안착 시 노출할 비교 조건 문구
   */
  private record PeerGroupLadderSpec(
      String jobFamily,
      List<String> ownCollectorBrackets,
      String ownCondition,
      List<String> widenedCollectorBrackets,
      String widenedCondition) {}

  /**
   * 단일 원천에서 집계된 최근 3개월 및 이전 12개월의 동료집단·전체시장 공고 수와 비중 계산을 캡슐화하는 내부 레코드.
   *
   * @param source 원천 플랫폼 코드
   * @param fallbackLevel 적용된 완화 단계 (1: 본인 경력 구간, 2: 인접 경력 구간 확장)
   * @param appliedConditions 적용된 비교 조건 문구
   * @param recentPeerPostings 최근 3개월 동료집단 신규 수집 공고 수
   * @param baselinePeerPostings 이전 12개월(4~15개월 전) 동료집단 신규 수집 공고 수
   * @param recentMarketPostings 최근 3개월 전체 시장 신규 수집 공고 수 (분모)
   * @param baselineMarketPostings 이전 12개월 전체 시장 신규 수집 공고 수 (분모)
   * @param minSampleSize 유효 표본 최소 기준치 (기본 10)
   */
  private record SourceShareStats(
      String source,
      int fallbackLevel,
      String appliedConditions,
      int recentPeerPostings,
      int baselinePeerPostings,
      int recentMarketPostings,
      int baselineMarketPostings,
      int minSampleSize) {

    /** 최근 3개월 동료집단 표본 수가 최소 표본 기준을 충족하는지 여부 */
    boolean meetsRecentSampleThreshold() {
      return recentPeerPostings >= minSampleSize;
    }

    /** 최근 3개월과 이전 12개월 동료집단 표본 수가 모두 최소 표본 기준을 충족하는지 여부 (완화 단계 안착 조건) */
    boolean meetsBothSampleThresholds() {
      return recentPeerPostings >= minSampleSize && baselinePeerPostings >= minSampleSize;
    }

    /** 최근 3개월 비중을 산출할 수 있는지 여부 (동료집단 표본이 최소 기준 이상이고 전체시장 모수가 존재해야 함) */
    boolean usableForRecentShare() {
      return meetsRecentSampleThreshold() && recentMarketPostings > 0;
    }

    /** 이전 12개월 비중을 산출할 수 있는지 여부 (동료집단 표본이 최소 기준 이상이고 전체시장 모수가 존재해야 함) */
    boolean usableForBaselineShare() {
      return baselinePeerPostings >= minSampleSize && baselineMarketPostings > 0;
    }

    /**
     * 이 원천이 채용 활발도 지수(최근 비중 ÷ 이전 비중 × 100) 결합에 참여할 수 있는지 여부.
     *
     * <p>최근 3개월과 이전 12개월 모두 유효 표본 기준을 충족해야 합니다(이전 창 동료집단 표본이 {@code minSampleSize >= 10} 이상이므로 분모인
     * {@code baselineSharePercent()}가 0이 되는 나눗셈 오류도 원천 차단됨).
     */
    boolean usableForIndex() {
      return usableForRecentShare() && usableForBaselineShare();
    }

    /** 해당 원천의 최근 3개월 동료집단 채용 비중(%) */
    double recentSharePercent() {
      return ((double) recentPeerPostings / recentMarketPostings) * 100.0;
    }

    /** 해당 원천의 이전 12개월 동료집단 채용 비중(%) */
    double baselineSharePercent() {
      return ((double) baselinePeerPostings / baselineMarketPostings) * 100.0;
    }
  }
}
