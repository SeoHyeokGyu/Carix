package com.carix.expense.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carix.expense.domain.ExpenseCategoryEnum;
import java.nio.charset.StandardCharsets;
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

  private static Resource yaml(String content) {
    return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8), "test-yaml");
  }
}
