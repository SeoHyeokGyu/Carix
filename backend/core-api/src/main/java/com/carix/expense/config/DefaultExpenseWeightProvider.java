package com.carix.expense.config;

import com.carix.expense.domain.ExpenseCategoryEnum;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * 저장소 루트 {@code config/default-expense-weights.yml}(정본)의 기본 소비 가중치를 제공한다. domain-logic.md §1.1 Step
 * 3(DEFAULT_CPI 전체 대체)·Step 4(부분 입력 보간)가 쓴다.
 *
 * <p><b>파일을 읽는 방식.</b> 정본은 저장소 루트에 하나만 두고, core-api 빌드가 {@code processResources} 로 JAR 클래스패스 {@code
 * config/} 에 복사한다(core-api/build.gradle). 그래서 IDE·bootRun·단위 테스트·컨테이너 어디서나 같은 {@code classpath:}
 * 경로로 읽으며 환경별 경로 설정이 없다. 이 파일은 Config Server({@code config/spring/}) 대상이 아니다 — 연 1회 공표되는 도메인 데이터라
 * 갱신은 yml 수정(version 상향) 후 재배포다.
 *
 * <p><b>기동 시점 검증.</b> 값이 틀린 채로 뜨면 지수가 조용히 틀린 결과를 내므로, 형식·카테고리 집합· 합계가 어긋나면 예외로 기동을
 * 거부한다(spring-cloud-architecture.md §5 fail-fast 원칙).
 *
 * <p>{@code subWeights}(CPI 코드 단위 세부 가중치)는 PPI 엔진(a2)이 쓸 몫이라 여기서 다루지 않는다.
 */
@Slf4j
@Component
public class DefaultExpenseWeightProvider {

  private static final String FILE_NAME = "default-expense-weights.yml";
  private static final BigDecimal SUM_TOLERANCE = new BigDecimal("0.0005");

  private final int version;
  private final String basePeriod;
  private final Map<ExpenseCategoryEnum, BigDecimal> weights;

  public DefaultExpenseWeightProvider(
      @Value("classpath:config/default-expense-weights.yml") Resource resource) {
    Map<?, ?> root = read(resource);

    this.version = Math.toIntExact(requirePositiveLong(root, "version"));
    this.basePeriod = requireText(root, "basePeriod");
    this.weights = parseWeights(root);

    log.info(
        "기본 소비 가중치 로드: version={}, basePeriod={}, source={}",
        version,
        basePeriod,
        resource.getDescription());
  }

  /** 6개 카테고리 기본 가중치, 합 1.0. 기동 시점에 검증돼 있어 호출부는 추가 검증이 필요 없다. */
  public Map<ExpenseCategoryEnum, BigDecimal> getDefaultWeights() {
    return weights;
  }

  /** yml {@code version}. 새 연도 반영 시 올린다. */
  public int getVersion() {
    return version;
  }

  /** 가계동향조사 기준 연도 (예: "2025"). */
  public String getBasePeriod() {
    return basePeriod;
  }

  // ---------------------------------------------------------------------------

  private static Map<?, ?> read(Resource resource) {
    try (InputStream in = resource.getInputStream()) {
      Object loaded = new Yaml(new SafeConstructor(new LoaderOptions())).load(in);
      if (loaded instanceof Map<?, ?> map) {
        return map;
      }
      throw invalid("최상위가 매핑이 아님 (" + resource.getDescription() + ")");
    } catch (IOException e) {
      throw new IllegalStateException(
          FILE_NAME
              + " 를 읽을 수 없음 ("
              + resource.getDescription()
              + ") — core-api/build.gradle 의 processResources 복사를 확인",
          e);
    } catch (YAMLException e) {
      throw new IllegalStateException(FILE_NAME + " 파싱 실패 (" + resource.getDescription() + ")", e);
    }
  }

  private static Map<ExpenseCategoryEnum, BigDecimal> parseWeights(Map<?, ?> root) {
    if (!(root.get("weights") instanceof Map<?, ?> weightsNode)) {
      throw invalid("'weights' 매핑이 없음");
    }

    // enum 에 없는 키(오타, 구 명칭 TRANSPORTATION·HEALTH·OTHER 등)는 조용히 무시하지 않고 거부한다
    Set<String> known =
        Arrays.stream(ExpenseCategoryEnum.values()).map(Enum::name).collect(Collectors.toSet());
    Set<String> unknown = new TreeSet<>();
    for (Object key : weightsNode.keySet()) {
      if (!known.contains(String.valueOf(key))) {
        unknown.add(String.valueOf(key));
      }
    }
    if (!unknown.isEmpty()) {
      throw invalid("ExpenseCategoryEnum 에 없는 카테고리 키: " + unknown);
    }

    Map<ExpenseCategoryEnum, BigDecimal> result = new EnumMap<>(ExpenseCategoryEnum.class);
    BigDecimal sum = BigDecimal.ZERO;
    for (ExpenseCategoryEnum category : ExpenseCategoryEnum.values()) {
      if (!(weightsNode.get(category.name()) instanceof Map<?, ?> categoryNode)
          || !(categoryNode.get("weight") instanceof Number rawWeight)) {
        throw invalid("카테고리 누락 또는 숫자 weight 없음: " + category);
      }
      // SnakeYAML 은 소수를 Double 로 파싱한다 — 이진 부동소수 오차를 피하려고 문자열을 경유한다
      BigDecimal weight = new BigDecimal(rawWeight.toString());
      if (weight.signum() <= 0) {
        throw invalid("weight 는 양수여야 함: " + category + "=" + weight.toPlainString());
      }
      result.put(category, weight);
      sum = sum.add(weight);
    }

    if (sum.subtract(BigDecimal.ONE).abs().compareTo(SUM_TOLERANCE) > 0) {
      throw invalid("카테고리 가중치 합이 1.0 이 아님 (실제: " + sum.stripTrailingZeros().toPlainString() + ")");
    }
    return Collections.unmodifiableMap(result);
  }

  private static long requirePositiveLong(Map<?, ?> root, String key) {
    if (!(root.get(key) instanceof Number number) || number.longValue() <= 0) {
      throw invalid("'" + key + "' 는 양수여야 함 (실제: " + root.get(key) + ")");
    }
    return number.longValue();
  }

  private static String requireText(Map<?, ?> root, String key) {
    Object raw = root.get(key);
    // basePeriod 를 따옴표 없이 2025 로 써도 문자열로 받는다
    if (raw == null || String.valueOf(raw).isBlank()) {
      throw invalid("'" + key + "' 가 없음");
    }
    return String.valueOf(raw);
  }

  private static IllegalStateException invalid(String detail) {
    return new IllegalStateException(FILE_NAME + " 검증 실패: " + detail);
  }
}
