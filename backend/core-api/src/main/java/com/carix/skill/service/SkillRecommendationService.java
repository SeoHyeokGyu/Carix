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
import com.carix.skill.repository.SkillRepository;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 동료집단 기반 스킬 보강 추천 서비스 (API 규격 §5.6, 기능 명세 US-D6).
 *
 * <p>사용자의 동료집단 공고에서 수요율이 높으나 아직 보유하지 않은 스킬 상위 3개를 추천하고, 사용자의 기존 보유 스킬 중 공고 표본이 가장 풍부한 기준 스킬과의
 * 동시출현율(Co-occurrence Rate)을 근거로 제시합니다.
 */
@Slf4j
@Service
@ReadOnlyRouting
@RequiredArgsConstructor
public class SkillRecommendationService {

  private final CareerProfileRepository careerProfileRepository;
  private final UserSkillRepository userSkillRepository;
  private final SkillRepository skillRepository;
  private final JobPostingSkillRepository jobPostingSkillRepository;
  private final PeerSalaryDistributionResolver peerSalaryDistributionResolver;
  private final PeerGroupFallbackResolver peerGroupFallbackResolver;
  private final SkillDemandCalculator skillDemandCalculator;
  private final MarketAggregateRepository marketAggregateRepository;

  @Autowired(required = false)
  private IndexThresholdProperties thresholds;

  private int minSampleSize() {
    return thresholds != null
        ? thresholds.getPeerGroupMinSampleSize()
        : PeerGroupFallbackResolver.MIN_SAMPLE_SIZE;
  }

  private double minCoOccurrenceRate() {
    return thresholds != null ? thresholds.getCoOccurrenceMinRate() : 0.20;
  }

  /**
   * 로그인 사용자의 동료집단 기반 스킬 보강 추천 목록을 조회합니다.
   *
   * @param userId 사용자 ID
   * @return 스킬 추천 응답 (조건 미달 시 빈 목록 반환)
   */
  @Transactional(readOnly = true)
  public SkillRecommendationsResponse getRecommendations(UUID userId) {
    Optional<CareerProfile> profileOpt = careerProfileRepository.findByUserId(userId);
    if (profileOpt.isEmpty()) {
      return new SkillRecommendationsResponse(new PeerGroupInfo("동료집단 미설정"), List.of());
    }

    CareerProfile profile = profileOpt.get();

    List<UserSkill> userSkills;
    ResolvedPeerPostings resolvedPeer;
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      Future<List<UserSkill>> skillsFuture =
          executor.submit(
              () ->
                  ReadOnlyRoutingContext.withReadOnlyCallable(
                          () -> userSkillRepository.findByUserId(userId))
                      .call());
      Future<ResolvedPeerPostings> peerFuture =
          executor.submit(
              () ->
                  ReadOnlyRoutingContext.withReadOnlyCallable(
                          () -> peerSalaryDistributionResolver.resolvePostings(profile))
                      .call());

      userSkills = skillsFuture.get();
      resolvedPeer = peerFuture.get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("스킬 추천 기본 데이터 병렬 조회 중 인터럽트 발생", e);
    } catch (ExecutionException e) {
      throw new RuntimeException("스킬 추천 기본 데이터 병렬 조회 실패", e.getCause());
    }

    List<JobPosting> peerPostings = resolvedPeer.postings();

    int minSample = minSampleSize();
    // 동료집단 표본이 최소 기준 미달인 경우 임의 스킬을 채우지 않고 빈 배열 반환 (US-D6)
    if (peerPostings == null || peerPostings.size() < minSample) {
      return new SkillRecommendationsResponse(
          new PeerGroupInfo(resolvedPeer.appliedConditions()), List.of());
    }

    // 1. 사용자 보유 스킬 목록 (커스텀 스킬 제외)
    Set<UUID> ownedSkillIds =
        userSkills.stream()
            .map(UserSkill::getSkill)
            .filter(Objects::nonNull)
            .map(Skill::getId)
            .collect(Collectors.toSet());

