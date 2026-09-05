package com.carix.index.mvi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

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
}
