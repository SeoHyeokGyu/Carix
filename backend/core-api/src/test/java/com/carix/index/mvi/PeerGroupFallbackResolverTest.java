package com.carix.index.mvi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.carix.config.IndexThresholdProperties;
import com.carix.market.MarketAggregate;
import com.carix.market.MarketAggregateRepository;
import com.carix.profile.domain.CareerProfile;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
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

  @Test
  @DisplayName("Level 1 완전 일치 표본 수 >= 10이면 Level 1로 즉시 안착해야 한다")
  void testLevel1ExactMatch() {
    // Given
    CareerProfile profile = new CareerProfile(UUID.randomUUID());
    profile.patch("DEVELOPER", 5, "서울 강남구", "FULL_TIME", "MEDIUM");

    MarketAggregate l1Aggregate =
        MarketAggregate.builder()
            .source("JOBKOREA")
            .jobFamily("DEVELOPER")
            .region("서울 강남구")
            .experienceBracket("MID")
            .employmentType("FULL_TIME")
            .companySize("MEDIUM")
            .postingCount(15) // >= 10
            .salaryMissingRatePercent(BigDecimal.valueOf(30.0))
            .build();

    when(marketAggregateRepository.findPeerGroupAggregate(
            "JOBKOREA", "202607", "DEVELOPER", "서울 강남구", "MID", "FULL_TIME", "MEDIUM"))
        .thenReturn(Optional.of(l1Aggregate));

    // When
    PeerGroupFallbackResolver.ResolvedPeerGroup result =
        resolver.resolveForSource("JOBKOREA", profile, "202607");

    // Then
    assertThat(result.isSuccessful()).isTrue();
    assertThat(result.getFallbackLevel()).isEqualTo(1);
    assertThat(result.getSampleCount()).isEqualTo(15);
    assertThat(result.getCoverage().source()).isEqualTo("JOBKOREA");
  }

  @Test
  @DisplayName("동료집단 완화의 최소 표본 기준은 설정값을 적용한다")
  void configuredPeerGroupSampleThresholdIsApplied() {
    CareerProfile profile = new CareerProfile(UUID.randomUUID());
    profile.patch("DEVELOPER", 5, "서울 강남구", "FULL_TIME", "MEDIUM");
    IndexThresholdProperties thresholds = new IndexThresholdProperties();
    thresholds.setPeerGroupMinSampleSize(20);
    ReflectionTestUtils.setField(resolver, "thresholds", thresholds);

    MarketAggregate l1 = MarketAggregate.builder().postingCount(15).build();
    MarketAggregate l2 =
        MarketAggregate.builder()
            .source("JOBKOREA")
            .jobFamily("DEVELOPER")
            .region("서울 강남구")
            .experienceBracket("MID")
            .employmentType("FULL_TIME")
            .postingCount(20)
            .salaryMissingRatePercent(BigDecimal.valueOf(25.0))
            .build();

    when(marketAggregateRepository.findPeerGroupAggregate(
            "JOBKOREA", "202607", "DEVELOPER", "서울 강남구", "MID", "FULL_TIME", "MEDIUM"))
        .thenReturn(Optional.of(l1));
    when(marketAggregateRepository.findPeerGroupAggregate(
            "JOBKOREA", "202607", "DEVELOPER", "서울 강남구", "MID", "FULL_TIME", null))
        .thenReturn(Optional.of(l2));

    PeerGroupFallbackResolver.ResolvedPeerGroup result =
        resolver.resolveForSource("JOBKOREA", profile, "202607");

    assertThat(result.isSuccessful()).isTrue();
    assertThat(result.getFallbackLevel()).isEqualTo(2);
    assertThat(result.getSampleCount()).isEqualTo(20);
  }

  @Test
  @DisplayName("Level 1 표본 부족 시 Level 2(회사규모 IS NULL)로 완화되어야 한다")
  void testLevel2Fallback() {
    // Given
    CareerProfile profile = new CareerProfile(UUID.randomUUID());
    profile.patch("DEVELOPER", 5, "서울 강남구", "FULL_TIME", "MEDIUM");

    // Level 1: 표본 3건 (< 10)
    MarketAggregate l1Aggregate =
        MarketAggregate.builder()
            .source("JOBKOREA")
            .postingCount(3)
            .salaryMissingRatePercent(BigDecimal.valueOf(30.0))
            .build();

    // Level 2: 회사규모 IS NULL 롤업 행 표본 18건 (>= 10)
    MarketAggregate l2Aggregate =
        MarketAggregate.builder()
            .source("JOBKOREA")
            .jobFamily("DEVELOPER")
            .region("서울 강남구")
            .experienceBracket("MID")
            .employmentType("FULL_TIME")
            .postingCount(18)
            .salaryMissingRatePercent(BigDecimal.valueOf(25.0))
            .build();

    when(marketAggregateRepository.findPeerGroupAggregate(
            "JOBKOREA", "202607", "DEVELOPER", "서울 강남구", "MID", "FULL_TIME", "MEDIUM"))
        .thenReturn(Optional.of(l1Aggregate));

    when(marketAggregateRepository.findPeerGroupAggregate(
            "JOBKOREA", "202607", "DEVELOPER", "서울 강남구", "MID", "FULL_TIME", null))
        .thenReturn(Optional.of(l2Aggregate));

    // When
    PeerGroupFallbackResolver.ResolvedPeerGroup result =
        resolver.resolveForSource("JOBKOREA", profile, "202607");

    // Then
    assertThat(result.isSuccessful()).isTrue();
    assertThat(result.getFallbackLevel()).isEqualTo(2);
    assertThat(result.getSampleCount()).isEqualTo(18);
  }

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
  @DisplayName("Level 3 지역 범위 확장으로 완화 성공")
  void testLevel3Fallback() {
    CareerProfile profile = new CareerProfile(UUID.randomUUID());
    profile.patch("DEVELOPER", 5, "경기 성남시", "FULL_TIME", null);

    MarketAggregate l3 =
        MarketAggregate.builder()
            .source("JOBKOREA")
            .jobFamily("DEVELOPER")
            .postingCount(20)
            .salaryMissingRatePercent(BigDecimal.valueOf(10.0))
            .build();

    when(marketAggregateRepository.findPeerGroupAggregate(
            "JOBKOREA", "202607", "DEVELOPER", "경기 성남시", "MID", "FULL_TIME", null))
        .thenReturn(Optional.empty());
    when(marketAggregateRepository.findPeerGroupAggregate(
            "JOBKOREA", "202607", "DEVELOPER", "경기도", "MID", "FULL_TIME", null))
        .thenReturn(Optional.of(l3));

    var result = resolver.resolveForSource("JOBKOREA", profile, "202607");

    assertThat(result.isSuccessful()).isTrue();
    assertThat(result.getFallbackLevel()).isEqualTo(3);
    assertThat(result.getRegion()).isEqualTo("경기도");
  }

  @Test
  @DisplayName("Level 4 경력 구간 확장으로 완화 성공")
  void testLevel4Fallback() {
    CareerProfile profile = new CareerProfile(UUID.randomUUID());
    profile.patch("DEVELOPER", 2, "인천 남동구", "FULL_TIME", null);

    MarketAggregate m1 =
        MarketAggregate.builder()
            .source("JOBKOREA")
            .jobFamily("DEVELOPER")
            .postingCount(6)
            .salaryMissingRatePercent(BigDecimal.valueOf(10.0))
            .build();
    MarketAggregate m2 =
        MarketAggregate.builder()
            .source("JOBKOREA")
            .jobFamily("DEVELOPER")
            .postingCount(7)
            .salaryMissingRatePercent(BigDecimal.valueOf(20.0))
            .build();

    when(marketAggregateRepository.findPeerGroupAggregate(
            "JOBKOREA", "202607", "DEVELOPER", "인천 남동구", "JUNIOR", "FULL_TIME", null))
        .thenReturn(Optional.empty());
    when(marketAggregateRepository.findPeerGroupAggregate(
            "JOBKOREA", "202607", "DEVELOPER", "인천광역시", "JUNIOR", "FULL_TIME", null))
        .thenReturn(Optional.empty());
    when(marketAggregateRepository.findPeerGroupAggregatesByBrackets(
            "JOBKOREA",
            "202607",
            "DEVELOPER",
            "인천광역시",
            List.of("JUNIOR", "MID"),
            "FULL_TIME",
            null))
        .thenReturn(List.of(m1, m2));

    var result = resolver.resolveForSource("JOBKOREA", profile, "202607");

    assertThat(result.isSuccessful()).isTrue();
    assertThat(result.getFallbackLevel()).isEqualTo(4);
    assertThat(result.getSampleCount()).isEqualTo(13);
  }

  @Test
  @DisplayName("Level 5 고용 형태 해제로 완화 성공")
  void testLevel5Fallback() {
    CareerProfile profile = new CareerProfile(UUID.randomUUID());
    profile.patch("DEVELOPER", 10, "부산 해운대구", "CONTRACT", null);

    MarketAggregate m =
        MarketAggregate.builder()
            .source("SARAMIN")
            .jobFamily("DEVELOPER")
            .postingCount(12)
            .salaryMissingRatePercent(BigDecimal.valueOf(15.0))
            .build();

    when(marketAggregateRepository.findPeerGroupAggregate(
            "SARAMIN", "202607", "DEVELOPER", "부산 해운대구", "SENIOR", "CONTRACT", null))
        .thenReturn(Optional.empty());
    when(marketAggregateRepository.findPeerGroupAggregate(
            "SARAMIN", "202607", "DEVELOPER", "부산광역시", "SENIOR", "CONTRACT", null))
        .thenReturn(Optional.empty());
    when(marketAggregateRepository.findPeerGroupAggregatesByBrackets(
            "SARAMIN", "202607", "DEVELOPER", "부산광역시", List.of("MID", "SENIOR"), "CONTRACT", null))
        .thenReturn(List.of());
    when(marketAggregateRepository.findPeerGroupAggregatesByBrackets(
            "SARAMIN", "202607", "DEVELOPER", "부산광역시", List.of("MID", "SENIOR"), null, null))
        .thenReturn(List.of(m));

    var result = resolver.resolveForSource("SARAMIN", profile, "202607");

    assertThat(result.isSuccessful()).isTrue();
    assertThat(result.getFallbackLevel()).isEqualTo(5);
  }

  @Test
  @DisplayName("모든 레벨에서 표본 부족 시 완화 실패(successful=false)")
  void testAllFallbackFailed() {
    CareerProfile profile = new CareerProfile(UUID.randomUUID());
    profile.patch("DEVELOPER", 5, "대구 수성구", "FULL_TIME", null);

    var result = resolver.resolveForSource("JOBKOREA", profile, "202607");

    assertThat(result.isSuccessful()).isFalse();
    assertThat(result.getFallbackLevel()).isEqualTo(5);
    assertThat(result.getSampleCount()).isEqualTo(0);
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

  @Test
  @DisplayName("resolveForSource - 프로필 필드가 null이거나 빈 문자열일 때 기본값 정상 처리")
  void testResolveForSource_defaultStringAndBlankFields() {
    CareerProfile profile = new CareerProfile(UUID.randomUUID());
    profile.patch("", null, "", "", "   ");

    PeerGroupFallbackResolver.ResolvedPeerGroup result =
        resolver.resolveForSource("JOBKOREA", profile, "202608");

    assertThat(result).isNotNull();
    assertThat(result.getJobFamily()).isEqualTo("DEVELOPER");
    assertThat(result.getRegion()).isEqualTo("서울특별시");
    assertThat(result.getBrackets()).contains("JUNIOR");
  }
}
