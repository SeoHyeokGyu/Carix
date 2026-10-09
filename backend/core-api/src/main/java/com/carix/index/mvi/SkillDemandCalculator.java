package com.carix.index.mvi;

import com.carix.config.IndexThresholdProperties;
import com.carix.index.enums.SkillDemandStatus;
import com.carix.market.MarketAggregate;
import com.carix.market.MarketAggregateRepository;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 스킬 수요 지수(Skill Demand Index) 산출기.
 *
 * <p>[기능 명세 §4.1]에 정의된 수식을 바탕으로, 특정 스킬의 최근 3개월(1~3개월 전)간 시장 수요율과 그 이전 12개월(4~15개월 전, 비중첩 창)간의 수요율을
 * 비교하여 지수화합니다. 기준월은 어느 창에도 포함하지 않습니다. 수요율은 해당 기간 내 신규 수집 공고(유량) 기준입니다 (데이터 아키텍처 §1.3, b21).
 *
 * <h3>수학 공식</h3>
 *
 * <p>\[ \text{스킬 수요 지수} = \frac{\text{최근 3개월 수요율}}{\text{이전 12개월 수요율}} \times 100 \]
 *
 * <p>여기서 \( \text{수요율} = \frac{\sum \text{posting\_count}}{\sum \text{total\_aggregate\_postings}}
 * \) 이며, 모수는 해당 창에 신규 수집된 동료집단 공고(유량)입니다.
 *
 * <h3>경계 조건 및 예외 처리 정책</h3>
 *
 * <ul>
 *   <li><b>분모 0 (이전 12개월 창 관측 없음):</b> 신규로 시장에 등장한 스킬이므로 {@code NEW_OBSERVATION}(신규 관측) 상태로 처리하며,
 *       MVI 가중합 산출에서 제외합니다.
 *   <li><b>분자 0 (최근 3개월 관측 없음):</b> 최근 채용 시장에서 신규 수요가 없는 스킬이므로 {@code NO_RECENT_DEMAND}(최근 수요 없음)
 *       상태로 표시하고, 수요 지수를 0으로 하여 MVI 가중합에 그대로 반영합니다. 이 판정은 최근 창의 <b>모수(신규 수집 공고)가 0보다 클 때만</b> 내립니다
 *       — 모수가 0이면 수요가 없는 것이 아니라 집계가 없는 것입니다.
 *   <li><b>최근 창 모수 0 (집계 공백):</b> 최근 신규 수집된 동료집단 공고 자체가 한 건도 없어 수요율이 정의되지 않으므로 {@code
 *       NO_RECENT_COVERAGE}(집계 없음)로 처리하고 그 스킬의 원천 결합에서 해당 원천을 뺍니다. 모든 원천이 이 상태면 MVI 는 0 이 아니라
 *       {@code NULL} 입니다. 표본 부족과 갈라 둔 것은 해소 주체가 다르기 때문입니다 — 사용자 입력으로는 해소되지 않습니다.
 *   <li><b>스킬 행 표본 미달:</b> 기간 창의 스킬 신규 공고 수가 {@link #MIN_SKILL_SAMPLE_SIZE} 미만이면 {@code
 *       INSUFFICIENT_SAMPLE}(표본 부족)로 처리하고 MVI 가중합에서 제외합니다 (기술 명세 §2.1). 최근 창의 스킬 공고 0 은 여기 해당하지
 *       않습니다 — 기준선 표본이 충분하고 최근 창에 모수가 있는데 스킬만 0 이면, 표본 문제가 아니라 수요가 사라진 신호입니다.
 *   <li><b>이상치 클램프:</b> 지수값이 지나치게 튀는 현상을 방지하기 위해 계산 결과를 [25.0, 400.0] 범위로 제한(Clamp)합니다.
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class SkillDemandCalculator {

  // TODO: [Member B] 운영 데이터 검증 후 이상치 클램프 [25, 400] 범위 조정 및 IndexThresholdProperties 주입 검토 (기능 명세
  // §4.1)
  /** 지수 최소 클램프 값 */
  public static final double MIN_CLAMP = 25.0;

  /** 지수 최대 클램프 값 */
  public static final double MAX_CLAMP = 400.0;

  /**
   * 스킬 행의 기간 창별 최소 표본 수 (기술 명세 §2.1). 원천별 × 스킬별 × 기간 창별로 판정한다.
   *
   * <p><b>이 값은 후보값이다.</b> 도메인 정의서 §2.3 의 실측으로는 가장 두꺼운 셀($N = 117$)에 붙은 스킬 239종 중 10건을 넘는 것이 9종뿐이라,
   * 이 기준을 그대로 쓰면 대부분의 스킬이 `표본 부족`으로 빠진다. 운영 데이터가 쌓이면 동료집단 셀 기준({@link
   * PeerGroupFallbackResolver#MIN_SAMPLE_SIZE})과 분리해 다시 정한다.
   */
  // TODO: [Member B] 운영 데이터 확보 후 기준값 재산정 (기술 명세 §2.1, 도메인 정의서 §2.3)
  public static final int MIN_SKILL_SAMPLE_SIZE = 10;

  private static final DateTimeFormatter YM_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");

  private final MarketAggregateRepository marketAggregateRepository;

  @Autowired(required = false)
  private IndexThresholdProperties thresholds;

  private double minClamp() {
    return thresholds == null ? MIN_CLAMP : thresholds.getSkillDemandMinClamp();
  }

  private double maxClamp() {
    return thresholds == null ? MAX_CLAMP : thresholds.getSkillDemandMaxClamp();
  }

  private int minSkillSampleSize() {
    return thresholds == null ? MIN_SKILL_SAMPLE_SIZE : thresholds.getSkillMinSampleSize();
  }

  /** 동료집단의 최근 3개월 및 이전 12개월 전체 공고 수 요약. */
  public record TotalPostings(int recentTotalPostings, int baselineTotalPostings) {}

  /**
   * 주어진 동료집단 완화 조건 및 기준월에 대해 전체 공고 수(모수)를 계산합니다.
   *
   * <p>스킬 ID와 무관한 공통 모수이므로, 스킬 루프 밖에서 1회만 조회하여 재사용할 수 있습니다.
   *
   * @param source 플랫폼 원천 (JOBKOREA, SARAMIN, WANTED, JUMPIT)
   * @param peerGroup 완화 완료된 동료집단 조건
   * @param baseYearMonth 기준년월 (YYYYMM)
   * @return 최근 3개월 및 이전 12개월 전체 공고 수
   */
  public TotalPostings calculateTotalPostings(
      String source, PeerGroupFallbackResolver.ResolvedPeerGroup peerGroup, String baseYearMonth) {
    YearMonth targetYm = parseYearMonth(baseYearMonth);

    List<String> recentMonths = getMonthsRange(targetYm.minusMonths(3), 3);
    List<String> baselineMonths = getMonthsRange(targetYm.minusMonths(15), 12);

    List<MarketAggregate> recentTotalAggs =
        marketAggregateRepository.findTotalPostingAggregates(
            source,
            recentMonths,
            peerGroup.getJobFamily(),
            peerGroup.getRegion(),
            peerGroup.getBrackets(),
            peerGroup.getEmploymentType(),
            peerGroup.getCompanySize());

    List<MarketAggregate> baselineTotalAggs =
        marketAggregateRepository.findTotalPostingAggregates(
            source,
            baselineMonths,
            peerGroup.getJobFamily(),
            peerGroup.getRegion(),
            peerGroup.getBrackets(),
            peerGroup.getEmploymentType(),
            peerGroup.getCompanySize());

    int recentTotalPostings =
        recentTotalAggs.stream().mapToInt(MarketAggregate::getPostingCount).sum();
    int baselineTotalPostings =
        baselineTotalAggs.stream().mapToInt(MarketAggregate::getPostingCount).sum();

    return new TotalPostings(recentTotalPostings, baselineTotalPostings);
  }

  /** 주어진 스킬의 수요 지수를 계산합니다 (동료집단 정보 기반 단일 호출 편의 메서드). */
  public SkillDemandResult calculate(
      UUID skillId, String baseYearMonth, PeerGroupFallbackResolver.ResolvedPeerGroup peerGroup) {
    String source =
        (peerGroup != null
                && peerGroup.getSource() != null
                && !"ALL".equalsIgnoreCase(peerGroup.getSource()))
            ? peerGroup.getSource()
            : "JOBKOREA";
    return calculateDemandIndex(source, skillId, peerGroup, baseYearMonth);
  }

  /**
   * 주어진 동료집단 완화 조건 및 기준월에 대해 특정 스킬의 수요 지수를 계산합니다.
   *
   * @param source 플랫폼 원천 (JOBKOREA, SARAMIN, WANTED, JUMPIT)
   * @param skillId 대상 스킬 ID
   * @param peerGroup 완화 완료된 동료집단 조건
   * @param baseYearMonth 기준년월 (YYYYMM)
   * @return 스킬 수요 지수 계산 결과
   */
  public SkillDemandResult calculateDemandIndex(
      String source,
      UUID skillId,
      PeerGroupFallbackResolver.ResolvedPeerGroup peerGroup,
      String baseYearMonth) {
    return calculateDemandIndex(source, skillId, peerGroup, baseYearMonth, null);
  }

  /**
   * 사전 계산된 전체 공고 수({@link TotalPostings})를 전달받아 특정 스킬의 수요 지수를 계산합니다.
   *
   * @param source 플랫폼 원천 (JOBKOREA, SARAMIN, WANTED, JUMPIT)
   * @param skillId 대상 스킬 ID
   * @param peerGroup 완화 완료된 동료집단 조건
   * @param baseYearMonth 기준년월 (YYYYMM)
   * @param totalPostings 사전 조회된 동료집단 전체 공고 수 (null인 경우 내부에서 직접 조회)
   * @return 스킬 수요 지수 계산 결과
   */
  public SkillDemandResult calculateDemandIndex(
      String source,
      UUID skillId,
      PeerGroupFallbackResolver.ResolvedPeerGroup peerGroup,
      String baseYearMonth,
      TotalPostings totalPostings) {

    TotalPostings resolvedTotals =
        (totalPostings != null)
            ? totalPostings
            : calculateTotalPostings(source, peerGroup, baseYearMonth);

    YearMonth targetYm = parseYearMonth(baseYearMonth);

    // 1. 기간 창(Window) 생성
    // NOTE: (cjkang) 기준월 자체는 두 창 어디에도 들어가지 않는다 (기능 명세 §4.1).
    // 최근 3개월(1~3개월 전): [targetYm - 3개월, targetYm - 1개월]
    List<String> recentMonths = getMonthsRange(targetYm.minusMonths(3), 3);

    // 이전 12개월(4~15개월 전, 비중첩): [targetYm - 15개월, targetYm - 4개월]
    List<String> baselineMonths = getMonthsRange(targetYm.minusMonths(15), 12);

    // 2. 최근 3개월 집계 조회
    List<MarketAggregate> recentSkillAggs =
        marketAggregateRepository.findSkillAggregates(
            source,
            recentMonths,
            peerGroup.getJobFamily(),
            peerGroup.getRegion(),
            peerGroup.getBrackets(),
            peerGroup.getEmploymentType(),
            peerGroup.getCompanySize(),
            skillId);

    int recentSkillPostings =
        recentSkillAggs.stream().mapToInt(MarketAggregate::getPostingCount).sum();
    int recentTotalPostings = resolvedTotals.recentTotalPostings();

    // 3. 이전 12개월 집계 조회
    List<MarketAggregate> baselineSkillAggs =
        marketAggregateRepository.findSkillAggregates(
            source,
            baselineMonths,
            peerGroup.getJobFamily(),
            peerGroup.getRegion(),
            peerGroup.getBrackets(),
            peerGroup.getEmploymentType(),
            peerGroup.getCompanySize(),
            skillId);

    int baselineSkillPostings =
        baselineSkillAggs.stream().mapToInt(MarketAggregate::getPostingCount).sum();
    int baselineTotalPostings = resolvedTotals.baselineTotalPostings();

    // 4. 수요율 계산
    double recentDemandRate =
        recentTotalPostings > 0 ? (double) recentSkillPostings / recentTotalPostings : 0.0;
    double baselineDemandRate =
        baselineTotalPostings > 0 ? (double) baselineSkillPostings / baselineTotalPostings : 0.0;

    // 5. 경계 조건 검사
    // (1) 분모 0: 이전 12개월 동안 스킬 공고가 전혀 없음 -> 신규 관측
    if (baselineDemandRate <= 0.0) {
      return SkillDemandResult.builder()
          .skillId(skillId)
          .demandIndex(0.0)
          .recentDemandRate(recentDemandRate)
          .baselineDemandRate(baselineDemandRate)
          .status(SkillDemandStatus.NEW_OBSERVATION)
          .clamped(false)
          .recentSkillPostings(recentSkillPostings)
          .baselineSkillPostings(baselineSkillPostings)
          .recentTotalPostings(recentTotalPostings)
          .build();
    }

    // (1-0) 최근 창에 동료집단 공고 집계 자체가 없다 -> 수요율이 0/0 이라 판정 불가.
    // 표본 부족과 갈라 둔다. 둘 다 가중합에서 빠지지만 해소 주체가 다르다 — 이쪽은 시장 집계가
    // 수집되기 전까지 사용자가 무엇을 입력해도 달라지지 않는다.
    if (recentTotalPostings == 0) {
      return SkillDemandResult.builder()
          .skillId(skillId)
          .demandIndex(0.0)
          .recentDemandRate(recentDemandRate)
          .baselineDemandRate(baselineDemandRate)
          .status(SkillDemandStatus.NO_RECENT_COVERAGE)
          .clamped(false)
          .recentSkillPostings(recentSkillPostings)
          .baselineSkillPostings(baselineSkillPostings)
          .recentTotalPostings(recentTotalPostings)
          .build();
    }

    // (1-1) 스킬 행 표본 미달 -> 표본 부족 (기술 명세 §2.1)
    // 최근 창의 스킬 0 은 여기서 걸러내지 않는다. 기준선 표본이 충분한데 최근이 0 이면 표본 문제가 아니라
    // "수요가 사라졌다" 는 신호이고, 기능 명세 §4.1 이 그 경우를 별도 상태로 규정한다.
    // 그 추론은 최근 창에 모수가 있을 때만 성립하는데, 모수 0 은 위 (1-0) 에서 이미 빠졌다.
    int minSampleSize = minSkillSampleSize();
    boolean baselineUnderSampled = baselineSkillPostings < minSampleSize;
    boolean recentUnderSampled = recentSkillPostings > 0 && recentSkillPostings < minSampleSize;
    if (baselineUnderSampled || recentUnderSampled) {
      return SkillDemandResult.builder()
          .skillId(skillId)
          .demandIndex(0.0)
          .recentDemandRate(recentDemandRate)
          .baselineDemandRate(baselineDemandRate)
          .status(SkillDemandStatus.INSUFFICIENT_SAMPLE)
          .clamped(false)
          .recentSkillPostings(recentSkillPostings)
          .baselineSkillPostings(baselineSkillPostings)
          .recentTotalPostings(recentTotalPostings)
          .build();
    }

    // (2) 분자 0: 최근 3개월 동안 스킬 공고가 없음 -> 최근 수요 없음 (지수 0으로 가중합 반영)
    if (recentDemandRate <= 0.0) {
      return SkillDemandResult.builder()
          .skillId(skillId)
          .demandIndex(0.0)
          .recentDemandRate(0.0)
          .baselineDemandRate(baselineDemandRate)
          .status(SkillDemandStatus.NO_RECENT_DEMAND)
          .clamped(false)
          .recentSkillPostings(0)
          .baselineSkillPostings(baselineSkillPostings)
          .recentTotalPostings(recentTotalPostings)
          .build();
    }

    // (3) 일반 계산: (최근 3개월 수요율 / 이전 12개월 수요율) * 100
    double rawIndex = (recentDemandRate / baselineDemandRate) * 100.0;

    // (4) 이상치 클램프 [25.0, 400.0]
    double clampedIndex = Math.min(maxClamp(), Math.max(minClamp(), rawIndex));
    boolean isClamped = (rawIndex != clampedIndex);

    return SkillDemandResult.builder()
        .skillId(skillId)
        .demandIndex(clampedIndex)
        .recentDemandRate(recentDemandRate)
        .baselineDemandRate(baselineDemandRate)
        .status(SkillDemandStatus.OK)
        .clamped(isClamped)
        .recentSkillPostings(recentSkillPostings)
        .baselineSkillPostings(baselineSkillPostings)
        .recentTotalPostings(recentTotalPostings)
        .build();
  }

  /** 지정된 시작월부터 연속된 n개월의 년월 문자열(YYYYMM) 목록을 생성합니다. */
  public List<String> getMonthsRange(YearMonth startYm, int count) {
    List<String> months = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      months.add(startYm.plusMonths(i).format(YM_FORMATTER));
    }
    return months;
  }

  private YearMonth parseYearMonth(String baseYearMonth) {
    if (baseYearMonth == null || baseYearMonth.length() != 6) {
      return YearMonth.now();
    }
    try {
      int year = Integer.parseInt(baseYearMonth.substring(0, 4));
      int month = Integer.parseInt(baseYearMonth.substring(4, 6));
      if (month < 1 || month > 12) {
        return YearMonth.now();
      }
      return YearMonth.of(year, month);
    } catch (Exception e) {
      return YearMonth.now();
    }
  }

  /** 스킬 수요 지수 산출 결과 DTO. */
  @Getter
  @Builder
  public static class SkillDemandResult {
    private final UUID skillId;
    private final double demandIndex;
    private final double recentDemandRate;
    private final double baselineDemandRate;
    private final SkillDemandStatus status;
    private final boolean clamped;
    private final int recentSkillPostings;
    private final int baselineSkillPostings;
    private final int recentTotalPostings;
  }
}
