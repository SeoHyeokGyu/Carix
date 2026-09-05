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
 * <p>[기능 명세 §4.1]에 정의된 수식을 바탕으로, 특정 스킬의 최근 3개월(1~3개월 전)간 시장 수요율과 그 이전 12개월(4~15개월 전, 비중첩 창)간의 수요율을
 * 비교하여 지수화합니다. 기준월은 어느 창에도 포함하지 않습니다.
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
 *   <li><b>스킬 행 표본 미달:</b> 기간 창의 스킬 공고 수가 {@link #MIN_SKILL_SAMPLE_SIZE} 미만이면 {@code
 *       INSUFFICIENT_SAMPLE}(표본 부족)로 처리하고 MVI 가중합에서 제외합니다 (기술 명세 §2.1). 최근 창의 0 은 여기 해당하지 않습니다 —
 *       기준선 표본이 충분한데 최근이 0 이면 표본 문제가 아니라 수요가 사라진 신호입니다.
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
          .baselineSkillPostings(baselineSkillPostings)
          .recentTotalPostings(recentTotalPostings)
          .build();
    }

    // (1-1) 스킬 행 표본 미달 -> 표본 부족 (기술 명세 §2.1)
    // 최근 창의 0 은 여기서 걸러내지 않는다. 기준선 표본이 충분한데 최근이 0 이면 표본 문제가 아니라
    // "수요가 사라졌다" 는 신호이고, 기능 명세 §4.1 이 그 경우를 별도 상태로 규정한다.
    boolean baselineUnderSampled = baselineSkillPostings < MIN_SKILL_SAMPLE_SIZE;
    boolean recentUnderSampled =
        recentSkillPostings > 0 && recentSkillPostings < MIN_SKILL_SAMPLE_SIZE;
    if (baselineUnderSampled || recentUnderSampled) {
      return SkillDemandResult.builder()
          .skillId(skillId)
          .demandIndex(0.0)
          .recentDemandRate(recentDemandRate)
          .baselineDemandRate(baselineDemandRate)
          .status("INSUFFICIENT_SAMPLE")
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
          .status("NO_RECENT_DEMAND")
          .clamped(false)
          .recentSkillPostings(0)
          .baselineSkillPostings(baselineSkillPostings)
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
    private final String status; // OK, NEW_OBSERVATION, NO_RECENT_DEMAND, INSUFFICIENT_SAMPLE
    private final boolean clamped;
    private final int recentSkillPostings;
    private final int baselineSkillPostings;
    private final int recentTotalPostings;
  }
}
