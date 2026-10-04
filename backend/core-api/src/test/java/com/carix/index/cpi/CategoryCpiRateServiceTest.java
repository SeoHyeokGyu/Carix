package com.carix.index.cpi;

import static org.assertj.core.api.Assertions.assertThat;

import com.carix.expense.domain.ExpenseCategoryEnum;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.core.io.ClassPathResource;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CategoryCpiRateServiceTest {

  private static final String[] CODES = {
    "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L"
  };

  @Autowired private CpiObservationRecordRepository repository;

  private CategoryCpiRateService service;

  @BeforeEach
  void setUp() {
    CpiCategoryConfig config =
        new CpiCategoryConfig(
            new ClassPathResource("config/cpi-category-map.yml"),
            new ClassPathResource("config/default-expense-weights.yml"));
    service = new CategoryCpiRateService(repository, config);
  }

  /** 12개 코드 전부 같은 값으로 한 달을 채운다. */
  private void month(String ym, String value) {
    for (String code : CODES) {
      repository.save(CpiObservationRecord.of(ym, code, value));
    }
  }

  private void put(String ym, String code, String value) {
    repository.findAll().stream()
        .filter(r -> r.getBaseYearMonth().equals(ym) && r.getCategory().equals(code))
        .forEach(repository::delete);
    repository.save(CpiObservationRecord.of(ym, code, value));
  }

  @Test
  @DisplayName("단일 계열 카테고리의 전년비는 그 계열의 전년비다")
  void singleSeries_rateIsSeriesRate() {
    month("202509", "100.00");
    month("202609", "100.00");
    put("202609", "G", "103.00");

    var rates = service.yearOverYear("202609").orElseThrow();

    assertThat(rates.cpiMonth()).isEqualTo("202609");
    assertThat(rates.rates().get(ExpenseCategoryEnum.TRANSPORT)).isEqualByComparingTo("0.03");
  }

  // 하위 가중치를 지수 수준에 곱하면 두 계열의 기준 수준 차이가 결과를 바꾼다. 변화율에 곱하면 바뀌지 않는다.
  @Test
  @DisplayName("합성 카테고리는 하위 계열 변화율의 가중합이다 — 지수 수준 평균이 아니다")
  void composite_weightsRatesNotLevels() {
    month("202509", "100.00");
    month("202609", "100.00");
    put("202509", "D", "100.00");
    put("202609", "D", "110.00"); // +10%
    put("202509", "H", "50.00");
    put("202609", "H", "51.00"); // +2%, 수준은 D 의 절반

    var rates = service.yearOverYear("202609").orElseThrow();

    // 0.6835 × 0.10 + 0.3165 × 0.02 = 0.07468
    assertThat(rates.rates().get(ExpenseCategoryEnum.HOUSING))
        .isEqualByComparingTo(new BigDecimal("0.07468"));
  }

  @Test
  @DisplayName("하위 계열 하나가 전년 동월에 없으면 그 카테고리만 빠진다 — 재조정하지 않는다")
  void missingSubSeries_dropsCategoryOnly() {
    month("202509", "100.00");
    month("202609", "101.00");
    repository.findAll().stream()
        .filter(r -> r.getBaseYearMonth().equals("202509") && r.getCategory().equals("H"))
        .forEach(repository::delete);

    Map<ExpenseCategoryEnum, BigDecimal> rates =
        service.yearOverYear("202609").orElseThrow().rates();

    assertThat(rates).doesNotContainKey(ExpenseCategoryEnum.HOUSING);
    assertThat(rates).containsKeys(ExpenseCategoryEnum.FOOD, ExpenseCategoryEnum.TRANSPORT);
  }

  @Test
  @DisplayName("대상 월 CPI 가 없으면 2개월 전까지의 최신 달을 쓴다")
  void usesLatestWithinTwoMonths() {
    month("202508", "100.00");
    month("202608", "102.00");

    var rates = service.yearOverYear("202610").orElseThrow();

    assertThat(rates.cpiMonth()).isEqualTo("202608");
    assertThat(rates.rates()).hasSize(6);
  }

  @Test
  @DisplayName("최신 CPI 가 대상 월보다 3개월 이상 오래되면 계산하지 않는다")
  void olderThanTwoMonths_empty() {
    month("202507", "100.00");
    month("202607", "102.00");

    assertThat(service.yearOverYear("202610")).isEmpty();
  }

  @Test
  @DisplayName("1월의 전년 동월은 전년 1월이다")
  void january_comparesWithPreviousJanuary() {
    month("202501", "100.00");
    month("202601", "105.00");

    var rates = service.yearOverYear("202601").orElseThrow();

    assertThat(rates.rates().get(ExpenseCategoryEnum.FOOD)).isEqualByComparingTo("0.05");
  }

  @Test
  @DisplayName("CPI 가 하나도 없으면 빈 값")
  void noData_empty() {
    assertThat(service.yearOverYear("202609")).isEmpty();
  }

  @Test
  @DisplayName("전년 동월이 통째로 없으면 모든 카테고리가 빠진다")
  void noYearAgo_allCategoriesDropped() {
    month("202609", "101.00");

    assertThat(service.yearOverYear("202609").orElseThrow().rates()).isEmpty();
  }

  @Test
  @DisplayName("전월 대비는 (from, to] 각 달을 직전 달과 비교한다")
  void monthOverMonth_comparesEachMonthWithPrevious() {
    month("202606", "100.00");
    month("202607", "100.00");
    month("202608", "100.00");
    put("202607", "G", "102.00");
    put("202608", "G", "102.00");

    var rates = service.monthOverMonth("202606", "202608");

    assertThat(rates).containsOnlyKeys("202607", "202608");
    ExpenseCategoryEnum gCategory = categoryOf("G");
    assertThat(rates.get("202607").get(gCategory)).isEqualByComparingTo("0.02");
    assertThat(rates.get("202608").get(gCategory)).isEqualByComparingTo("0");
  }

  @Test
  @DisplayName("전월 대비: CPI 행이 없는 달은 키가 없고, 하위 계열이 빠진 카테고리는 값이 없다")
  void monthOverMonth_missingMonthAndSeries() {
    month("202606", "100.00");
    month("202607", "100.00");
    repository.findAll().stream()
        .filter(r -> r.getBaseYearMonth().equals("202607") && r.getCategory().equals("G"))
        .forEach(repository::delete);

    var rates = service.monthOverMonth("202606", "202608");

    assertThat(rates).containsOnlyKeys("202607");
    assertThat(rates.get("202607")).doesNotContainKey(categoryOf("G"));
  }

  @Test
  @DisplayName("최신 CPI 월은 기준월 이하에서 가장 늦은 달이다")
  void latestCpiMonth() {
    month("202608", "100.00");
    month("202609", "100.00");

    assertThat(service.latestCpiMonth("202610")).contains("202609");
    assertThat(service.latestCpiMonth("202607")).isEmpty();
  }

  /** 코드가 속한 카테고리. 매핑 파일이 바뀌어도 테스트가 따라가도록 설정에서 찾는다. */
  private ExpenseCategoryEnum categoryOf(String code) {
    CpiCategoryConfig config =
        new CpiCategoryConfig(
            new ClassPathResource("config/cpi-category-map.yml"),
            new ClassPathResource("config/default-expense-weights.yml"));
    return config.categories().entrySet().stream()
        .filter(e -> e.getValue().subSeries().stream().anyMatch(s -> s.cpiCode().equals(code)))
        .map(java.util.Map.Entry::getKey)
        .findFirst()
        .orElseThrow();
  }
}
