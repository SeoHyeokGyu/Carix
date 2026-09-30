package com.carix.index.cpi;

import com.carix.expense.domain.ExpenseCategoryEnum;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * KOSIS 지출목적별 CPI 12대 분류를 Carix 6개 소비 카테고리로 합성하는 규칙.
 *
 * <p>코드 매핑과 라벨은 {@code cpi-category-map.yml}, 하위 가중치는 {@code default-expense-weights.yml} 의 {@code
 * subWeights} 에서 온다. 두 정본 모두 저장소 루트 {@code config/} 에 있고 core-api 빌드가 JAR 로 복사한다.
 *
 * <p>값이 어긋난 채로 뜨면 기여도가 조용히 틀리므로, 카테고리 집합·코드 집합·하위 가중치 합이 어긋나면 기동을 거부한다.
 */
@Component
public class CpiCategoryConfig {

  private static final BigDecimal SUM_TOLERANCE = new BigDecimal("0.0005");

  /** 하위 CPI 계열 하나. {@code weight} 는 카테고리 안에서의 비중이다. */
  public record SubSeries(String cpiCode, BigDecimal weight) {}

  public record CategoryDefinition(String label, List<SubSeries> subSeries) {}

  private final Map<ExpenseCategoryEnum, CategoryDefinition> categories;

  public CpiCategoryConfig(
      @Value("classpath:config/cpi-category-map.yml") Resource categoryMap,
      @Value("classpath:config/default-expense-weights.yml") Resource expenseWeights) {
    Map<?, ?> mapCategories = section(load(categoryMap), "categories", categoryMap);
    Map<?, ?> weightCategories = section(load(expenseWeights), "weights", expenseWeights);

    validateCategorySetInMap(mapCategories);
    validateCategorySetInWeights(weightCategories);

    Map<ExpenseCategoryEnum, CategoryDefinition> parsed = new EnumMap<>(ExpenseCategoryEnum.class);
    for (ExpenseCategoryEnum category : ExpenseCategoryEnum.values()) {
      parsed.put(category, definition(category, mapCategories, weightCategories));
    }
    this.categories = Collections.unmodifiableMap(parsed);
  }

  public Map<ExpenseCategoryEnum, CategoryDefinition> categories() {
    return categories;
  }

  public Map<ExpenseCategoryEnum, String> labels() {
    Map<ExpenseCategoryEnum, String> labels = new EnumMap<>(ExpenseCategoryEnum.class);
    categories.forEach((category, definition) -> labels.put(category, definition.label()));
    return Collections.unmodifiableMap(labels);
  }

  private static CategoryDefinition definition(
      ExpenseCategoryEnum category, Map<?, ?> mapCategories, Map<?, ?> weightCategories) {
    if (!(mapCategories.get(category.name()) instanceof Map<?, ?> mapNode)
        || !(mapNode.get("label") instanceof String label)
        || !(mapNode.get("cpiCodes") instanceof List<?> codeNodes)) {
      throw invalid(category + ": cpi-category-map.yml 에 label·cpiCodes 가 없음");
    }
    if (!(weightCategories.get(category.name()) instanceof Map<?, ?> weightNode)
        || !(weightNode.get("subWeights") instanceof List<?> subNodes)) {
      throw invalid(category + ": default-expense-weights.yml 에 subWeights 가 없음");
    }

    Set<String> mappedCodes = new TreeSet<>();
    for (Object node : codeNodes) {
      if (node instanceof Map<?, ?> codeNode && codeNode.get("code") != null) {
        mappedCodes.add(String.valueOf(codeNode.get("code")));
      }
    }

    List<SubSeries> subSeries = new ArrayList<>();
    BigDecimal sum = BigDecimal.ZERO;
    for (Object node : subNodes) {
      if (!(node instanceof Map<?, ?> subNode)
          || subNode.get("cpi") == null
          || !(subNode.get("weight") instanceof Number rawWeight)) {
        throw invalid(category + ": subWeights 항목에 cpi·weight 가 없음");
      }
      // SnakeYAML 은 소수를 Double 로 읽는다 — 이진 오차를 피하려고 문자열을 경유한다
      BigDecimal weight = new BigDecimal(rawWeight.toString());
      if (weight.signum() <= 0) {
        throw invalid(category + ": 하위 가중치는 양수여야 함");
      }
      subSeries.add(new SubSeries(String.valueOf(subNode.get("cpi")), weight));
      sum = sum.add(weight);
    }

    Set<String> weightedCodes = new TreeSet<>();
    subSeries.forEach(series -> weightedCodes.add(series.cpiCode()));
    if (!mappedCodes.equals(weightedCodes)) {
      throw invalid(category + ": CPI 코드 불일치 — 매핑 " + mappedCodes + ", 가중치 " + weightedCodes);
    }
    if (sum.subtract(BigDecimal.ONE).abs().compareTo(SUM_TOLERANCE) > 0) {
      throw invalid(category + ": 하위 가중치 합이 1.0 이 아님 (" + sum.toPlainString() + ")");
    }
    return new CategoryDefinition(label, List.copyOf(subSeries));
  }

  private static Map<?, ?> load(Resource resource) {
    try (InputStream in = resource.getInputStream()) {
      if (new Yaml(new SafeConstructor(new LoaderOptions())).load(in) instanceof Map<?, ?> root) {
        return root;
      }
      throw invalid("최상위가 매핑이 아님 (" + resource.getDescription() + ")");
    } catch (IOException | YAMLException e) {
      throw new IllegalStateException(
          "CPI 매핑 설정을 읽을 수 없음 (" + resource.getDescription() + ") — core-api/build.gradle 복사를 확인",
          e);
    }
  }

  private static void validateCategorySetInMap(Map<?, ?> mapCategories) {
    Set<String> enumNames = new TreeSet<>();
    for (ExpenseCategoryEnum category : ExpenseCategoryEnum.values()) {
      enumNames.add(category.name());
    }
    Set<String> unknownKeys = new TreeSet<>();
    for (Object key : mapCategories.keySet()) {
      if (!enumNames.contains(String.valueOf(key))) {
        unknownKeys.add(String.valueOf(key));
      }
    }
    if (!unknownKeys.isEmpty()) {
      throw invalid("cpi-category-map.yml 에 알 수 없는 카테고리: " + unknownKeys);
    }
  }

  private static void validateCategorySetInWeights(Map<?, ?> weightCategories) {
    Set<String> enumNames = new TreeSet<>();
    for (ExpenseCategoryEnum category : ExpenseCategoryEnum.values()) {
      enumNames.add(category.name());
    }
    Set<String> unknownKeys = new TreeSet<>();
    for (Object key : weightCategories.keySet()) {
      if (!enumNames.contains(String.valueOf(key))) {
        unknownKeys.add(String.valueOf(key));
      }
    }
    if (!unknownKeys.isEmpty()) {
      throw invalid("default-expense-weights.yml 에 알 수 없는 카테고리: " + unknownKeys);
    }
  }

  private static Map<?, ?> section(Map<?, ?> root, String key, Resource resource) {
    if (root.get(key) instanceof Map<?, ?> section) {
      return section;
    }
    throw invalid("'" + key + "' 매핑이 없음 (" + resource.getDescription() + ")");
  }

  private static IllegalStateException invalid(String detail) {
    return new IllegalStateException("CPI 매핑 설정 검증 실패: " + detail);
  }
}
