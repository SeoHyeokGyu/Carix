package com.carix.index.mvi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carix.config.IndexThresholdProperties;
import com.carix.market.MarketAggregate;
import com.carix.market.MarketAggregateRepository;
import com.carix.profile.domain.CareerProfile;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PeerGroupFallbackResolverTest {

  @Mock private MarketAggregateRepository marketAggregateRepository;

  @InjectMocks private PeerGroupFallbackResolver resolver;

  // ---------- Level 4 인접 브래킷 (도메인 정의서 §2.1) ----------

  @Test
  @DisplayName("Level 4 는 인접 브래킷만 더한다 — JUNIOR 에 SENIOR 를 섞지 않는다")
  void testAdjacentBrackets_junior() {
    assertThat(resolver.getAdjacentBrackets("JUNIOR")).containsExactly("JUNIOR", "MID");
  }

  @Test
  @DisplayName("Level 4 는 인접 브래킷만 더한다 — SENIOR 에 JUNIOR 를 섞지 않는다")
  void testAdjacentBrackets_senior() {
    assertThat(resolver.getAdjacentBrackets("SENIOR")).containsExactly("MID", "SENIOR");
  }

  @Test
  @DisplayName("MID 는 양쪽이 모두 인접이라 세 구간이 된다 — 도메인 정의서 §2.1 의 예시다")
  void testAdjacentBrackets_mid() {
    assertThat(resolver.getAdjacentBrackets("MID")).containsExactly("JUNIOR", "MID", "SENIOR");
  }

  @Test
  @DisplayName("mapExperienceBracket 연차 매핑 (기존 3구간 호환)")
  void testMapExperienceBracket() {
    assertThat(resolver.mapExperienceBracket(0)).isEqualTo("JUNIOR");
    assertThat(resolver.mapExperienceBracket(3)).isEqualTo("JUNIOR");
    assertThat(resolver.mapExperienceBracket(4)).isEqualTo("MID");
    assertThat(resolver.mapExperienceBracket(7)).isEqualTo("MID");
    assertThat(resolver.mapExperienceBracket(8)).isEqualTo("SENIOR");
  }

  @Test
  @DisplayName("mapExperienceBracket6 6구간 연차 매핑")
  void testMapExperienceBracket6() {
    assertThat(resolver.mapExperienceBracket6(0)).isEqualTo("ENTRY");
    assertThat(resolver.mapExperienceBracket6(1)).isEqualTo("ENTRY");
    assertThat(resolver.mapExperienceBracket6(2)).isEqualTo("JUNIOR");
    assertThat(resolver.mapExperienceBracket6(3)).isEqualTo("JUNIOR");
    assertThat(resolver.mapExperienceBracket6(4)).isEqualTo("MID");
    assertThat(resolver.mapExperienceBracket6(7)).isEqualTo("MID");
    assertThat(resolver.mapExperienceBracket6(8)).isEqualTo("SENIOR");
    assertThat(resolver.mapExperienceBracket6(11)).isEqualTo("SENIOR");
    assertThat(resolver.mapExperienceBracket6(12)).isEqualTo("LEAD");
    assertThat(resolver.mapExperienceBracket6(15)).isEqualTo("LEAD");
  }

  @Test
  @DisplayName("to3TierBracket 3구간 롤업 매핑")
  void testTo3TierBracket() {
    assertThat(resolver.to3TierBracket("ENTRY")).isEqualTo("JUNIOR");
    assertThat(resolver.to3TierBracket("JUNIOR")).isEqualTo("JUNIOR");
    assertThat(resolver.to3TierBracket("MID")).isEqualTo("MID");
    assertThat(resolver.to3TierBracket("SENIOR")).isEqualTo("SENIOR");
    assertThat(resolver.to3TierBracket("LEAD")).isEqualTo("SENIOR");
    assertThat(resolver.to3TierBracket(null)).isEqualTo("JUNIOR");
    assertThat(resolver.to3TierBracket("UNKNOWN")).isEqualTo("UNKNOWN");
  }

  // ---------- MVI 동료집단: 직무 × 경력만 실재한다 ----------
  //
  // market_aggregate 는 지역·고용형태·회사규모를 ALL 로만 집계한다. 해석기는 그 세 차원을 ALL 로
  // 명시해 조회하고, 비교 조건 문구도 실제 조회 조건(전국 / 경력 / 무관 / 무관)으로 쓴다.

  private static MarketAggregate row(String bracket, int count) {
    return MarketAggregate.builder()
        .source("JOBKOREA")
        .jobFamily("DEVELOPER")
        .region("ALL")
        .experienceBracket(bracket)
        .employmentType("ALL")
        .companySize("ALL")
        .postingCount(count)
        .salaryMissingRatePercent(BigDecimal.valueOf(30.0))
        .build();
  }

  private static CareerProfile profile(int years) {
    CareerProfile profile = new CareerProfile(UUID.randomUUID());
    profile.patch("DEVELOPER", years, "서울 강남구", "FULL_TIME", "LARGE", null);
    return profile;
  }

  private void givenAllBracket(String month, int count) {
    when(marketAggregateRepository.findPeerGroupAggregatesByBrackets(
            "JOBKOREA", month, "DEVELOPER", "ALL", List.of("ALL"), "ALL", "ALL"))
        .thenReturn(List.of(row("ALL", count)));
  }

  @Test
  @DisplayName("스킬 수요율은 직무 단위(경력 전체)로 잰다 — 지역·고용형태·규모도 ALL 이다")
  void queriesJobFamilyAcrossAllBrackets() {
    givenAllBracket("202607", 170);

    var result = resolver.resolveForSource("JOBKOREA", profile(5), "202607");

    assertThat(result.isSuccessful()).isTrue();
    assertThat(result.getFallbackLevel()).isEqualTo(1);
    assertThat(result.getSampleCount()).isEqualTo(170);
    assertThat(result.getBrackets()).containsExactly("ALL");
    assertThat(result.getRegion()).isEqualTo("ALL");
    assertThat(result.getEmploymentType()).isEqualTo("ALL");
    assertThat(result.getCompanySize()).isEqualTo("ALL");
  }

  @Test
  @DisplayName("비교 조건 문구는 실제 조회 조건이다 — 경력 전체")
  void conditionTextIsActualQuery() {
    givenAllBracket("202607", 170);

    var result = resolver.resolveForSource("JOBKOREA", profile(5), "202607");

    assertThat(result.getCoverage().appliedConditions())
        .isEqualTo("전국 / 경력 전체 / 회사규모 무관 / 고용형태 무관");
    assertThat(result.getCoverage().appliedConditions())
        .doesNotContain("서울", "LARGE", "FULL_TIME", "MID", "4~7년");
  }

  @Test
  @DisplayName("직무 전체도 표본이 부족하면 실패 — 단계는 1")
  void failsWhenJobFamilyUnderSampled() {
    givenAllBracket("202607", 4);

    var result = resolver.resolveForSource("JOBKOREA", profile(5), "202607");

    assertThat(result.isSuccessful()).isFalse();
    assertThat(result.getFallbackLevel()).isEqualTo(1);
    assertThat(result.getSampleCount()).isEqualTo(4);
    assertThat(result.getCoverage().appliedConditions())
        .isEqualTo("전국 / 경력 전체 / 회사규모 무관 / 고용형태 무관");
  }

  @Test
  @DisplayName("resolve(ALL) 은 잡코리아가 실패하면 사람인을 쓰고 원티드·점핏은 보지 않는다")
  void resolveAllFallsBackToSaraminOnly() {
    when(marketAggregateRepository.findPeerGroupAggregatesByBrackets(
            "JOBKOREA", "202607", "DEVELOPER", "ALL", List.of("ALL"), "ALL", "ALL"))
        .thenReturn(List.of());
    when(marketAggregateRepository.findPeerGroupAggregatesByBrackets(
            "SARAMIN", "202607", "DEVELOPER", "ALL", List.of("ALL"), "ALL", "ALL"))
        .thenReturn(List.of(row("ALL", 170)));

    var result = resolver.resolve("ALL", "202607", profile(5));

    assertThat(result.isSuccessful()).isTrue();
    assertThat(result.getSource()).isEqualTo("SARAMIN");
    verify(marketAggregateRepository, never())
        .findPeerGroupAggregatesByBrackets(eq("JUMPIT"), any(), any(), any(), any(), any(), any());
    verify(marketAggregateRepository, never())
        .findPeerGroupAggregatesByBrackets(eq("WANTED"), any(), any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("프로필 직무가 비어도 기본 직무로 조회한다")
  void blankProfileUsesDefaults() {
    CareerProfile profile = new CareerProfile(UUID.randomUUID());
    profile.patch("", null, "", "", "   ", null);

    var result = resolver.resolveForSource("JOBKOREA", profile, "202608");

    assertThat(result.getJobFamily()).isEqualTo("DEVELOPER");
    assertThat(result.getRegion()).isEqualTo("ALL");
    assertThat(result.getBrackets()).containsExactly("ALL");
  }

  @Test
  @DisplayName("b25: normalizeRegion 은 다양한 지역 표기(구 단위, 정식 명칭, 레거시 영문)를 17개 시도 코드로 통일한다")
  void testNormalizeRegion() {
    assertThat(PeerGroupFallbackResolver.normalizeRegion("서울 강남구")).isEqualTo("서울");
    assertThat(PeerGroupFallbackResolver.normalizeRegion("서울특별시")).isEqualTo("서울");
    assertThat(PeerGroupFallbackResolver.normalizeRegion("서울시")).isEqualTo("서울");
    assertThat(PeerGroupFallbackResolver.normalizeRegion("SEOUL_GANGNAM")).isEqualTo("서울");
    assertThat(PeerGroupFallbackResolver.normalizeRegion("경기 성남시 분당구")).isEqualTo("경기");
    assertThat(PeerGroupFallbackResolver.normalizeRegion("경기도")).isEqualTo("경기");
    assertThat(PeerGroupFallbackResolver.normalizeRegion("세종특별자치시")).isEqualTo("세종");
    assertThat(PeerGroupFallbackResolver.normalizeRegion("부산전체")).isEqualTo("부산");
    assertThat(PeerGroupFallbackResolver.normalizeRegion("전남광주 광산구")).isEqualTo("광주");
    assertThat(PeerGroupFallbackResolver.normalizeRegion("전남광주 담양군")).isEqualTo("전남");
    assertThat(PeerGroupFallbackResolver.normalizeRegion("전남광주")).isEqualTo("ALL");
    assertThat(PeerGroupFallbackResolver.normalizeRegion("제주특별자치도")).isEqualTo("제주");
    assertThat(PeerGroupFallbackResolver.normalizeRegion("강원특별자치도")).isEqualTo("강원");
    assertThat(PeerGroupFallbackResolver.normalizeRegion("충청북도")).isEqualTo("충북");
    assertThat(PeerGroupFallbackResolver.normalizeRegion("전북특별자치도")).isEqualTo("전북");
    assertThat(PeerGroupFallbackResolver.normalizeRegion("경상남도")).isEqualTo("경남");
    assertThat(PeerGroupFallbackResolver.normalizeRegion("해외")).isEqualTo("ALL");
    assertThat(PeerGroupFallbackResolver.normalizeRegion("전국")).isEqualTo("ALL");
    assertThat(PeerGroupFallbackResolver.normalizeRegion("ALL")).isEqualTo("ALL");
    assertThat(PeerGroupFallbackResolver.normalizeRegion("")).isEqualTo("ALL");
    assertThat(PeerGroupFallbackResolver.normalizeRegion(null)).isEqualTo("ALL");
  }

  @Test
  @DisplayName("b25: formatConditionText 는 다차원 조건(지역, 규모, 고용형태)의 모든 옵션을 올바르게 한글 라벨로 조립한다")
  void testFormatConditionText() {
    String text1 = resolver.formatConditionText("서울", List.of("MID"), "LARGE", "FULL_TIME");
    assertThat(text1).isEqualTo("서울 / 4~7년 / 대기업 / 정규직");

    String text2 = resolver.formatConditionText("ALL", List.of("JUNIOR", "MID"), "ALL", "ALL");
    assertThat(text2).isEqualTo("전국 / 0~7년 / 회사규모 무관 / 고용형태 무관");

    assertThat(resolver.formatCompanySizeText("MEDIUM")).isEqualTo("중소·중견기업");
    assertThat(resolver.formatCompanySizeText("STARTUP")).isEqualTo("스타트업");
    assertThat(resolver.formatCompanySizeText("ETC")).isEqualTo("ETC");
    assertThat(resolver.formatCompanySizeText(null)).isEqualTo("회사규모 무관");

    assertThat(resolver.formatEmploymentTypeText("CONTRACT")).isEqualTo("계약직");
    assertThat(resolver.formatEmploymentTypeText("FREELANCE")).isEqualTo("프리랜서");
    assertThat(resolver.formatEmploymentTypeText("INTERN")).isEqualTo("인턴");
    assertThat(resolver.formatEmploymentTypeText("ETC")).isEqualTo("ETC");
    assertThat(resolver.formatEmploymentTypeText(null)).isEqualTo("고용형태 무관");
  }

  @Test
  @DisplayName("thresholds 주입 시 최소 표본 수가 변경되고 분기 처리가 정상 동작한다")
  void testMinSampleSizeWithCustomThresholds() {
    IndexThresholdProperties props = new IndexThresholdProperties();
    props.setPeerGroupMinSampleSize(25);
    ReflectionTestUtils.setField(resolver, "thresholds", props);

    givenAllBracket("202607", 20); // 25 미만이므로 실패해야 함
    var result = resolver.resolveForSource("JOBKOREA", profile(5), "202607");
    assertThat(result.isSuccessful()).isFalse();

    // 원복
    ReflectionTestUtils.setField(resolver, "thresholds", null);
  }

  @Test
  @DisplayName("resolve 메서드의 source 인자 분기 (null, 특정 원천, 둘 다 실패 케이스)")
  void testResolveSourceBranches() {
    // 1) source == null -> 잡코리아 성공 시 잡코리아 반환
    when(marketAggregateRepository.findPeerGroupAggregatesByBrackets(
            "JOBKOREA", "202607", "DEVELOPER", "ALL", List.of("ALL"), "ALL", "ALL"))
        .thenReturn(List.of(row("ALL", 20)));
    var r1 = resolver.resolve(null, "202607", profile(5));
    assertThat(r1.isSuccessful()).isTrue();
    assertThat(r1.getSource()).isEqualTo("JOBKOREA");

    // 2) 특정 소스 직접 지정 ("SARAMIN")
    when(marketAggregateRepository.findPeerGroupAggregatesByBrackets(
            "SARAMIN", "202607", "DEVELOPER", "ALL", List.of("ALL"), "ALL", "ALL"))
        .thenReturn(List.of(row("ALL", 15)));
    var r2 = resolver.resolve("SARAMIN", "202607", profile(5));
    assertThat(r2.isSuccessful()).isTrue();
    assertThat(r2.getSource()).isEqualTo("SARAMIN");

    // 3) source == "ALL" 인데 잡코리아와 사람인 둘 다 실패한 경우 -> jk 반환
    when(marketAggregateRepository.findPeerGroupAggregatesByBrackets(
            "JOBKOREA", "202607", "DEVELOPER", "ALL", List.of("ALL"), "ALL", "ALL"))
        .thenReturn(List.of(row("ALL", 2)));
    when(marketAggregateRepository.findPeerGroupAggregatesByBrackets(
            "SARAMIN", "202607", "DEVELOPER", "ALL", List.of("ALL"), "ALL", "ALL"))
        .thenReturn(List.of(row("ALL", 3)));
    var r3 = resolver.resolve("ALL", "202607", profile(5));
    assertThat(r3.isSuccessful()).isFalse();
    assertThat(r3.getSource()).isEqualTo("JOBKOREA");
  }

  @Test
  @DisplayName("toCollectorBrackets 분기 검증 (null, empty, ALL 포함, 정상 목록)")
  void testToCollectorBracketsBranches() {
    assertThat(resolver.toCollectorBrackets(null)).containsExactly("ALL");
    assertThat(resolver.toCollectorBrackets(List.of())).containsExactly("ALL");
    assertThat(resolver.toCollectorBrackets(List.of("ALL"))).containsExactly("ALL");
    assertThat(resolver.toCollectorBrackets(List.of("JUNIOR", "MID")))
        .containsExactly("ENTRY", "JUNIOR", "MID");
    assertThat(resolver.toCollectorBrackets(List.of("SENIOR"))).containsExactly("SENIOR", "LEAD");
  }

  @Test
  @DisplayName("formatBracketYears 및 formatBracketRange 모든 브랜치 검증")
  void testBracketFormattingBranches() {
    // formatBracketYears private 메서드 검증
    assertThat(ReflectionTestUtils.<String>invokeMethod(resolver, "formatBracketYears", "ENTRY"))
        .isEqualTo("0~1년");
    assertThat(ReflectionTestUtils.<String>invokeMethod(resolver, "formatBracketYears", "JUNIOR"))
        .isEqualTo("0~3년");
    assertThat(ReflectionTestUtils.<String>invokeMethod(resolver, "formatBracketYears", "MID"))
        .isEqualTo("4~7년");
    assertThat(ReflectionTestUtils.<String>invokeMethod(resolver, "formatBracketYears", "SENIOR"))
        .isEqualTo("8년+");
    assertThat(ReflectionTestUtils.<String>invokeMethod(resolver, "formatBracketYears", "LEAD"))
        .isEqualTo("12년+");
    assertThat(ReflectionTestUtils.<String>invokeMethod(resolver, "formatBracketYears", "CUSTOM"))
        .isEqualTo("CUSTOM");

    // formatBracketRange private 메서드 검증
    assertThat(
            ReflectionTestUtils.<String>invokeMethod(
                resolver, "formatBracketRange", List.of("ENTRY", "JUNIOR")))
        .isEqualTo("0~3년");
    assertThat(
            ReflectionTestUtils.<String>invokeMethod(
                resolver, "formatBracketRange", List.of("MID", "MID")))
        .isEqualTo("4~7년");
    assertThat(
            ReflectionTestUtils.<String>invokeMethod(
                resolver, "formatBracketRange", List.of("MID", "SENIOR")))
        .isEqualTo("4년+");
    assertThat(
            ReflectionTestUtils.<String>invokeMethod(
                resolver, "formatBracketRange", List.of("SENIOR", "LEAD")))
        .isEqualTo("8년+");
    assertThat(
            ReflectionTestUtils.<String>invokeMethod(
                resolver, "formatBracketRange", List.of("LEAD", "LEAD")))
        .isEqualTo("12년+");
  }

  @Test
  @DisplayName("buildCompositeResolvedResult 표본수 0건 시 결측률 100% 분기 검증")
  void testBuildCompositeResolvedResultZeroSample() {
    PeerGroupFallbackResolver.ResolvedPeerGroup result =
        ReflectionTestUtils.invokeMethod(
            resolver,
            "buildCompositeResolvedResult",
            "JOBKOREA",
            1,
            List.of(row("ALL", 0)),
            List.of("ALL"));
    assertThat(result).isNotNull();
    assertThat(result.getCoverage().salaryMissingRatePercent())
        .isEqualByComparingTo(BigDecimal.valueOf(100.00));
  }

  @Test
  @DisplayName("normalizeRegion 추가 분기 검증 (SEOUL, GYEONGGI 단독, 전남광주 비구 단위 등)")
  void testNormalizeRegionAdditionalBranches() {
    assertThat(PeerGroupFallbackResolver.normalizeRegion("SEOUL")).isEqualTo("서울");
    assertThat(PeerGroupFallbackResolver.normalizeRegion("GYEONGGI")).isEqualTo("경기");
    assertThat(PeerGroupFallbackResolver.normalizeRegion("GYEONGGI_SUWON")).isEqualTo("경기");
    assertThat(PeerGroupFallbackResolver.normalizeRegion("전체")).isEqualTo("ALL");
    assertThat(PeerGroupFallbackResolver.normalizeRegion("전남광주 송정면")).isEqualTo("전남");
  }
}
