package com.carix.expense.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carix.expense.domain.ExpenseCategoryEnum;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

class DefaultExpenseWeightProviderTest {

  private static final String VALID =
      """
            version: 1
            basePeriod: "2025"
            weights:
              HOUSING: { weight: 0.30 }
              FOOD: { weight: 0.30 }
              TRANSPORT: { weight: 0.10 }
              MEDICAL: { weight: 0.10 }
              EDUCATION: { weight: 0.10 }
              LEISURE: { weight: 0.10 }
            """;

  @Test
  @DisplayName("저장소 정본 yml(빌드 시 클래스패스로 복사됨)이 검증을 통과한다")
  void repositoryYaml_passesValidation() {
    var provider =
        new DefaultExpenseWeightProvider(
            new ClassPathResource("config/default-expense-weights.yml"));

    assertThat(provider.getDefaultWeights()).containsOnlyKeys(ExpenseCategoryEnum.values());
    assertThat(provider.getVersion()).isPositive();
    assertThat(provider.getBasePeriod()).isNotBlank();
  }

  @Test
  @DisplayName("정상 yml 이면 가중치·메타를 그대로 읽고, 반환 맵은 수정할 수 없다")
  void valid_parsesValuesAndIsUnmodifiable() {
    var provider = new DefaultExpenseWeightProvider(yaml(VALID));

    Map<ExpenseCategoryEnum, BigDecimal> weights = provider.getDefaultWeights();
    assertThat(weights.get(ExpenseCategoryEnum.HOUSING)).isEqualByComparingTo("0.30");
    assertThat(weights.get(ExpenseCategoryEnum.LEISURE)).isEqualByComparingTo("0.10");
    assertThat(provider.getVersion()).isEqualTo(1);
    assertThat(provider.getBasePeriod()).isEqualTo("2025");

    assertThatThrownBy(() -> weights.put(ExpenseCategoryEnum.FOOD, BigDecimal.ONE))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @DisplayName("basePeriod 를 따옴표 없이 써도 문자열로 읽는다")
  void basePeriod_unquoted_readAsText() {
    var provider =
        new DefaultExpenseWeightProvider(
            yaml(VALID.replace("basePeriod: \"2025\"", "basePeriod: 2025")));

    assertThat(provider.getBasePeriod()).isEqualTo("2025");
  }

  @Test
  @DisplayName("카테고리가 빠지면 기동을 거부한다")
  void missingCategory_fails() {
    String broken = VALID.replace("MEDICAL: { weight: 0.10 }", "");

    assertThatThrownBy(() -> new DefaultExpenseWeightProvider(yaml(broken)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("MEDICAL");
  }

  @Test
  @DisplayName("enum 에 없는 카테고리 키(구 명칭 등)는 조용히 무시하지 않고 거부한다")
  void unknownCategoryKey_fails() {
    String broken = VALID.replace("TRANSPORT:", "TRANSPORTATION:");

    assertThatThrownBy(() -> new DefaultExpenseWeightProvider(yaml(broken)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("TRANSPORTATION");
  }

  @Test
  @DisplayName("가중치 합이 1.0 이 아니면 기동을 거부한다")
  void sumNotOne_fails() {
    String broken = VALID.replace("HOUSING: { weight: 0.30 }", "HOUSING: { weight: 0.40 }");

    assertThatThrownBy(() -> new DefaultExpenseWeightProvider(yaml(broken)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("합");
  }

  @Test
  @DisplayName("weight 가 0 이하이면 기동을 거부한다")
  void nonPositiveWeight_fails() {
    String broken = VALID.replace("LEISURE: { weight: 0.10 }", "LEISURE: { weight: 0 }");

    assertThatThrownBy(() -> new DefaultExpenseWeightProvider(yaml(broken)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("LEISURE");
  }

  @Test
  @DisplayName("weights 루트 키가 없거나 빈 파일이면 기동을 거부한다")
  void missingWeightsOrEmptyFile_fails() {
    assertThatThrownBy(
            () -> new DefaultExpenseWeightProvider(yaml("version: 1\nbasePeriod: \"2025\"\n")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("weights");

    assertThatThrownBy(() -> new DefaultExpenseWeightProvider(yaml("")))
        .isInstanceOf(IllegalStateException.class);
  }

  private static Resource yaml(String content) {
    return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8), "test-yaml");
  }
}
