package com.carix.index.mvi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.carix.index.IndexStatus;
import com.carix.index.dto.MviResult;
import com.carix.index.dto.SourceCoverage;
import com.carix.index.dto.WageBaseline;
import com.carix.profile.domain.CareerProfile;
import com.carix.profile.repository.CareerProfileRepository;
import com.carix.skill.domain.Skill;
import com.carix.skill.domain.UserSkill;
import com.carix.skill.repository.UserSkillRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarketValueIndexServiceTest {

  @Mock private CareerProfileRepository careerProfileRepository;
  @Mock private UserSkillRepository userSkillRepository;
  @Mock private PeerGroupFallbackResolver peerGroupFallbackResolver;
  @Mock private SkillDemandCalculator skillDemandCalculator;
  @Mock private SkillWeightCalculator skillWeightCalculator;
  @Mock private WageBaselineResolver wageBaselineResolver;

  @InjectMocks private MarketValueIndexServiceImpl service;

  @Test
  @DisplayName("보유 스킬이 없는 경우 INSUFFICIENT_SAMPLE 상태와 null 값을 반환해야 한다")
  void testNoSkillsReturnsInsufficientSample() {
    UUID userId = UUID.randomUUID();
    when(userSkillRepository.findByUserId(userId)).thenReturn(List.of());

    MviResult result = service.calculate(userId, "202607");

    assertThat(result.value()).isNull();
    assertThat(result.status()).isEqualTo(IndexStatus.INSUFFICIENT_SAMPLE);
  }

  @Test
  @DisplayName("정상 계산: 원천별 스킬 수요 지수와 가중치를 결합하여 MviResult를 산출해야 한다")
  void testSuccessfulMviCalculation() {
    // Given
    UUID userId = UUID.randomUUID();
    String baseYearMonth = "202607";

    CareerProfile profile = new CareerProfile(userId);
    profile.patch("DEVELOPER", 5, "서울 강남구", "FULL_TIME", "MEDIUM");

    Skill skill =
        Skill.builder().id(UUID.randomUUID()).standardName("Java").isCustom(false).build();
    UserSkill userSkill =
        UserSkill.builder()
            .skill(skill)
            .monthsOfExperience(24)
            .lastUsedDate(LocalDate.of(2026, 7, 1))
            .build();

    when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
    when(userSkillRepository.findByUserId(userId)).thenReturn(List.of(userSkill));

    PeerGroupFallbackResolver.ResolvedPeerGroup resolvedGroup =
        PeerGroupFallbackResolver.ResolvedPeerGroup.builder()
            .source("JOBKOREA")
            .successful(true)
            .sampleCount(50)
            .fallbackLevel(1)
            .coverage(new SourceCoverage("JOBKOREA", 1, 50, "조건", BigDecimal.ZERO))
            .build();

    when(peerGroupFallbackResolver.resolveForSource(any(), any(), any())).thenReturn(resolvedGroup);

    SkillDemandCalculator.SkillDemandResult demandResult =
        SkillDemandCalculator.SkillDemandResult.builder()
            .skillId(skill.getId())
            .demandIndex(120.0)
            .status("OK")
            .build();

    when(skillDemandCalculator.calculateDemandIndex(
            any(), eq(skill.getId()), any(), eq(baseYearMonth)))
        .thenReturn(demandResult);

    when(skillWeightCalculator.calculateWeights(any(), any(), any()))
        .thenReturn(Map.of(skill.getId(), BigDecimal.valueOf(1.0)));

    when(wageBaselineResolver.resolveBaseline(any()))
        .thenReturn(new WageBaseline(65000000L, "초과급여 제외", "202606"));

    // When
    MviResult result = service.calculate(userId, baseYearMonth);

    // Then
    assertThat(result.status()).isEqualTo(IndexStatus.OK);
    assertThat(result.value()).isNotNull();
    assertThat(result.value().doubleValue()).isEqualTo(120.0);
    assertThat(result.wageBaseline()).isNotNull();
    assertThat(result.wageBaseline().medianGrossSalary()).isEqualTo(65000000L);
  }
}
