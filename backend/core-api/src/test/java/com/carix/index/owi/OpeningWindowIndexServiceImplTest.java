package com.carix.index.owi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import com.carix.config.IndexThresholdProperties;
import com.carix.index.dto.OwiResult;
import com.carix.index.enums.IndexStatus;
import com.carix.index.mvi.PeerGroupFallbackResolver;
import com.carix.market.MarketAggregate;
import com.carix.market.MarketAggregateRepository;
import com.carix.profile.domain.CareerProfile;
import com.carix.profile.repository.CareerProfileRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OpeningWindowIndexServiceImplTest {

  private static final UUID USER_ID = UUID.randomUUID();
  private static final String BASE_YM = "202608";

  private static final List<String> RECENT_MONTHS = List.of("202605", "202606", "202607");
  private static final List<String> BASELINE_MONTHS =
      List.of(
          "202505", "202506", "202507", "202508", "202509", "202510", "202511", "202512", "202601",
          "202602", "202603", "202604");

  private CareerProfileRepository careerProfileRepository;
  private MarketAggregateRepository marketAggregateRepository;
  private PeerGroupFallbackResolver peerGroupFallbackResolver;
  private OpeningWindowIndexServiceImpl service;

  @BeforeEach
  void setUp() {
    careerProfileRepository = mock(CareerProfileRepository.class);
    marketAggregateRepository = mock(MarketAggregateRepository.class);
    peerGroupFallbackResolver = new PeerGroupFallbackResolver(marketAggregateRepository);

    lenient()
        .when(
            marketAggregateRepository.findTotalPostingAggregates(
                anyString(),
                anyCollection(),
                anyString(),
                anyString(),
                anyCollection(),
                anyString(),
                anyString()))
        .thenReturn(List.of());
    lenient()
        .when(marketAggregateRepository.findLatestAvailableYearMonthForSource(anyString(), any()))
        .thenReturn(Optional.empty());
    lenient()
        .when(marketAggregateRepository.findEarliestAvailableYearMonthForSource(anyString(), any()))
        .thenReturn(Optional.empty());

    service =
        new OpeningWindowIndexServiceImpl(
            careerProfileRepository,
            marketAggregateRepository,
            peerGroupFallbackResolver,
            new IndexThresholdProperties());
  }

  private CareerProfile createProfile(int yearsOfExperience) {
    return CareerProfile.builder()
        .userId(USER_ID)
        .jobFamily("DEVELOPER")
        .yearsOfExperience(yearsOfExperience)
        .region("서울 강남구")
        .employmentType("FULL_TIME")
        .companySize("LARGE")
        .build();
  }

  private MarketAggregate agg(
      String source, String ym, String jobFamily, String bracket, int count) {
    return MarketAggregate.builder()
        .id(UUID.randomUUID())
        .source(source)
        .baseYearMonth(ym)
        .jobFamily(jobFamily)
        .region("ALL")
        .experienceBracket(bracket)
        .employmentType("ALL")
        .companySize("ALL")
        .postingCount(count)
        .totalAggregatePostings(count)
        .demandRate(BigDecimal.ONE)
        .salaryMissingRatePercent(BigDecimal.ZERO)
        .build();
  }

  private void stubTotals(
      String source,
      List<String> months,
      String jobFamily,
      List<String> brackets,
      int totalPostingCount) {
    given(
            marketAggregateRepository.findTotalPostingAggregates(
                eq(source),
                eq(months),
                eq(jobFamily),
                eq("ALL"),
                eq(brackets),
                eq("ALL"),
                eq("ALL")))
        .willReturn(
            totalPostingCount > 0
                ? List.of(
                    agg(
                        source,
                        months.getFirst(),
                        jobFamily,
                        brackets.getFirst(),
                        totalPostingCount))
                : List.of());
  }

  @Test
  @DisplayName("최근 3개월·이전 12개월 표본이 충분하면 동료집단 채용 비중 추이(OWI)를 산출한다")
  void OWI_정상_산출() {
    given(careerProfileRepository.findByUserId(USER_ID)).willReturn(Optional.of(createProfile(5)));

    // 최근 3개월: 전체시장 100건, 동료집단(DEVELOPER / MID) 30건 -> 30.00%
    stubTotals("JOBKOREA", RECENT_MONTHS, "ALL", List.of("ALL"), 100);
    stubTotals("JOBKOREA", RECENT_MONTHS, "DEVELOPER", List.of("MID"), 30);

    // 이전 12개월: 전체시장 400건, 동료집단(DEVELOPER / MID) 80건 -> 20.00%
    stubTotals("JOBKOREA", BASELINE_MONTHS, "ALL", List.of("ALL"), 400);
    stubTotals("JOBKOREA", BASELINE_MONTHS, "DEVELOPER", List.of("MID"), 80);

    OwiResult result = service.calculate(USER_ID, "2026-08");

    // OWI = (30.00 / 20.00) * 100 = 150.00
    assertThat(result.baseYearMonth()).isEqualTo("202608");
    assertThat(result.status()).isEqualTo(IndexStatus.OK);
    assertThat(result.value()).isEqualByComparingTo("150.00");
    assertThat(result.recentSharePercent()).isEqualByComparingTo("30.00");
    assertThat(result.baselineSharePercent()).isEqualByComparingTo("20.00");
    assertThat(result.peerSampleSize()).isEqualTo(30);
    assertThat(result.baselineSampleSize()).isEqualTo(80);
    assertThat(result.marketSampleSize()).isEqualTo(100);
    assertThat(result.fallbackLevel()).isEqualTo(1);
    assertThat(result.appliedConditions()).isEqualTo("전국 / 4~7년 / 회사규모 무관 / 고용형태 무관");
  }

  @Test
  @DisplayName("수집 이력 15개월 전이라 이전 12개월 창 표본이 부족하면 INSUFFICIENT_SAMPLE 과 최근 비중만 반환한다")
  void 이전창_표본부족_최근비중만_유지() {
    given(careerProfileRepository.findByUserId(USER_ID)).willReturn(Optional.of(createProfile(5)));

    // 최근 3개월: 전체시장 100건, 동료집단 25건 -> 25.00%
    stubTotals("JOBKOREA", RECENT_MONTHS, "ALL", List.of("ALL"), 100);
    stubTotals("JOBKOREA", RECENT_MONTHS, "DEVELOPER", List.of("MID"), 25);

    // 이전 12개월: 표본 4건 (< 10), 인접 구간 확장(Level 2)도 6건 (< 10)
    stubTotals("JOBKOREA", BASELINE_MONTHS, "ALL", List.of("ALL"), 50);
    stubTotals("JOBKOREA", BASELINE_MONTHS, "DEVELOPER", List.of("MID"), 4);
    stubTotals(
        "JOBKOREA",
        RECENT_MONTHS,
        "DEVELOPER",
        List.of("ENTRY", "JUNIOR", "MID", "SENIOR", "LEAD"),
        60);
    stubTotals(
        "JOBKOREA",
        BASELINE_MONTHS,
        "DEVELOPER",
        List.of("ENTRY", "JUNIOR", "MID", "SENIOR", "LEAD"),
        6);

    OwiResult result = service.calculate(USER_ID, BASE_YM);

    assertThat(result.status()).isEqualTo(IndexStatus.INSUFFICIENT_SAMPLE);
    assertThat(result.value()).isNull();
    assertThat(result.recentSharePercent()).isEqualByComparingTo("25.00");
    assertThat(result.baselineSharePercent()).isNull();
    assertThat(result.peerSampleSize()).isEqualTo(25);
    assertThat(result.baselineSampleSize()).isEqualTo(4);
    assertThat(result.fallbackLevel()).isEqualTo(1);
    assertThat(result.appliedConditions()).isEqualTo("전국 / 4~7년 / 회사규모 무관 / 고용형태 무관");
  }

  @Test
  @DisplayName("최근 창과 이전 창 모두 표본이 10건 미만이면 최근·이전 비중 모두 null 로 반환한다")
  void 양쪽창_모두_표본부족() {
    given(careerProfileRepository.findByUserId(USER_ID)).willReturn(Optional.of(createProfile(2)));

    // JUNIOR (ENTRY + JUNIOR) Level 1 = 3건, Level 2 (ENTRY + JUNIOR + MID) = 5건 (< 10)
    stubTotals("JOBKOREA", RECENT_MONTHS, "ALL", List.of("ALL"), 40);
    stubTotals("JOBKOREA", RECENT_MONTHS, "DEVELOPER", List.of("ENTRY", "JUNIOR"), 3);
    stubTotals("JOBKOREA", RECENT_MONTHS, "DEVELOPER", List.of("ENTRY", "JUNIOR", "MID"), 5);

    OwiResult result = service.calculate(USER_ID, BASE_YM);

    assertThat(result.status()).isEqualTo(IndexStatus.INSUFFICIENT_SAMPLE);
    assertThat(result.value()).isNull();
    assertThat(result.recentSharePercent()).isNull();
    assertThat(result.baselineSharePercent()).isNull();
    assertThat(result.peerSampleSize()).isEqualTo(5);
    assertThat(result.fallbackLevel()).isEqualTo(2);
    assertThat(result.appliedConditions()).isEqualTo("전국 / 0~7년 / 회사규모 무관 / 고용형태 무관");
  }

  @Test
  @DisplayName("Level 1(본인 경력 구간) 표본이 부족하면 Level 2(인접 경력 구간)로 완화하여 산출한다")
  void 동료집단_Level2_인접경력_완화() {
    given(careerProfileRepository.findByUserId(USER_ID)).willReturn(Optional.of(createProfile(2)));

    // 전체시장
    stubTotals("JOBKOREA", RECENT_MONTHS, "ALL", List.of("ALL"), 100);
    stubTotals("JOBKOREA", BASELINE_MONTHS, "ALL", List.of("ALL"), 200);

    // Level 1: JUNIOR (ENTRY, JUNIOR) -> 최근 6건 (< 10), 이전 15건
    stubTotals("JOBKOREA", RECENT_MONTHS, "DEVELOPER", List.of("ENTRY", "JUNIOR"), 6);
    stubTotals("JOBKOREA", BASELINE_MONTHS, "DEVELOPER", List.of("ENTRY", "JUNIOR"), 15);

    // Level 2: JUNIOR + MID (ENTRY, JUNIOR, MID) -> 최근 20건 (20%), 이전 50건 (25%)
    stubTotals("JOBKOREA", RECENT_MONTHS, "DEVELOPER", List.of("ENTRY", "JUNIOR", "MID"), 20);
    stubTotals("JOBKOREA", BASELINE_MONTHS, "DEVELOPER", List.of("ENTRY", "JUNIOR", "MID"), 50);

    OwiResult result = service.calculate(USER_ID, BASE_YM);

    // OWI = (20% / 25%) * 100 = 80.00
    assertThat(result.status()).isEqualTo(IndexStatus.OK);
    assertThat(result.value()).isEqualByComparingTo("80.00");
    assertThat(result.recentSharePercent()).isEqualByComparingTo("20.00");
    assertThat(result.baselineSharePercent()).isEqualByComparingTo("25.00");
    assertThat(result.fallbackLevel()).isEqualTo(2);
    assertThat(result.appliedConditions()).isEqualTo("전국 / 0~7년 / 회사규모 무관 / 고용형태 무관");
  }

  @Test
  @DisplayName("다중 원천 결합 시 정기 수집 원천(SARAMIN) 가중치를 표본 원천(JOBKOREA) 상한으로 제한한다")
  void 다중원천_SourceCombineWeights_결합() {
    given(careerProfileRepository.findByUserId(USER_ID)).willReturn(Optional.of(createProfile(5)));

    // JOBKOREA (표본 원천): 최근 20 / 100 (20%), 이전 20 / 100 (20%) -> 지수 100.00, 표본 20
    stubTotals("JOBKOREA", RECENT_MONTHS, "ALL", List.of("ALL"), 100);
    stubTotals("JOBKOREA", RECENT_MONTHS, "DEVELOPER", List.of("MID"), 20);
    stubTotals("JOBKOREA", BASELINE_MONTHS, "ALL", List.of("ALL"), 100);
    stubTotals("JOBKOREA", BASELINE_MONTHS, "DEVELOPER", List.of("MID"), 20);

    // SARAMIN (정기 수집 원천): 최근 60 / 150 (40%), 이전 40 / 200 (20%) -> 지수 200.00, 표본 60 (상한 20으로 축소)
    stubTotals("SARAMIN", RECENT_MONTHS, "ALL", List.of("ALL"), 150);
    stubTotals("SARAMIN", RECENT_MONTHS, "DEVELOPER", List.of("MID"), 60);
    stubMarketRows("SARAMIN", BASELINE_MONTHS, BASELINE_MONTHS, 200);
    stubTotals("SARAMIN", BASELINE_MONTHS, "DEVELOPER", List.of("MID"), 40);

    OwiResult result = service.calculate(USER_ID, BASE_YM);

    // JOBKOREA 가중치 20, SARAMIN 가중치 20 (상한 적용) -> 동일 가중 평균
    // 지수 = (100 + 200) / 2 = 150.00
    // 최근 비중 = (20% + 40%) / 2 = 30.00%
    // 이전 비중 = (20% + 20%) / 2 = 20.00%
    assertThat(result.status()).isEqualTo(IndexStatus.OK);
    assertThat(result.value()).isEqualByComparingTo("150.00");
    assertThat(result.recentSharePercent()).isEqualByComparingTo("30.00");
    assertThat(result.baselineSharePercent()).isEqualByComparingTo("20.00");
    assertThat(result.peerSampleSize()).isEqualTo(80);
    assertThat(result.baselineSampleSize()).isEqualTo(60);
  }

  @Test
  @DisplayName("산출 지수가 400을 초과하면 400.00으로, 25 미만이면 25.00으로 클램프한다")
  void 이상치_클램프_검증() {
    given(careerProfileRepository.findByUserId(USER_ID)).willReturn(Optional.of(createProfile(5)));

    // 상한 클램프: 최근 60/100 (60%), 이전 10/100 (10%) -> raw 600.00 -> 400.00
    stubTotals("JOBKOREA", RECENT_MONTHS, "ALL", List.of("ALL"), 100);
    stubTotals("JOBKOREA", RECENT_MONTHS, "DEVELOPER", List.of("MID"), 60);
    stubTotals("JOBKOREA", BASELINE_MONTHS, "ALL", List.of("ALL"), 100);
    stubTotals("JOBKOREA", BASELINE_MONTHS, "DEVELOPER", List.of("MID"), 10);

    OwiResult high = service.calculate(USER_ID, BASE_YM);
    assertThat(high.value()).isEqualByComparingTo("400.00");

    // 하한 클램프: 최근 10/200 (5%), 이전 50/100 (50%) -> raw 10.00 -> 25.00
    stubTotals("JOBKOREA", RECENT_MONTHS, "ALL", List.of("ALL"), 200);
    stubTotals("JOBKOREA", RECENT_MONTHS, "DEVELOPER", List.of("MID"), 10);
    stubTotals("JOBKOREA", BASELINE_MONTHS, "ALL", List.of("ALL"), 100);
    stubTotals("JOBKOREA", BASELINE_MONTHS, "DEVELOPER", List.of("MID"), 50);

    OwiResult low = service.calculate(USER_ID, BASE_YM);
    assertThat(low.value()).isEqualByComparingTo("25.00");
  }

  @Test
  @DisplayName("당월 표본이 아직 없으면 잡코리아 최신 가용 기준월을 사용해 기간 창을 잡는다")
  void 기준월_유효월_해석() {
    given(careerProfileRepository.findByUserId(USER_ID)).willReturn(Optional.of(createProfile(5)));
    given(marketAggregateRepository.findLatestAvailableYearMonthForSource("JOBKOREA", "202609"))
        .willReturn(Optional.of("202608"));

    stubTotals("JOBKOREA", RECENT_MONTHS, "ALL", List.of("ALL"), 100);
    stubTotals("JOBKOREA", RECENT_MONTHS, "DEVELOPER", List.of("MID"), 25);
    stubTotals("JOBKOREA", BASELINE_MONTHS, "ALL", List.of("ALL"), 100);
    stubTotals("JOBKOREA", BASELINE_MONTHS, "DEVELOPER", List.of("MID"), 25);

    OwiResult result = service.calculate(USER_ID, "202609");

    assertThat(result.baseYearMonth()).isEqualTo("202609");
    assertThat(result.status()).isEqualTo(IndexStatus.OK);
    assertThat(result.value()).isEqualByComparingTo("100.00");
  }

  // --- 시점 근거 재료 (c20) ---

  private static final List<String> OWN = List.of("MID");
  private static final List<String> WIDENED = List.of("ENTRY", "JUNIOR", "MID", "SENIOR", "LEAD");

  /** 창 전체를 조회하면 {@code rowMonths} 달의 전체시장 행을 돌려준다. 같은 달을 두 번 넣으면 행이 둘이다. */
  private void stubMarketRows(
      String source, List<String> window, List<String> rowMonths, int totalPostingCount) {
    int perRow = totalPostingCount / rowMonths.size();
    int first = totalPostingCount - perRow * (rowMonths.size() - 1);
    List<MarketAggregate> rows = new java.util.ArrayList<>();
    for (int i = 0; i < rowMonths.size(); i++) {
      rows.add(agg(source, rowMonths.get(i), "ALL", "ALL", i == 0 ? first : perRow));
    }
    given(
            marketAggregateRepository.findTotalPostingAggregates(
                eq(source),
                eq(window),
                eq("ALL"),
                eq("ALL"),
                eq(List.of("ALL")),
                eq("ALL"),
                eq("ALL")))
        .willReturn(rows);
  }

  private void stubFullSource(String source, int recentPeer, int baselinePeer) {
    stubMarketRows(source, RECENT_MONTHS, RECENT_MONTHS, 1000);
    stubMarketRows(source, BASELINE_MONTHS, BASELINE_MONTHS, 4000);
    stubTotals(source, RECENT_MONTHS, "DEVELOPER", OWN, recentPeer);
    stubTotals(source, BASELINE_MONTHS, "DEVELOPER", OWN, baselinePeer);
  }

  @Test
  @DisplayName("표준오차는 결합에 참여한 원천만으로 내고, 빠진 원천은 충족 여부와 완화 단계도 바꾸지 않는다")
  void 표준오차_결합원천만() {
    given(careerProfileRepository.findByUserId(USER_ID)).willReturn(Optional.of(createProfile(5)));
    stubFullSource("JOBKOREA", 120, 400); // 12% / 10% -> 지수 120.00, 가중치 120
    stubFullSource("WANTED", 200, 900); // 20% / 22.5% -> 지수 88.89, 가중치 200
    // 이전 창 동료 5건이라 결합 제외. 월도 하나뿐이고 표본은 가장 크다.
    stubTotals("SARAMIN", RECENT_MONTHS, "ALL", List.of("ALL"), 1000);
    stubTotals("SARAMIN", BASELINE_MONTHS, "ALL", List.of("ALL"), 1000);
    stubTotals("SARAMIN", RECENT_MONTHS, "DEVELOPER", OWN, 500);
    stubTotals("SARAMIN", BASELINE_MONTHS, "DEVELOPER", OWN, 5);
    stubTotals("SARAMIN", RECENT_MONTHS, "DEVELOPER", WIDENED, 500);
    stubTotals("SARAMIN", BASELINE_MONTHS, "DEVELOPER", WIDENED, 5);

    OwiResult result = service.calculate(USER_ID, "2026-08");

    assertThat(result.status()).isEqualTo(IndexStatus.OK);
    // √((120×120)²(1/120+1/400) + (200×88.89)²(1/200+1/900)) ÷ 320 = 6.39
    assertThat(result.standardError()).isEqualByComparingTo("6.39");
    assertThat(result.recentFromMonth()).isEqualTo("202605");
    assertThat(result.recentToMonth()).isEqualTo("202607");
    assertThat(result.windowsFullyCovered()).isTrue();
    assertThat(result.maxFallbackLevel()).isEqualTo(1);
  }

  // 사람인은 첫 수집 달에 공고가 평소의 열 배 들어왔다. 그 한 달이 이전 창 전부가 되면 지수가 비교 대상을 잃는다.
  @Test
  @DisplayName("정기 수집 원천은 이전 창 12개월을 채우기 전에는 결합하지 않는다")
  void 정기수집_12개월_전_제외() {
    given(careerProfileRepository.findByUserId(USER_ID)).willReturn(Optional.of(createProfile(5)));
    stubFullSource("JOBKOREA", 120, 400); // 12% / 10% -> 지수 120.00
    stubMarketRows("SARAMIN", RECENT_MONTHS, RECENT_MONTHS, 1000);
    stubMarketRows("SARAMIN", BASELINE_MONTHS, BASELINE_MONTHS.subList(11, 12), 1800);
    stubTotals("SARAMIN", RECENT_MONTHS, "DEVELOPER", OWN, 500);
    stubTotals("SARAMIN", BASELINE_MONTHS, "DEVELOPER", OWN, 200);

    OwiResult result = service.calculate(USER_ID, "2026-08");

    assertThat(result.value()).isEqualByComparingTo("120.00");
    assertThat(result.windowsFullyCovered()).isTrue();
  }

  @Test
  @DisplayName("표본 원천은 이전 창이 덜 차도 표본 기준만 넘으면 결합한다 — 현재 값이 바뀌지 않아야 한다")
  void 표본원천_부분_이전창_유지() {
    given(careerProfileRepository.findByUserId(USER_ID)).willReturn(Optional.of(createProfile(5)));
    stubFullSource("JOBKOREA", 120, 400);
    stubMarketRows("JOBKOREA", BASELINE_MONTHS, BASELINE_MONTHS.subList(4, 12), 4000);

    OwiResult result = service.calculate(USER_ID, "2026-08");

    assertThat(result.value()).isEqualByComparingTo("120.00");
    assertThat(result.windowsFullyCovered()).isFalse();
  }

  @Test
  @DisplayName("결합 원천의 이전 창이 11개월뿐이면 값은 나와도 두 창을 채운 것이 아니다")
  void 이전창_11개월() {
    given(careerProfileRepository.findByUserId(USER_ID)).willReturn(Optional.of(createProfile(5)));
    stubFullSource("JOBKOREA", 120, 400);
    stubMarketRows("JOBKOREA", BASELINE_MONTHS, BASELINE_MONTHS.subList(1, 12), 4000);

    OwiResult result = service.calculate(USER_ID, "2026-08");

    assertThat(result.status()).isEqualTo(IndexStatus.OK);
    assertThat(result.windowsFullyCovered()).isFalse();
  }

  @Test
  @DisplayName("결합 원천의 최근 창이 2개월뿐이면 백필이 밀린 것이라 두 창을 채운 것이 아니다")
  void 최근창_2개월() {
    given(careerProfileRepository.findByUserId(USER_ID)).willReturn(Optional.of(createProfile(5)));
    stubFullSource("JOBKOREA", 120, 400);
    stubFullSource("WANTED", 100, 400);
    stubMarketRows("WANTED", RECENT_MONTHS, RECENT_MONTHS.subList(0, 2), 1000);

    OwiResult result = service.calculate(USER_ID, "2026-08");

    assertThat(result.status()).isEqualTo(IndexStatus.OK);
    assertThat(result.windowsFullyCovered()).isFalse();
  }

  @Test
  @DisplayName("한 달에 행이 둘이어도 월은 한 번만 센다")
  void 같은달_행_둘() {
    given(careerProfileRepository.findByUserId(USER_ID)).willReturn(Optional.of(createProfile(5)));
    stubFullSource("JOBKOREA", 120, 400);
    List<String> twelveRowsElevenMonths = new java.util.ArrayList<>(BASELINE_MONTHS.subList(1, 12));
    twelveRowsElevenMonths.add(BASELINE_MONTHS.get(1));
    stubMarketRows("JOBKOREA", BASELINE_MONTHS, twelveRowsElevenMonths, 4000);

    OwiResult result = service.calculate(USER_ID, "2026-08");

    assertThat(result.windowsFullyCovered()).isFalse();
  }

  @Test
  @DisplayName("대표 원천이 1단계여도 결합 원천 하나가 2단계면 최대 완화 단계는 2다")
  void 최대_완화단계() {
    given(careerProfileRepository.findByUserId(USER_ID)).willReturn(Optional.of(createProfile(5)));
    stubFullSource("JOBKOREA", 120, 400);
    stubMarketRows("WANTED", RECENT_MONTHS, RECENT_MONTHS, 1000);
    stubMarketRows("WANTED", BASELINE_MONTHS, BASELINE_MONTHS, 4000);
    stubTotals("WANTED", RECENT_MONTHS, "DEVELOPER", OWN, 100);
    stubTotals("WANTED", BASELINE_MONTHS, "DEVELOPER", OWN, 5);
    stubTotals("WANTED", RECENT_MONTHS, "DEVELOPER", WIDENED, 110);
    stubTotals("WANTED", BASELINE_MONTHS, "DEVELOPER", WIDENED, 400);

    OwiResult result = service.calculate(USER_ID, "2026-08");

    assertThat(result.fallbackLevel()).isEqualTo(1);
    assertThat(result.maxFallbackLevel()).isEqualTo(2);
  }

  @Test
  @DisplayName("비교 창 월은 잡코리아 집계로 보정한 유효월 기준이다 — 기준월 11월이어도 6~8월일 수 있다")
  void 창_월_유효월_기준() {
    given(careerProfileRepository.findByUserId(USER_ID)).willReturn(Optional.of(createProfile(5)));
    given(
            marketAggregateRepository.findLatestAvailableYearMonthForSource(
                eq("JOBKOREA"), eq("202611")))
        .willReturn(Optional.of("202609"));

    OwiResult result = service.calculate(USER_ID, "2026-11");

    assertThat(result.baseYearMonth()).isEqualTo("202611");
    assertThat(result.recentFromMonth()).isEqualTo("202606");
    assertThat(result.recentToMonth()).isEqualTo("202608");
  }

  @Test
  @DisplayName("표본 부족이면 창 월만 채우고 결합 원천이 있어야 뜻이 있는 값은 비운다")
  void 표본부족_창월만() {
    given(careerProfileRepository.findByUserId(USER_ID)).willReturn(Optional.of(createProfile(5)));
    stubTotals("JOBKOREA", RECENT_MONTHS, "ALL", List.of("ALL"), 100);
    stubTotals("JOBKOREA", RECENT_MONTHS, "DEVELOPER", OWN, 25);

    OwiResult result = service.calculate(USER_ID, "2026-08");

    assertThat(result.status()).isEqualTo(IndexStatus.INSUFFICIENT_SAMPLE);
    assertThat(result.recentFromMonth()).isEqualTo("202605");
    assertThat(result.standardError()).isNull();
    assertThat(result.windowsFullyCovered()).isNull();
    assertThat(result.maxFallbackLevel()).isNull();
  }
}
