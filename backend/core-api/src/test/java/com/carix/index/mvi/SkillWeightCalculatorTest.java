package com.carix.index.mvi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

import com.carix.skill.domain.Skill;
import com.carix.skill.domain.UserSkill;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SkillWeightCalculatorTest {

  private final SkillWeightCalculator calculator = new SkillWeightCalculator();

  @Test
  @DisplayName("도메인 정의서 §3.3 산출 예시와 동일한 가중치(71.5% vs 28.5%)가 도출되어야 한다")
  void testDomainSpecificationExample() {
    // Given
    // 기준월: 2026년 7월
    String baseYearMonth = "202607";

    Skill skillA =
        Skill.builder().id(UUID.randomUUID()).standardName("Java").isCustom(false).build();
    Skill skillB =
        Skill.builder().id(UUID.randomUUID()).standardName("Spring").isCustom(false).build();

    // 스킬 A: 24개월 경력, 이번 달(2026-07)까지 사용 (0개월 방치)
    UserSkill userSkillA =
        UserSkill.builder()
            .skill(skillA)
            .monthsOfExperience(24)
            .lastUsedDate(LocalDate.of(2026, 7, 15))
            .build();

    // 스킬 B: 12개월 경력, 10개월 전(2025-09) 마지막 사용 (10개월 방치)
    UserSkill userSkillB =
        UserSkill.builder()
            .skill(skillB)
            .monthsOfExperience(12)
            .lastUsedDate(LocalDate.of(2025, 9, 1))
            .build();

    List<UserSkill> userSkills = List.of(userSkillA, userSkillB);

    // When
    Map<UUID, BigDecimal> weights =
        calculator.calculateWeights(userSkills, baseYearMonth, Set.of());

    // Then
    // S_A = ln(25) * 1.0 ≈ 3.218876
    // S_B = ln(13) * (1 / (1 + 0.1 * 10)) = 2.564949 * 0.5 ≈ 1.282475
    // Total ≈ 4.50135
    // W_A ≈ 3.218876 / 4.50135 ≈ 0.7151 (71.5%)
    // W_B ≈ 1.282475 / 4.50135 ≈ 0.2849 (28.5%)
    assertThat(weights).hasSize(2);
    assertThat(weights.get(skillA.getId()).doubleValue()).isCloseTo(0.7151, offset(0.001));
    assertThat(weights.get(skillB.getId()).doubleValue()).isCloseTo(0.2849, offset(0.001));

    // 두 가중치의 합이 1.0에 근사한지 검증
    double sum = weights.values().stream().mapToDouble(BigDecimal::doubleValue).sum();
    assertThat(sum).isCloseTo(1.0, offset(0.001));
  }

  @Test
  @DisplayName("제외된 스킬이 있을 때 나머지 유효 스킬로 100% 재정규화되어야 한다")
  void testExclusionAndRenormalization() {
    // Given
    String baseYearMonth = "202607";
    Skill skillA =
        Skill.builder().id(UUID.randomUUID()).standardName("Java").isCustom(false).build();
    Skill skillExcluded =
        Skill.builder().id(UUID.randomUUID()).standardName("Custom").isCustom(true).build();

    UserSkill usA =
        UserSkill.builder()
            .skill(skillA)
            .monthsOfExperience(12)
            .lastUsedDate(LocalDate.of(2026, 7, 1))
            .build();

    UserSkill usExcluded =
        UserSkill.builder()
            .skill(skillExcluded)
            .monthsOfExperience(36)
            .lastUsedDate(LocalDate.of(2026, 7, 1))
            .build();

    // When
    Map<UUID, BigDecimal> weights =
        calculator.calculateWeights(
            List.of(usA, usExcluded), baseYearMonth, Set.of(skillExcluded.getId()));

    // Then
    assertThat(weights).hasSize(1);
    assertThat(weights.containsKey(skillExcluded.getId())).isFalse();
    assertThat(weights.get(skillA.getId()).doubleValue()).isCloseTo(1.0000, offset(0.0001));
  }
}