    List<Skill> ownedStandardSkills =
        userSkills.stream()
            .map(UserSkill::getSkill)
            .filter(s -> s != null && !s.isCustom())
            .toList();

    // 2. 동료집단 공고별 매핑된 스킬 로드
    List<UUID> postingIds = peerPostings.stream().map(JobPosting::getId).toList();
    List<JobPostingSkill> jpsList = jobPostingSkillRepository.findByJobPostingIdIn(postingIds);

    Map<UUID, Set<UUID>> postingToSkillIds = new HashMap<>();
    Map<UUID, Skill> skillCatalog = new HashMap<>();
    for (JobPostingSkill jps : jpsList) {
      if (jps.getSkill() != null && jps.getJobPosting() != null) {
        postingToSkillIds
            .computeIfAbsent(jps.getJobPosting().getId(), k -> new HashSet<>())
            .add(jps.getSkill().getId());
        skillCatalog.putIfAbsent(jps.getSkill().getId(), jps.getSkill());
      }
    }

    // 3. 후보 스킬 (미보유 & 커스텀 제외) 수요율 산출
    int totalPeerCount = peerPostings.size();
    Map<UUID, Integer> candidateCounts = new HashMap<>();
    for (JobPosting posting : peerPostings) {
      Set<UUID> skillsInPosting = postingToSkillIds.getOrDefault(posting.getId(), Set.of());
      for (UUID sId : skillsInPosting) {
        if (!ownedSkillIds.contains(sId)) {
          Skill skill = skillCatalog.get(sId);
          if (skill != null && !skill.isCustom()) {
            candidateCounts.put(sId, candidateCounts.getOrDefault(sId, 0) + 1);
          }
        }
      }
    }

    if (candidateCounts.isEmpty()) {
      return new SkillRecommendationsResponse(
          new PeerGroupInfo(resolvedPeer.appliedConditions()), List.of());
    }

    List<CandidateRanking> rankedCandidates =
        candidateCounts.entrySet().stream()
            .map(
                entry -> {
                  UUID sId = entry.getKey();
                  int count = entry.getValue();
                  double rawDemandRate = (double) count / totalPeerCount;
                  double roundedDemandRate = Math.round(rawDemandRate * 1000.0) / 1000.0;
                  return new CandidateRanking(skillCatalog.get(sId), count, roundedDemandRate);
                })
            .sorted(
                Comparator.comparingDouble(CandidateRanking::demandRate)
                    .thenComparingInt(CandidateRanking::postingCount)
                    .reversed()
                    .thenComparing(c -> c.skill().getStandardName()))
            .limit(3)
            .toList();

    // 4. 보유 스킬 중 기준 스킬(Base Skill) 도출: 동료집단 공고 수가 가장 많은 스킬
    Skill baseSkill = null;
    int maxBasePostings = 0;
    for (Skill ownedSkill : ownedStandardSkills) {
      int count = 0;
      for (JobPosting posting : peerPostings) {
        if (postingToSkillIds
            .getOrDefault(posting.getId(), Set.of())
            .contains(ownedSkill.getId())) {
          count++;
        }
      }
      if (count > maxBasePostings) {
        maxBasePostings = count;
        baseSkill = ownedSkill;
      }
    }

    // 기준 스킬의 동료집단 공고 수가 최소 표본(minSample) 미달이면 coOccurrence는 null
    boolean isBaseSkillSufficient = (baseSkill != null && maxBasePostings >= minSample);

    // 5. 추천 항목 조립 및 동시출현율 계산
    String baseYearMonth = resolveBaseYearMonth();
    PeerGroupFallbackResolver.ResolvedPeerGroup resolvedPeerGroup =
        peerGroupFallbackResolver.resolve("ALL", baseYearMonth, profile);

