package com.carix.index.mvi;

import com.carix.skill.domain.UserSkill;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 사용자 스킬 가중치(W_skill) 산출기.
 *
 * <p>[도메인 정의서 §3]에 정의된 공식을 바탕으로, 사용자가 보유한 각 스킬의 실무 경력(Months)과 최근성(Recency)을 반영하여 상대적 중요도 가중치를
 * 분배합니다.
 *
 * <h3>수학 공식 요약</h3>
 *
 * <ol>
 *   <li><b>경력 기간 요인:</b> \( F_{dur}(E_i) = \ln(E_i + 1) \) <br>
 *       경력이 길어질수록 숙련도가 상승하나, 한계효용 체감을 반영하기 위해 자연로그 적용.
 *   <li><b>최근성 감쇄 요인:</b> \( F_{rec}(R_i) = \frac{1}{1 + \alpha \cdot R_i} \) (\(\alpha = 0.1\))
 *       <br>
 *       사용하지 않은 지 오래될수록 망각 및 시장 트렌드 격차를 반영하는 쌍곡선형 감쇄.
 *   <li><b>스킬별 원천 점수:</b> \( S_i = F_{dur}(E_i) \times F_{rec}(R_i) = \ln(E_i + 1) \times
 *       \frac{1}{1 + 0.1 \cdot R_i} \)
 *   <li><b>정규화 가중치:</b> \( W_i = \frac{S_i}{\sum_{j \in \text{유효}} S_j} \) (\(\sum W_i = 1.0\))
 *       <br>
 *       제외된 스킬을 제외한 유효 스킬들의 점수 총합으로 나누어 가중치 합을 1(100%)로 재정규화.
 * </ol>
 */
@Component
public class SkillWeightCalculator {

  /** 최근성 감쇄 계수 (\(\alpha = 0.1\)) */
  private static final double ALPHA = 0.1;

  /**
   * 사용자의 스킬 목록과 기준 년월에 대해, 유효 스킬들의 정규화된 가중치 맵을 계산합니다.
   *
   * @param userSkills 사용자의 보유 스킬 엔티티 목록
   * @param baseYearMonth 기준년월 (YYYYMM)
   * @param excludedSkillIds 계산에서 제외할 스킬 ID 집합 (사용자 정의 스킬, 통계 미제공 스킬 등)
   * @return 스킬 ID -> 정규화된 가중치(BigDecimal, 소수점 4자리 반올림) 매핑
   */
  public Map<UUID, BigDecimal> calculateWeights(
      List<UserSkill> userSkills, String baseYearMonth, Set<UUID> excludedSkillIds) {
    if (userSkills == null || userSkills.isEmpty()) {
      return Collections.emptyMap();
    }

    YearMonth targetYm = parseYearMonth(baseYearMonth);
    Map<UUID, Double> rawScores = new HashMap<>();
    double totalScore = 0.0;

    for (UserSkill us : userSkills) {
      UUID skillId = us.getSkill().getId();

      // 제외 대상 스킬은 가중치 산출 대상에서 건너뜀 (도메인 정의서 §4.2)
      if (excludedSkillIds != null && excludedSkillIds.contains(skillId)) {
        continue;
      }

      // 1. 실무 경력 개월 수 (E_i)
      int durationMonths = us.getMonthsOfExperience();

      // 2. 최종 사용일로부터 경과된 개월 수 (R_i)
      long recencyMonths = calculateMonthsPassed(us.getLastUsedDate(), targetYm);

      // 3. 원천 점수 S_i = ln(E_i + 1) * (1 / (1 + 0.1 * R_i))
      double score = calculateRawScore(durationMonths, recencyMonths);
      rawScores.put(skillId, score);
      totalScore += score;
    }

    // 유효 스킬이 없거나 점수 총합이 0인 경우 빈 맵 반환
    if (rawScores.isEmpty() || totalScore <= 0.0) {
      return Collections.emptyMap();
    }

    // 4. 가중치 정규화 (W_i = S_i / TotalScore)
    Map<UUID, BigDecimal> normalizedWeights = new HashMap<>();
    for (Map.Entry<UUID, Double> entry : rawScores.entrySet()) {
      double weight = entry.getValue() / totalScore;
      BigDecimal roundedWeight = BigDecimal.valueOf(weight).setScale(4, RoundingMode.HALF_UP);
      normalizedWeights.put(entry.getKey(), roundedWeight);
    }

    return normalizedWeights;
  }

  /**
   * 단일 스킬에 대한 원천 점수(Raw Score, S_i)를 산출합니다.
   *
   * @param durationMonths 실무 사용 경력 (개월 수, 1 ~ 600)
   * @param recencyMonths 마지막 사용 후 경과 기간 (개월 수, >= 0)
   * @return 스킬 숙련도 점수 S_i
   */
  public double calculateRawScore(int durationMonths, long recencyMonths) {
    // F_dur(E_i) = ln(E_i + 1)
    double fDur = Math.log(Math.max(1, durationMonths) + 1.0);

    // F_rec(R_i) = 1 / (1 + 0.1 * R_i)
    double nonNegativeRecency = Math.max(0, recencyMonths);
    double fRec = 1.0 / (1.0 + ALPHA * nonNegativeRecency);

    return fDur * fRec;
  }

  /**
   * 마지막 사용일과 기준 년월 사이의 경과 개월 수를 계산합니다.
   *
   * <p>마지막 사용일이 기준 년월 당월이거나 미래인 경우 경과 개월 수는 0으로 처리합니다.
   */
  public long calculateMonthsPassed(LocalDate lastUsedDate, YearMonth baseYearMonth) {
    if (lastUsedDate == null) {
      return 0;
    }
    YearMonth lastUsedYm = YearMonth.from(lastUsedDate);
    long monthsBetween = ChronoUnit.MONTHS.between(lastUsedYm, baseYearMonth);
    return Math.max(0, monthsBetween);
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
}
