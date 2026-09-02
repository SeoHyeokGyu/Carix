package com.carix.index.mvi;

import com.carix.index.ExcludedSkill;
import com.carix.index.IndexStatus;
import com.carix.index.MarketValueIndexProvider;
import com.carix.index.MviResult;
import com.carix.index.SkillExclusionReason;
import com.carix.index.SourceCoverage;
import com.carix.index.WageBaseline;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
 */
@Slf4j
@Service
@Primary
@RequiredArgsConstructor
public class MarketValueIndexServiceImpl implements MarketValueIndexProvider {

  /** 지원하는 채용공고 원천 플랫폼 목록 */
  public static final List<String> SUPPORTED_SOURCES = List.of("JOBKOREA", "SARAMIN");

  private final CareerProfileRepository careerProfileRepository;
  private final UserSkillRepository userSkillRepository;
  private final PeerGroupFallbackResolver peerGroupFallbackResolver;
  private final SkillDemandCalculator skillDemandCalculator;
  private final SkillWeightCalculator skillWeightCalculator;
  private final WageBaselineResolver wageBaselineResolver;

  @Override
  @Transactional(readOnly = true)
  public MviResult calculate(UUID userId, String baseYearMonth) {
    if (userId == null) {
      return MviResult.notAvailable(baseYearMonth);
    }

    // 1. 사용자 프로필 및 보유 스킬 조회
    CareerProfile profile = careerProfileRepository.findByUserId(userId).orElse(null);
    List<UserSkill> userSkills = userSkillRepository.findByUserId(userId);

    // 보유 스킬이 없거나 프로필이 없는 경우 계산 불가 (INSUFFICIENT_SAMPLE)
    if (userSkills == null || userSkills.isEmpty()) {
      log.debug("User {} has no registered skills. Returning INSUFFICIENT_SAMPLE", userId);
      return new MviResult(
          baseYearMonth,
          null,
          IndexStatus.INSUFFICIENT_SAMPLE,
          List.of(),
          List.of(),
          null,
          LocalDate.now());
    }

    if (profile == null) {
      profile = CareerProfile.builder().userId(userId).build();
    }

    // 2. 원천별 동료집단 완화 수행
    List<SourceCoverage> coverages = new ArrayList<>();
    Map<String, PeerGroupFallbackResolver.ResolvedPeerGroup> resolvedPeerGroups = new HashMap<>();
    int totalOverallSamples = 0;

    for (String source : SUPPORTED_SOURCES) {
      PeerGroupFallbackResolver.ResolvedPeerGroup resolved =
          peerGroupFallbackResolver.resolveForSource(source, profile, baseYearMonth);
      resolvedPeerGroups.put(source, resolved);
      coverages.add(resolved.getCoverage());
      if (resolved.isSuccessful()) {
        totalOverallSamples += resolved.getSampleCount();
      }
    }

    // 모든 원천에서 표본이 부족한 경우 (N < 10)
    if (totalOverallSamples < PeerGroupFallbackResolver.MIN_SAMPLE_SIZE) {
      log.debug("Insufficient sample count across all sources for user {}", userId);
      WageBaseline baseline = wageBaselineResolver.resolveBaseline(profile);
      return new MviResult(
          baseYearMonth,
          null,
          IndexStatus.INSUFFICIENT_SAMPLE,
          coverages,
          List.of(),
          baseline,
          LocalDate.now());
    }

    // 3. 제외 스킬 분류 및 원천별 스킬 수요 지수 산출
    Map<UUID, String> excludedSkillMap = new HashMap<>();
    Map<String, Map<UUID, Double>> sourceSkillDemandIndices = new HashMap<>();

    for (String source : SUPPORTED_SOURCES) {
      PeerGroupFallbackResolver.ResolvedPeerGroup peerGroup = resolvedPeerGroups.get(source);
      Map<UUID, Double> skillIndices = new HashMap<>();

      for (UserSkill us : userSkills) {
        Skill skill = us.getSkill();
        UUID skillId = skill.getId();

        // 표준 스킬이 아닌 사용자 정의 스킬 여부 검사 (도메인 정의서 §4.2)
        if (skill.isCustom()) {
          excludedSkillMap.put(skillId, "CUSTOM_SKILL_NO_STATS");
          continue;
        }

        SkillDemandCalculator.SkillDemandResult demandResult =
            skillDemandCalculator.calculateDemandIndex(source, skillId, peerGroup, baseYearMonth);

        if ("NEW_OBSERVATION".equals(demandResult.getStatus())) {
          // 분모 0인 신규 관측 스킬 -> 가중합 제외 (기능 명세 §4.1)
          excludedSkillMap.put(skillId, "NEW_OBSERVATION");
        } else {
          skillIndices.put(skillId, demandResult.getDemandIndex());
        }
      }
      sourceSkillDemandIndices.put(source, skillIndices);
    }

    // 제외 스킬 목록 DTO 구성
    Set<UUID> excludedSkillIds = excludedSkillMap.keySet();
    List<ExcludedSkill> excludedSkills = new ArrayList<>();
    for (UserSkill us : userSkills) {
      UUID skillId = us.getSkill().getId();
      if (excludedSkillIds.contains(skillId)) {
        String reasonCode = excludedSkillMap.get(skillId);
        SkillExclusionReason reason =
            "CUSTOM_SKILL_NO_STATS".equals(reasonCode)
                ? SkillExclusionReason.CUSTOM_SKILL_NO_STATS
                : SkillExclusionReason.NEW_OBSERVATION;
        excludedSkills.add(new ExcludedSkill(skillId, us.getSkill().getStandardName(), reason));
      }
    }

    // 4. 유효 스킬들에 대해 숙련도 가중치 재정규화 (합 = 1.0)
    Map<UUID, BigDecimal> normalizedWeights =
        skillWeightCalculator.calculateWeights(userSkills, baseYearMonth, excludedSkillIds);

    if (normalizedWeights.isEmpty()) {
      log.debug("No valid skills remaining after exclusion for user {}", userId);
      WageBaseline baseline = wageBaselineResolver.resolveBaseline(profile);
      return new MviResult(
          baseYearMonth,
          null,
          IndexStatus.INSUFFICIENT_SAMPLE,
          coverages,
          excludedSkills,
          baseline,
          LocalDate.now());
    }

    // 5. 원천별 MVI 산출 및 표본수 가중 결합
    double weightedMviSum = 0.0;
    int validSampleSum = 0;

    for (String source : SUPPORTED_SOURCES) {
      PeerGroupFallbackResolver.ResolvedPeerGroup peerGroup = resolvedPeerGroups.get(source);
      if (!peerGroup.isSuccessful() || peerGroup.getSampleCount() <= 0) {
        continue;
      }

      Map<UUID, Double> skillIndices = sourceSkillDemandIndices.get(source);
      double sourceMvi = 0.0;

      for (Map.Entry<UUID, BigDecimal> entry : normalizedWeights.entrySet()) {
        UUID skillId = entry.getKey();
        double weight = entry.getValue().doubleValue();
        double demandIndex = skillIndices.getOrDefault(skillId, 100.0);
        sourceMvi += weight * demandIndex;
      }

      int sampleCount = peerGroup.getSampleCount();
      weightedMviSum += sourceMvi * sampleCount;
      validSampleSum += sampleCount;
    }

    BigDecimal finalMviValue = null;
    IndexStatus status = IndexStatus.OK;

    if (validSampleSum > 0) {
      double combinedMvi = weightedMviSum / validSampleSum;
      finalMviValue = BigDecimal.valueOf(combinedMvi).setScale(1, RoundingMode.HALF_UP);
    } else {
      status = IndexStatus.INSUFFICIENT_SAMPLE;
    }

    // 6. 임금 기준선 조회
    WageBaseline wageBaseline = wageBaselineResolver.resolveBaseline(profile);

    // TODO: [Member B/C] aggregateAsOf에 market_aggregate 최신 적재 일자 또는 배치 완료 시점 동적 매핑
    // TODO: [Member B/C] 스냅샷 재계산(Job 4) 트리거 연동 시 recalculateSnapshot(userId, fromYm) 호출 배선
    return new MviResult(
        baseYearMonth,
        finalMviValue,
        status,
        coverages,
        excludedSkills,
        wageBaseline,
        LocalDate.now());
  }

