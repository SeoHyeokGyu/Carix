package com.carix.income.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.carix.income.domain.PerformanceBonus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PerformanceBonusRepositoryTest {

  @Autowired private PerformanceBonusRepository performanceBonusRepository;

  @Test
  @DisplayName("저장한 금액은 암호화 컨버터를 거쳐 되읽힌다 - 지급월 내림차순, 같은 달 여러 건 허용")
  void 저장한_금액은_암호화_컨버터를_거쳐_되읽힌다() {
    UUID userId = UUID.randomUUID();
    performanceBonusRepository.save(new PerformanceBonus(userId, "202606", 3_000_000L));
    performanceBonusRepository.save(new PerformanceBonus(userId, "202612", 5_000_000L));
    performanceBonusRepository.save(new PerformanceBonus(userId, "202612", 1_000_000L));
    performanceBonusRepository.save(new PerformanceBonus(UUID.randomUUID(), "202701", 9L));
    performanceBonusRepository.flush();

    List<PerformanceBonus> found =
        performanceBonusRepository.findByUserIdOrderByPaidMonthDesc(userId);

    assertThat(found)
        .extracting(PerformanceBonus::getPaidMonth)
        .containsExactly("202612", "202612", "202606");
    assertThat(found)
        .extracting(PerformanceBonus::getAmount)
        .containsExactlyInAnyOrder(5_000_000L, 1_000_000L, 3_000_000L);
  }

  @Test
  @DisplayName("findByIdAndUserId - 다른 사용자의 행은 찾지 못한다")
  void findByIdAndUserId_다른_사용자_행은_비어있다() {
    UUID userId = UUID.randomUUID();
    PerformanceBonus saved =
        performanceBonusRepository.save(new PerformanceBonus(userId, "202606", 3_000_000L));

    assertThat(performanceBonusRepository.findByIdAndUserId(saved.getId(), userId)).isPresent();
    assertThat(performanceBonusRepository.findByIdAndUserId(saved.getId(), UUID.randomUUID()))
        .isEmpty();
  }

  @Test
  @DisplayName("deleteByUserId - 해당 사용자의 성과급만 지운다")
  void deleteByUserId_해당_사용자_행만_지운다() {
    UUID userId = UUID.randomUUID();
    UUID otherId = UUID.randomUUID();
    performanceBonusRepository.save(new PerformanceBonus(userId, "202606", 1L));
    performanceBonusRepository.save(new PerformanceBonus(userId, "202607", 2L));
    performanceBonusRepository.save(new PerformanceBonus(otherId, "202606", 3L));
    performanceBonusRepository.flush();

    performanceBonusRepository.deleteByUserId(userId);
    performanceBonusRepository.flush();

    assertThat(performanceBonusRepository.findByUserIdOrderByPaidMonthDesc(userId)).isEmpty();
    assertThat(performanceBonusRepository.findByUserIdOrderByPaidMonthDesc(otherId)).hasSize(1);
  }
}
