package com.carix.index.mvi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.carix.market.MarketAggregate;
import com.carix.market.MarketAggregateRepository;
import com.carix.profile.domain.CareerProfile;
import java.math.BigDecimal;
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
}