  /**
   * 사용자의 스킬별 상세 수요 정보 및 가중치 목록을 계산합니다 (대시보드 S-14 연동용).
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

    CareerProfile profile = careerProfileRepository.findByUserId(userId).orElse(null);
    if (profile == null) {
      profile = CareerProfile.builder().userId(userId).build();
    }

    List<UserSkill> userSkills = userSkillRepository.findByUserId(userId);
    if (userSkills == null || userSkills.isEmpty()) {
      return Collections.emptyList();
    }

    // 1. 원천별 완화 수행
    Map<String, PeerGroupFallbackResolver.ResolvedPeerGroup> resolvedPeerGroups = new HashMap<>();
    int totalSamples = 0;
    for (String source : SUPPORTED_SOURCES) {
      PeerGroupFallbackResolver.ResolvedPeerGroup resolved =
          peerGroupFallbackResolver.resolveForSource(source, profile, baseYearMonth);
      resolvedPeerGroups.put(source, resolved);
      if (resolved.isSuccessful()) {
        totalSamples += resolved.getSampleCount();
      }
    }

    // 2. 제외 스킬 식별
    Set<UUID> excludedSkillIds = new HashSet<>();
    for (UserSkill us : userSkills) {
      Skill skill = us.getSkill();
      if (skill.isCustom()) {
        excludedSkillIds.add(skill.getId());
      }
    }

    // 3. 가중치 계산
    Map<UUID, BigDecimal> weights =
        skillWeightCalculator.calculateWeights(userSkills, baseYearMonth, excludedSkillIds);

    // 4. 스킬별 수요 통계 집계
    List<SkillDemandDetailData> resultList = new ArrayList<>();
    for (UserSkill us : userSkills) {
      Skill skill = us.getSkill();
      UUID skillId = skill.getId();
      String standardName = skill.getStandardName();
      double weight = weights.getOrDefault(skillId, BigDecimal.ZERO).doubleValue();

      if (excludedSkillIds.contains(skillId)) {
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

      for (String source : SUPPORTED_SOURCES) {
        PeerGroupFallbackResolver.ResolvedPeerGroup peerGroup = resolvedPeerGroups.get(source);
        SkillDemandCalculator.SkillDemandResult demandRes =
            skillDemandCalculator.calculateDemandIndex(source, skillId, peerGroup, baseYearMonth);

        int sampleSize = peerGroup.getSampleCount();
        sourceStats.add(new SkillSourceStatData(source, demandRes.getDemandIndex(), sampleSize));

        if ("NEW_OBSERVATION".equals(demandRes.getStatus())) {
          overallStatus = "NEW_OBSERVATION";
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
