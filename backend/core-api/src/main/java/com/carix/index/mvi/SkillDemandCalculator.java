package com.carix.index.mvi;

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
import org.springframework.stereotype.Component;

/**
 * 스킬 수요 지수(Skill Demand Index) 산출기.
 *
 * <p>[기능 명세 §4.1]에 정의된 수식을 바탕으로, 특정 스킬의 최근 3개월간 시장 수요율과 그 이전 12개월(4~15개월 전, 비중첩 창)간의 수요율을 비교하여
 * 지수화합니다.
 *
 * <h3>수학 공식</h3>
 *
 * <p>\[ \text{스킬 수요 지수} = \frac{\text{최근 3개월 수요율}}{\text{이전 12개월 수요율}} \times 100 \]
 *
 * <p>여기서 \( \text{수요율} = \frac{\sum \text{posting\_count}}{\sum \text{total\_aggregate\_postings}}
 * \)
 *
 * <h3>경계 조건 및 예외 처리 정책</h3>
 *
 * <ul>
 *   <li><b>분모 0 (이전 12개월 창 관측 없음):</b> 신규로 시장에 등장한 스킬이므로 {@code NEW_OBSERVATION}(신규 관측) 상태로 처리하며,
 *       MVI 가중합 산출에서 제외합니다.
 *   <li><b>분자 0 (최근 3개월 관측 없음):</b> 최근 채용 시장에서 수요가 없는 스킬이므로 {@code NO_RECENT_DEMAND}(최근 수요 없음) 상태로
 *       표시하고, 수요 지수를 0으로 하여 MVI 가중합에 그대로 반영합니다.
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

  private static final DateTimeFormatter YM_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");

  private final MarketAggregateRepository marketAggregateRepository;

  /**
   * 주어진 동료집단 완화 조건 및 기준월에 대해 특정 스킬의 수요 지수를 계산합니다.
   *
   * @param source 플랫폼 원천 (JOBKOREA, SARAMIN)
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

    YearMonth targetYm = parseYearMonth(baseYearMonth);

    // 1. 기간 창(Window) 생성
    // 최근 3개월: [targetYm - 2개월, targetYm]
    List<String> recentMonths = getMonthsRange(targetYm.minusMonths(2), 3);

    // 이전 12개월(4~15개월 전, 비중첩): [targetYm - 14개월, targetYm - 3개월]
    List<String> baselineMonths = getMonthsRange(targetYm.minusMonths(14), 12);

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

    List<MarketAggregate> recentTotalAggs =
        marketAggregateRepository.findTotalPostingAggregates(
            source,
            recentMonths,
            peerGroup.getJobFamily(),
            peerGroup.getRegion(),
            peerGroup.getBrackets(),
            peerGroup.getEmploymentType(),
            peerGroup.getCompanySize());

    int recentSkillPostings =
        recentSkillAggs.stream().mapToInt(MarketAggregate::getPostingCount).sum();
    int recentTotalPostings =
        recentTotalAggs.stream().mapToInt(MarketAggregate::getPostingCount).sum();

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

    List<MarketAggregate> baselineTotalAggs =
        marketAggregateRepository.findTotalPostingAggregates(
            source,
            baselineMonths,
            peerGroup.getJobFamily(),
            peerGroup.getRegion(),
            peerGroup.getBrackets(),
            peerGroup.getEmploymentType(),
            peerGroup.getCompanySize());

    int baselineSkillPostings =
        baselineSkillAggs.stream().mapToInt(MarketAggregate::getPostingCount).sum();
    int baselineTotalPostings =
        baselineTotalAggs.stream().mapToInt(MarketAggregate::getPostingCount).sum();

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
          .status("NEW_OBSERVATION")
          .clamped(false)
          .recentSkillPostings(recentSkillPostings)
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
          .status("NO_RECENT_DEMAND")
          .clamped(false)
          .recentSkillPostings(0)
          .recentTotalPostings(recentTotalPostings)
          .build();
    }

    // (3) 일반 계산: (최근 3개월 수요율 / 이전 12개월 수요율) * 100
    double rawIndex = (recentDemandRate / baselineDemandRate) * 100.0;

    // (4) 이상치 클램프 [25.0, 400.0]
    double clampedIndex = Math.min(MAX_CLAMP, Math.max(MIN_CLAMP, rawIndex));
    boolean isClamped = (rawIndex != clampedIndex);

    return SkillDemandResult.builder()
        .skillId(skillId)
        .demandIndex(clampedIndex)
        .recentDemandRate(recentDemandRate)
        .baselineDemandRate(baselineDemandRate)
        .status("OK")
        .clamped(isClamped)
        .recentSkillPostings(recentSkillPostings)
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
    int year = Integer.parseInt(baseYearMonth.substring(0, 4));
    int month = Integer.parseInt(baseYearMonth.substring(4, 6));
    return YearMonth.of(year, month);
  }

  /** 스킬 수요 지수 산출 결과 DTO. */
  @Getter
  @Builder
  public static class SkillDemandResult {
    private final UUID skillId;
    private final double demandIndex;
    private final double recentDemandRate;
    private final double baselineDemandRate;
    private final String status; // OK, NEW_OBSERVATION, NO_RECENT_DEMAND
    private final boolean clamped;
    private final int recentSkillPostings;
    private final int recentTotalPostings;
  }
}