    Map<UUID, String> growthTrends;
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Map.Entry<UUID, Future<String>>> trendFutures = new ArrayList<>(rankedCandidates.size());
      for (CandidateRanking candidate : rankedCandidates) {
        UUID cId = candidate.skill().getId();
        trendFutures.add(
            Map.entry(
                cId,
                executor.submit(
                    () ->
                        ReadOnlyRoutingContext.withReadOnlyCallable(
                                () -> evaluateGrowthTrend(cId, baseYearMonth, resolvedPeerGroup))
                            .call())));
      }
      growthTrends = new HashMap<>();
      for (var entry : trendFutures) {
        growthTrends.put(entry.getKey(), entry.getValue().get());
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("후보 스킬 트렌드 병렬 계산 중 인터럽트 발생", e);
    } catch (ExecutionException e) {
      throw new RuntimeException("후보 스킬 트렌드 병렬 계산 실패", e.getCause());
    }

    List<SkillRecommendationItem> items = new ArrayList<>();
    double coOccurMinRate = minCoOccurrenceRate();

    for (CandidateRanking candidate : rankedCandidates) {
      Skill candSkill = candidate.skill();
      CoOccurrenceInfo coOccurrence = null;
      String reason;

      if (isBaseSkillSufficient) {
        UUID baseId = baseSkill.getId();
        UUID candId = candSkill.getId();
        int bothCount = 0;

        for (JobPosting posting : peerPostings) {
          Set<UUID> sIds = postingToSkillIds.getOrDefault(posting.getId(), Set.of());
          if (sIds.contains(baseId) && sIds.contains(candId)) {
            bothCount++;
          }
        }

        double rawRate = (double) bothCount / maxBasePostings;
        double roundedRate = Math.round(rawRate * 100.0) / 100.0;

        if (roundedRate >= coOccurMinRate) {
          coOccurrence = new CoOccurrenceInfo(baseSkill.getStandardName(), roundedRate);
          int percent = (int) Math.round(roundedRate * 100);
          reason =
              String.format("보유하신 %s 공고의 %d%%가 함께 요구합니다.", baseSkill.getStandardName(), percent);
        } else {
          // 동시출현율이 임계값 미만이면 수요율 문구로 대체 (US-D6)
          reason =
              String.format("동료집단 내 공고의 %.1f%%에서 요구되는 핵심 스택입니다.", candidate.demandRate() * 100.0);
        }
      } else {
        // 기준 스킬 부재 또는 표본 미달 시 수요율 문구
        reason =
            String.format("동료집단 내 공고의 %.1f%%에서 요구되는 핵심 스택입니다.", candidate.demandRate() * 100.0);
      }

      String growthTrend = growthTrends.getOrDefault(candSkill.getId(), "STABLE");

      items.add(
          new SkillRecommendationItem(
              candSkill.getStandardName(),
              candSkill.getCategory(),
              candidate.demandRate(),
              growthTrend,
              coOccurrence,
              reason));
    }

    return new SkillRecommendationsResponse(
        new PeerGroupInfo(resolvedPeer.appliedConditions()), items);
  }

  private String evaluateGrowthTrend(
      UUID skillId,
      String baseYearMonth,
      PeerGroupFallbackResolver.ResolvedPeerGroup resolvedPeerGroup) {
    try {
      SkillDemandResult demandResult =
          skillDemandCalculator.calculate(skillId, baseYearMonth, resolvedPeerGroup);
      if (demandResult != null) {
        if (demandResult.getStatus() == SkillDemandStatus.OK) {
          if (demandResult.getDemandIndex() > 105.0) {
            return "UP";
          } else if (demandResult.getDemandIndex() < 95.0) {
            return "DOWN";
          } else {
            return "STABLE";
          }
        } else if (demandResult.getStatus() == SkillDemandStatus.NEW_OBSERVATION) {
          return "UP";
        } else if (demandResult.getStatus() == SkillDemandStatus.NO_RECENT_DEMAND) {
          return "DOWN";
        }
      }
    } catch (Exception e) {
      log.debug("스킬({}) 성장 트렌드 계산 중 예외 발생, 기본값 STABLE 사용: {}", skillId, e.getMessage());
    }
    return "STABLE";
  }

  private String resolveBaseYearMonth() {
    String currentYm = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
    return marketAggregateRepository.findLatestAvailableYearMonth(currentYm).orElse(currentYm);
  }

  private record CandidateRanking(Skill skill, int postingCount, double demandRate) {}
}
