package com.carix.index.owi;

import com.carix.config.datasource.ReadOnlyRoutingContext;
import com.carix.index.OpeningWindowIndexProvider;
import com.carix.index.dto.OwiResult;
import com.carix.index.enums.IndexStatus;
import com.carix.index.mvi.PeerGroupFallbackResolver;
import com.carix.jobposting.JobPosting;
import com.carix.jobposting.JobPostingRepository;
import com.carix.profile.domain.CareerProfile;
import com.carix.profile.repository.CareerProfileRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 개방창 지수(Opening Window Index, OWI) 산출 서비스 구현체.
 *
 * <p><b>도메인 배경 및 목적 (기능 명세 §4.5, 개발 계획 §3 b6):</b><br>
 * 개방창 지수(OWI)는 사용자의 동료집단(Peer Group)이 속한 채용 시장의 <b>공고 모집 기간(개방창, window_days)</b>이 전체 시장 대비 얼마나 길거나
 * 짧은지를 상대적으로 나타내는 시장 속도 지표입니다.
 *
 * <ul>
 *   <li><b>지수 100 기준:</b> 동료집단의 중앙 개방일수가 전체 시장의 중앙 개방일수와 동일함을 의미합니다.
 *   <li><b>지수 &gt; 100 (긴 개방창):</b> 모집 기간이 시장 평균보다 길며, 지원 기회가 상대적으로 여유롭거나 상시성 채용 성향이 높음을 시사합니다.
 *   <li><b>지수 &lt; 100 (짧은 개방창):</b> 모집 기간이 시장 평균보다 짧으며, 마감이 빠르게 마감되는 집중 채용 경향을 시사합니다.
 * </ul>
 *
 * <p><b>핵심 산출 알고리즘 및 규칙:</b>
 *
 * <ol>
 *   <li><b>등록월 버킷팅:</b> 공고의 등록일({@code posted_start_date}) 기준월을 기준으로 당월에 열린 채용 기회를 집계합니다.
 *   <li><b>원천 공고 직접 집계:</b> {@code market_aggregate} 통계 테이블이 아닌 {@code job_posting} 원천 테이블을 직접 조회하여
 *       개별 공고의 마감일 보유 여부와 상시채용 여부를 정밀 판정합니다.
 *   <li><b>대표 공고 중복 제거:</b> 여러 플랫폼에 동일 게시된 공고는 {@code dedup_group_id}(없으면 공고 {@code id})를 기준으로 중복을
 *       제거하여 표본 왜곡을 방지합니다.
 *   <li><b>데이터 이상치/원천 오류 필터링:</b> 마감일이 등록일보다 앞선 역전 공고({@code isReversed()})는 원천 결측/오류로 판단하여 표본에서
 *       완전히 배제합니다.
 *   <li><b>동료집단 5단계 완화 사다리:</b> 표본 부족 시 연봉 분포 완화 규칙과 동일하게 조건을 완화(Level 1 ~ Level 5)합니다.
 *   <li><b>마감일 보유 표본 안착 기준:</b> 상시채용 공고는 개방일수가 없으므로, 안착 판정의 최소 표본({@code N >= 10})은 반드시 <b>마감일을 보유한
 *       공고 수({@code deadlineSampleSize})</b>에 적용됩니다.
 *   <li><b>상시채용 비율 산출:</b> 동료집단 전체 유효 표본 중 마감일이 없는 상시채용 공고({@code isOpenEnded()})의 비율(%)을 별도로 계산하여
 *       표본 부족 시에도 시장 인사이트를 보존합니다.
 *   <li><b>이상치 클램핑:</b> 극단값으로 인한 지수 왜곡을 방지하기 위해 산출 결과는 {@code [25.00, 400.00]} 범위로 제한합니다.
 *   <li><b>성능 최적화:</b> Java 21+ Virtual Thread({@code var executor =
 *       Executors.newVirtualThreadPerTaskExecutor()})를 통해 전체 시장 집계와 동료집단 완화 조회를 비동기 병렬로 처리하며, 월별 전체
 *       시장 중앙값은 {@link ConcurrentHashMap}에 인메모리 캐싱하여 중복 DB I/O를 방지합니다.
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpeningWindowIndexServiceImpl implements OpeningWindowIndexProvider {

  /** 지수 하한 클램프 값 (기능 명세 §4.5) */
  private static final BigDecimal MIN_CLAMP = new BigDecimal("25.00");

  /** 지수 상한 클램프 값 (기능 명세 §4.5) */
  private static final BigDecimal MAX_CLAMP = new BigDecimal("400.00");

  /** 기준년월 파싱 포맷터 (YYYYMM) */
  private static final DateTimeFormatter YM_FORMATTER = DateTimeFormatter.ofPattern("uuuuMM");

  /** 기준년월(YYYYMM)별 전체 시장 중앙 개방일수 캐시 */
  private final Map<String, Optional<Integer>> marketMedianCache = new ConcurrentHashMap<>();

  private final CareerProfileRepository careerProfileRepository;
  private final JobPostingRepository jobPostingRepository;
  private final PeerGroupFallbackResolver peerGroupFallbackResolver;

  /**
   * 지정된 사용자와 기준년월에 대해 개방창 지수(OWI)를 계산합니다.
   *
   * @param userId 사용자 식별자
   * @param baseYearMonth 기준년월 (YYYYMM 또는 YYYY-MM)
   * @return 개방창 지수 산출 결과 {@link OwiResult}
   */
  @Override
  @Transactional(readOnly = true)
  public OwiResult calculate(UUID userId, String baseYearMonth) {
    if (baseYearMonth == null || baseYearMonth.isBlank()) {
      return OwiResult.notAvailable(baseYearMonth);
    }

    String normalizedYm = baseYearMonth.replace("-", "").trim();
    YearMonth ym;
    try {
      ym = YearMonth.parse(normalizedYm, YM_FORMATTER);
    } catch (DateTimeParseException e) {
      log.warn("Invalid baseYearMonth format for OWI: {}", baseYearMonth);
      return OwiResult.notAvailable(baseYearMonth);
    }

    Optional<CareerProfile> profileOpt = careerProfileRepository.findByUserId(userId);
    if (profileOpt.isEmpty()) {
      return OwiResult.notAvailable(normalizedYm);
    }

    CareerProfile profile = profileOpt.get();
    LocalDate monthStart = ym.atDay(1);
    LocalDate monthEnd = ym.atEndOfMonth();

    Integer marketMedianWindowDays;
    ResolvedPeerGroup peerGroup;

    // 가상 스레드(Virtual Thread) 기반 비동기 병렬 I/O 처리
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var marketFuture =
          executor.submit(
              ReadOnlyRoutingContext.withReadOnlyCallable(
                  () -> getOrComputeMarketMedian(normalizedYm, monthStart, monthEnd)));
      var peerGroupFuture =
          executor.submit(
              ReadOnlyRoutingContext.withReadOnlyCallable(
                  () -> resolvePeerGroup(profile, monthStart, monthEnd)));

      marketMedianWindowDays = marketFuture.get();
      peerGroup = peerGroupFuture.get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("OWI calculation interrupted", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("OWI calculation failed in virtual thread", e.getCause());
    }

    int peerSampleSize = peerGroup.peerSampleSize();
    int deadlineSampleSize = peerGroup.deadlineSampleSize();
    BigDecimal openEndedRate = calculateOpenEndedRate(peerGroup.openEndedCount(), peerSampleSize);

    // 3. 표본 부족 검증: 마감일 보유 표본이 10건 미만이거나 전체 시장 모수가 없으면 지수 산출 불가
    if (deadlineSampleSize < PeerGroupFallbackResolver.MIN_SAMPLE_SIZE
        || marketMedianWindowDays == null
        || marketMedianWindowDays <= 0) {
      return new OwiResult(
          normalizedYm,
          null,
          IndexStatus.INSUFFICIENT_SAMPLE,
          peerSampleSize,
          deadlineSampleSize,
          null,
          openEndedRate,
          peerGroup.level(),
          peerGroup.appliedConditions(),
          marketMedianWindowDays);
    }

    // 4. OWI 정상 산출: (동료집단 중앙일수 / 전체시장 중앙일수) * 100, 소수점 둘째 자리 반올림 및 [25, 400] 클램프
    int peerMedianWindowDays = calculateMedian(peerGroup.deadlineWindowDays());
    BigDecimal rawValue =
        BigDecimal.valueOf(peerMedianWindowDays)
            .multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(marketMedianWindowDays), 2, RoundingMode.HALF_UP);
    BigDecimal clampedValue = clamp(rawValue);

    return new OwiResult(
        normalizedYm,
        clampedValue,
        IndexStatus.OK,
        peerSampleSize,
        deadlineSampleSize,
        peerMedianWindowDays,
        openEndedRate,
        peerGroup.level(),
        peerGroup.appliedConditions(),
        marketMedianWindowDays);
  }

  /**
   * 기준월의 전체 시장 중앙 개방일수를 캐시에서 조회하거나 계산합니다.
   *
   * <p>ConcurrentHashMap 에 캐시된 값이 없으면 가상 스레드 안에서 {@link #calculateMarketMedianWindowDays}를 수행하여
   * 저장하고, 계산 불가인 경우 빈 Optional 을 캐싱하여 불필요한 재조회를 방지합니다.
   */
  private Integer getOrComputeMarketMedian(
      String normalizedYm, LocalDate monthStart, LocalDate monthEnd) {
    return marketMedianCache
        .computeIfAbsent(
            normalizedYm,
            k -> Optional.ofNullable(calculateMarketMedianWindowDays(monthStart, monthEnd)))
        .orElse(null);
  }

  /** 전체 시장 중앙 개방일수 캐시를 비웁니다. 공고 수집 배치가 완료되거나 테스트 격리가 필요한 경우 호출합니다. */
  public void clearMarketMedianCache() {
    marketMedianCache.clear();
  }

  // =========================================================================
  // 동료집단 완화 사다리 (Fallback Ladder)
  // =========================================================================

  /** 사용자 커리어 프로필과 기준월을 바탕으로 5단계 완화 사다리를 순차 탐색하여 마감일 표본 N >= 10을 충족하는 첫 번째 동료집단을 반환합니다. */
  private ResolvedPeerGroup resolvePeerGroup(
      CareerProfile profile, LocalDate monthStart, LocalDate monthEnd) {
    PeerGroupSearchContext ctx = buildSearchContext(profile, monthStart, monthEnd);
    List<ResolvedPeerGroup> ladder = new ArrayList<>();

    // Level 1: 완전 일치 (직무 + 상세구 + 경력구간 + 고용형태 + 회사규모)
    if (ctx.hasCompanySize()) {
      ladder.add(
          attempt(
              ctx,
              1,
              ctx.detailRegion(),
              ctx.baseRange(),
              ctx.companySize(),
              ctx.detailRegion(),
              ctx.baseBracket(),
              ctx.employmentType()));
    }

    // Level 2: 회사규모 해제 (직무 + 상세구 + 경력구간 + 고용형태)
    ladder.add(
        attempt(
            ctx,
            2,
            ctx.detailRegion(),
            ctx.baseRange(),
            null,
            ctx.detailRegion(),
            ctx.baseBracket(),
            ctx.employmentType()));

    // Level 3: 지역 확장 (직무 + 광역 시/도 + 경력구간 + 고용형태)
    ladder.add(
        attempt(
            ctx,
            3,
            ctx.broadRegionPrefix(),
            ctx.baseRange(),
            null,
            ctx.broadRegionPrefix(),
            ctx.baseBracket(),
            ctx.employmentType()));

    // Level 4: 경력구간 확장 (직무 + 광역 시/도 + 인접 경력구간 ±1 + 고용형태)
    ladder.add(
        attempt(
            ctx,
            4,
            ctx.broadRegionPrefix(),
            ctx.widenedRange(),
            null,
            ctx.broadRegionPrefix(),
            ctx.baseBracket() + "±1",
            ctx.employmentType()));

    // Level 5: 고용형태 해제 (직무 + 광역 시/도 + 인접 경력구간 ±1)
    ladder.add(
        attempt(
            ctx,
            5,
            ctx.broadRegionPrefix(),
            ctx.widenedRange(),
            null,
            ctx.broadRegionPrefix(),
            ctx.baseBracket() + "±1",
            null));

    // 마감일 표본 수 N >= 10을 충족하는 첫 단계로 안착
    for (ResolvedPeerGroup candidate : ladder) {
      if (candidate.deadlineSampleSize() >= PeerGroupFallbackResolver.MIN_SAMPLE_SIZE) {
        return candidate;
      }
    }

    // 충족 단계가 없으면 가장 완화된 마지막 단계 반환
    return ladder.get(ladder.size() - 1);
  }

  /** 완화 단계별 조건으로 공고를 조회하고 집계 통계를 도출합니다. */
  private ResolvedPeerGroup attempt(
      PeerGroupSearchContext ctx,
      int level,
      String queryRegionPrefix,
      int[] yearRange,
      String queryCompanySize,
      String displayRegionLabel,
      String displayBracketLabel,
      String queryEmploymentType) {

    List<JobPosting> candidates =
        jobPostingRepository.findPeerGroupCandidatesByRegistrationMonth(
            ctx.jobFamily(),
            queryRegionPrefix,
            queryEmploymentType,
            queryCompanySize,
            yearRange[0],
            yearRange[1],
            ctx.monthStart(),
            ctx.monthEnd());

    PostingWindowStats stats = extractPostingWindowStats(candidates);

    String condition =
        "%s / 경력 %s / %s"
            .formatted(displayRegionLabel, displayBracketLabel, companySizeLabel(queryCompanySize));

    return new ResolvedPeerGroup(
        level,
        stats.validPeerCount(),
        stats.deadlineSampleSize(),
        stats.openEndedCount(),
        stats.deadlineWindowDays(),
        condition);
  }

  /** 사용자 프로필로부터 완화 탐색에 필요한 기준 파라미터들을 준비합니다. */
  private PeerGroupSearchContext buildSearchContext(
      CareerProfile profile, LocalDate monthStart, LocalDate monthEnd) {
    String jobFamily = orDefault(profile.getJobFamily(), "DEVELOPER");
    int years = profile.getYearsOfExperience() != null ? profile.getYearsOfExperience() : 3;
    String bracket = peerGroupFallbackResolver.mapExperienceBracket(years);
    String detailRegion = orDefault(profile.getRegion(), "서울 강남구");
    String broadPrefix = broadRegionPrefix(detailRegion);
    String employmentType = orDefault(profile.getEmploymentType(), "FULL_TIME");
    String companySize = profile.getCompanySize();

    int[] baseRange = bracketYearRange(bracket);
    int[] widenedRange = bracketYearRange(peerGroupFallbackResolver.getAdjacentBrackets(bracket));

    return new PeerGroupSearchContext(
        jobFamily,
        detailRegion,
        broadPrefix,
        employmentType,
        companySize,
        baseRange,
        widenedRange,
        bracket,
        monthStart,
        monthEnd);
  }

  // =========================================================================
  // 공통 공고 통계 추출 및 수학적 헬퍼
  // =========================================================================

  /** 당월 전체 시장의 등록 공고를 기준으로 중앙 개방일수(분모)를 산출합니다. */
  private Integer calculateMarketMedianWindowDays(LocalDate monthStart, LocalDate monthEnd) {
    List<JobPosting> allPostings =
        jobPostingRepository.findAllByRegistrationMonth(monthStart, monthEnd);
    PostingWindowStats stats = extractPostingWindowStats(allPostings);

    if (stats.deadlineWindowDays().isEmpty()) {
      return null;
    }
    return calculateMedian(stats.deadlineWindowDays());
  }

  /** 공고 리스트에서 중복 공고를 제거하고, 원천 오류(역전 공고)를 필터링한 후 유효 공고 수, 마감일 개방일수 목록, 상시채용 공고 수를 한 번에 집계합니다. */
  private PostingWindowStats extractPostingWindowStats(List<JobPosting> postings) {
    List<JobPosting> deduped = dedupe(postings);
    List<Integer> deadlineWindowDays = new ArrayList<>();
    int openEndedCount = 0;
    int validPeerCount = 0;

    for (JobPosting p : deduped) {
      if (p.isReversed()) {
        // 등록일이 마감일보다 늦은 역전 공고는 원천 수집 오류이므로 표본에서 배제
        continue;
      }
      validPeerCount++;
      Integer days = p.windowDays();
      if (days != null) {
        deadlineWindowDays.add(days);
      } else if (p.isOpenEnded()) {
        openEndedCount++;
      }
    }

    return new PostingWindowStats(validPeerCount, deadlineWindowDays, openEndedCount);
  }

  /**
   * 중복 공고 제거 (대표 공고 선별).
   *
   * <p>{@code dedupKey()}(중복 그룹 ID 또는 단일 ID)를 기준으로 최초 1건만 유지합니다.
   */
  private List<JobPosting> dedupe(List<JobPosting> postings) {
    Map<UUID, JobPosting> byGroup = new LinkedHashMap<>();
    for (JobPosting posting : postings) {
      byGroup.putIfAbsent(posting.dedupKey(), posting);
    }
    return new ArrayList<>(byGroup.values());
  }

  /**
   * 중앙값(Median) 산출.
   *
   * <p>짝수 개인 경우에도 내림차순/올림차순 모호성을 없애기 위해 기술 명세에 따라 {@code floor(size * 0.5)} 인덱스 값을 선택합니다.
   */
  private int calculateMedian(List<Integer> days) {
    List<Integer> sorted = new ArrayList<>(days);
    Collections.sort(sorted);
    return sorted.get((int) Math.floor(sorted.size() * 0.50));
  }

  /** 상시채용 공고 비율(%): (상시채용 공고 수 / 전체 유효 표본 수) * 100 */
  private BigDecimal calculateOpenEndedRate(int openEndedCount, int peerSampleSize) {
    if (peerSampleSize <= 0) {
      return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
    return BigDecimal.valueOf(openEndedCount)
        .multiply(BigDecimal.valueOf(100))
        .divide(BigDecimal.valueOf(peerSampleSize), 2, RoundingMode.HALF_UP);
  }

  /** 이상치 클램핑: 산출 지수값을 [25.00, 400.00] 범위로 제한 */
  private BigDecimal clamp(BigDecimal value) {
    if (value.compareTo(MIN_CLAMP) < 0) {
      return MIN_CLAMP;
    }
    if (value.compareTo(MAX_CLAMP) > 0) {
      return MAX_CLAMP;
    }
    return value;
  }

  /** 경력 브래킷명에 대응하는 최소/최대 연차 범위 반환 */
  private int[] bracketYearRange(String bracket) {
    return switch (bracket) {
      case "JUNIOR" -> new int[] {0, 3};
      case "SENIOR" -> new int[] {8, 99};
      default -> new int[] {4, 7};
    };
  }

  /** 복수 경력 브래킷(인접 확장)을 포괄하는 최소/최대 연차 범위 반환 */
  private int[] bracketYearRange(List<String> brackets) {
    int min = Integer.MAX_VALUE;
    int max = Integer.MIN_VALUE;
    for (String bracket : brackets) {
      int[] range = bracketYearRange(bracket);
      min = Math.min(min, range[0]);
      max = Math.max(max, range[1]);
    }
    return new int[] {min, max};
  }

  /** 상세 구 지역명에서 시/도 광역 prefix 추출 (예: "서울 강남구" -> "서울") */
  private String broadRegionPrefix(String detailRegion) {
    String trimmed = detailRegion.trim();
    int space = trimmed.indexOf(' ');
    return space > 0 ? trimmed.substring(0, space) : trimmed;
  }

  private String companySizeLabel(String companySize) {
    return companySize == null ? "회사규모 무관" : companySize;
  }

  private String orDefault(String value, String fallback) {
    return (value != null && !value.isBlank()) ? value : fallback;
  }

  // =========================================================================
  // 내부 불변 레코드 (Value Objects)
  // =========================================================================

  /** 동료집단 완화 탐색 컨텍스트 */
  private record PeerGroupSearchContext(
      String jobFamily,
      String detailRegion,
      String broadRegionPrefix,
      String employmentType,
      String companySize,
      int[] baseRange,
      int[] widenedRange,
      String baseBracket,
      LocalDate monthStart,
      LocalDate monthEnd) {
    boolean hasCompanySize() {
      return companySize != null && !companySize.isBlank();
    }
  }

  /** 공고 목록 집계 통계 결과 */
  private record PostingWindowStats(
      int validPeerCount, List<Integer> deadlineWindowDays, int openEndedCount) {
    int deadlineSampleSize() {
      return deadlineWindowDays.size();
    }
  }

  /** 동료집단 완화 단계 확정 결과 */
  private record ResolvedPeerGroup(
      int level,
      int peerSampleSize,
      int deadlineSampleSize,
      int openEndedCount,
      List<Integer> deadlineWindowDays,
      String appliedConditions) {}
}
