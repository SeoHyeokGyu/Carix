package com.carix.index.cpi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carix.expense.domain.ExpenseCategoryEnum;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

class CpiCategoryConfigTest {

  private static final String MAP =
      """
      categories:
        HOUSING:   { label: 주거·공공요금, cpiCodes: [ { code: D }, { code: H } ] }
        FOOD:      { label: 식비, cpiCodes: [ { code: A } ] }
        TRANSPORT: { label: 교통, cpiCodes: [ { code: G } ] }
        MEDICAL:   { label: 의료, cpiCodes: [ { code: F } ] }
        EDUCATION: { label: 교육·자기계발, cpiCodes: [ { code: J } ] }
        LEISURE:   { label: 여가·기타, cpiCodes: [ { code: B } ] }
      """;

  private static final String WEIGHTS =
      """
      weights:
        HOUSING:   { weight: 0.3, subWeights: [ { cpi: D, weight: 0.6835 }, { cpi: H, weight: 0.3165 } ] }
        FOOD:      { weight: 0.3, subWeights: [ { cpi: A, weight: 1.0 } ] }
        TRANSPORT: { weight: 0.1, subWeights: [ { cpi: G, weight: 1.0 } ] }
        MEDICAL:   { weight: 0.1, subWeights: [ { cpi: F, weight: 1.0 } ] }
        EDUCATION: { weight: 0.1, subWeights: [ { cpi: J, weight: 1.0 } ] }
        LEISURE:   { weight: 0.1, subWeights: [ { cpi: B, weight: 1.0 } ] }
      """;

  private static Resource yaml(String content) {
    return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8), "test.yml");
  }

  @Test
  @DisplayName("저장소 정본 두 파일(빌드 시 클래스패스로 복사됨)이 검증을 통과한다")
  void repositoryFiles_passValidation() {
    var config =
        new CpiCategoryConfig(
            new ClassPathResource("config/cpi-category-map.yml"),
            new ClassPathResource("config/default-expense-weights.yml"));

    assertThat(config.categories()).containsOnlyKeys(ExpenseCategoryEnum.values());
    assertThat(config.labels().get(ExpenseCategoryEnum.TRANSPORT)).isEqualTo("교통");
    assertThat(config.categories().get(ExpenseCategoryEnum.LEISURE).subSeries()).hasSize(5);
  }

  @Test
  @DisplayName("카테고리마다 라벨과 하위 계열(코드·가중치)을 읽는다")
  void valid_parsesLabelsAndSubSeries() {
    var config = new CpiCategoryConfig(yaml(MAP), yaml(WEIGHTS));

    var housing = config.categories().get(ExpenseCategoryEnum.HOUSING);
    assertThat(housing.label()).isEqualTo("주거·공공요금");
    assertThat(housing.subSeries())
        .extracting(CpiCategoryConfig.SubSeries::cpiCode)
        .containsExactly("D", "H");
    assertThat(housing.subSeries().get(0).weight()).isEqualByComparingTo("0.6835");
  }

  @Test
  @DisplayName("하위 가중치 합이 1.0 이 아니면 기동을 거부한다")
  void subWeightSumNotOne_rejects() {
    String broken = WEIGHTS.replace("{ cpi: H, weight: 0.3165 }", "{ cpi: H, weight: 0.2 }");

    assertThatThrownBy(() -> new CpiCategoryConfig(yaml(MAP), yaml(broken)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("HOUSING");
  }

  @Test
  @DisplayName("매핑 파일과 가중치 파일의 CPI 코드가 다르면 기동을 거부한다")
  void codeMismatch_rejects() {
    String broken = WEIGHTS.replace("{ cpi: G, weight: 1.0 }", "{ cpi: K, weight: 1.0 }");

    assertThatThrownBy(() -> new CpiCategoryConfig(yaml(MAP), yaml(broken)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("TRANSPORT");
  }

  @Test
  @DisplayName("카테고리가 빠지면 기동을 거부한다")
  void missingCategory_rejects() {
    String broken = MAP.replace("  MEDICAL:   { label: 의료, cpiCodes: [ { code: F } ] }\n", "");

    assertThatThrownBy(() -> new CpiCategoryConfig(yaml(broken), yaml(WEIGHTS)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("MEDICAL");
  }

  @Test
  @DisplayName("매핑 파일에 알 수 없는 카테고리가 있으면 기동을 거부한다")
  void unknownCategory_rejects() {
    String broken = MAP + "  OTHER:     { label: 기타, cpiCodes: [ { code: L } ] }\n";

    assertThatThrownBy(() -> new CpiCategoryConfig(yaml(broken), yaml(WEIGHTS)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("OTHER");
  }

  @Test
  @DisplayName("가중치 파일에 알 수 없는 카테고리가 있으면 기동을 거부한다")
  void unknownCategoryInWeights_rejects() {
    String broken =
        WEIGHTS + "  TRANSPORTATION: { weight: 0.1, subWeights: [ { cpi: L, weight: 1.0 } ] }\n";

    assertThatThrownBy(() -> new CpiCategoryConfig(yaml(MAP), yaml(broken)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("TRANSPORTATION");
  }
}
