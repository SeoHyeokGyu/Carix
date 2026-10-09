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
  @DisplayName("조건 문구는 세 구간이 다 모였을 때만 `경력 전체 구간` 이다")
  void testConditionText_twoBracketsIsNotAllBrackets() {
    String juniorMid = resolver.formatConditionText("서울특별시", List.of("JUNIOR", "MID"), null, null);
    String midSenior = resolver.formatConditionText("서울특별시", List.of("MID", "SENIOR"), null, null);
    String allThree =
        resolver.formatConditionText("서울특별시", List.of("JUNIOR", "MID", "SENIOR"), null, null);

    assertThat(juniorMid).doesNotContain("경력 전체 구간").contains("JUNIOR·MID (0~7년)");
    assertThat(midSenior).doesNotContain("경력 전체 구간").contains("MID·SENIOR (4년+)");
    assertThat(allThree).contains("경력 전체 구간");
  }

  @Test
  @DisplayName("단일 브래킷 조건 문구는 그대로 유지된다")
  void testConditionText_singleBracketUnchanged() {
    assertThat(resolver.formatConditionText("서울 강남구", List.of("MID"), "FULL_TIME", "MEDIUM"))
        .isEqualTo("서울 강남구 / MID (4~7년) / MEDIUM / FULL_TIME");
  }

  @Test
  @DisplayName("extractBroadRegion 다양한 시/도 변환")
  void testExtractBroadRegion() {
    assertThat(resolver.extractBroadRegion(null)).isEqualTo("서울특별시");
    assertThat(resolver.extractBroadRegion("")).isEqualTo("서울특별시");
    assertThat(resolver.extractBroadRegion("서울 마포구")).isEqualTo("서울특별시");
    assertThat(resolver.extractBroadRegion("경기 수원시")).isEqualTo("경기도");
    assertThat(resolver.extractBroadRegion("인천 연수구")).isEqualTo("인천광역시");
    assertThat(resolver.extractBroadRegion("부산 진구")).isEqualTo("부산광역시");
    assertThat(resolver.extractBroadRegion("대구 중구")).isEqualTo("대구광역시");
    assertThat(resolver.extractBroadRegion("대전 유성구")).isEqualTo("대전광역시");
    assertThat(resolver.extractBroadRegion("광주 북구")).isEqualTo("광주광역시");
    assertThat(resolver.extractBroadRegion("제주 서귀포시")).isEqualTo("제주 서귀포시");
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

  @Test
  @DisplayName("getAdjacentBrackets - MID 및 SENIOR 인접 구간 확장")
  void testGetAdjacentBrackets() {
    assertThat(resolver.getAdjacentBrackets("JUNIOR")).containsExactly("JUNIOR", "MID");
    assertThat(resolver.getAdjacentBrackets("SENIOR")).containsExactly("MID", "SENIOR");
    assertThat(resolver.getAdjacentBrackets("MID")).containsExactly("JUNIOR", "MID", "SENIOR");
  }

  @Test
  @DisplayName("formatConditionText - 구 미포함 지역 및 브래킷 문구 포맷팅 검증")
  void testFormatConditionText() {
    // 구 미포함 지역
    String t1 = resolver.formatConditionText("경기도", List.of("JUNIOR"), "FULL_TIME", "LARGE");
    assertThat(t1).contains("경기도 전체").contains("JUNIOR (0~3년)");

    // 3개 구간 전체
    String t2 =
        resolver.formatConditionText("서울 강남구", List.of("JUNIOR", "MID", "SENIOR"), null, null);
    assertThat(t2).contains("경력 전체 구간").contains("회사규모 무관").contains("고용형태 무관");

    // 2개 구간 (MID - SENIOR)
    String t3 = resolver.formatConditionText("서울 강남구", List.of("MID", "SENIOR"), "PART_TIME", null);
    assertThat(t3).contains("MID·SENIOR (4년+)");

    // 단일 구간 ENTRY, SENIOR, LEAD, 기타 문자열
    assertThat(resolver.formatConditionText("서울 강남구", List.of("ENTRY"), null, null))
        .contains("ENTRY (0~1년)");
    assertThat(resolver.formatConditionText("서울 강남구", List.of("SENIOR"), null, null))
        .contains("SENIOR (8년+)");
    assertThat(resolver.formatConditionText("서울 강남구", List.of("LEAD"), null, null))
        .contains("LEAD (12년+)");
    assertThat(resolver.formatConditionText("서울 강남구", List.of("OTHER"), null, null))
        .contains("OTHER (OTHER)");

    // 인접 브래킷 범위 포맷팅 분기 커버리지
    assertThat(resolver.formatConditionText("서울 강남구", List.of("ENTRY", "JUNIOR"), null, null))
        .contains("ENTRY·JUNIOR (0~3년)");
    assertThat(resolver.formatConditionText("서울 강남구", List.of("ENTRY", "MID"), null, null))
        .contains("ENTRY·MID (0~7년)");
    assertThat(resolver.formatConditionText("서울 강남구", List.of("SENIOR", "LEAD"), null, null))
        .contains("SENIOR·LEAD (8년+)");
    assertThat(resolver.formatConditionText("서울 강남구", List.of("LEAD", "UNKNOWN"), null, null))
        .contains("LEAD (12년+)");
    assertThat(resolver.formatConditionText("서울 강남구", null, null, null)).contains("경력 전체 구간");
    assertThat(resolver.formatConditionText("서울 강남구", List.of(), null, null)).contains("경력 전체 구간");
    assertThat(resolver.formatConditionText("서울 강남구", List.of("CUSTOM1", "CUSTOM2"), null, null))
        .contains("CUSTOM1·CUSTOM2");
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
    profile.patch("DEVELOPER", years, "서울 강남구", "FULL_TIME", "LARGE");
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
  @DisplayName("경력이 달라도 같은 직무면 같은 동료집단이다")
  void bracketDoesNotChangePeerGroup() {
    givenAllBracket("202607", 170);

    var junior = resolver.resolveForSource("JOBKOREA", profile(2), "202607");
    var senior = resolver.resolveForSource("JOBKOREA", profile(10), "202607");

    assertThat(junior.getBrackets()).isEqualTo(senior.getBrackets()).containsExactly("ALL");
    assertThat(junior.getSampleCount()).isEqualTo(senior.getSampleCount());
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
  @DisplayName("최소 표본 기준은 설정값을 따른다")
  void configuredThresholdApplies() {
    IndexThresholdProperties thresholds = new IndexThresholdProperties();
    thresholds.setPeerGroupMinSampleSize(200);
    ReflectionTestUtils.setField(resolver, "thresholds", thresholds);
    givenAllBracket("202607", 170);

    var result = resolver.resolveForSource("JOBKOREA", profile(5), "202607");

    assertThat(result.isSuccessful()).isFalse();
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
    profile.patch("", null, "", "", "   ");

    var result = resolver.resolveForSource("JOBKOREA", profile, "202608");

    assertThat(result.getJobFamily()).isEqualTo("DEVELOPER");
    assertThat(result.getRegion()).isEqualTo("ALL");
    assertThat(result.getBrackets()).containsExactly("ALL");
  }
}
