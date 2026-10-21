package com.carix.income.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.carix.income.domain.PerformanceBonus;
import com.carix.income.dto.PerformanceBonusRequest;
import com.carix.income.dto.PerformanceBonusResponse;
import com.carix.income.exception.InvalidPaidMonthException;
import com.carix.income.exception.PerformanceBonusNotFoundException;
import com.carix.income.repository.PerformanceBonusRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PerformanceBonusServiceTest {

  private static final UUID USER_ID = UUID.randomUUID();
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-21T03:00:00Z"), SEOUL);

  private PerformanceBonusRepository repository;
  private PerformanceBonusService service;

  @BeforeEach
  void setUp() {
    repository = mock(PerformanceBonusRepository.class);
    given(repository.save(any(PerformanceBonus.class))).willAnswer(i -> i.getArgument(0));
    service = new PerformanceBonusService(repository, CLOCK);
  }

  @Test
  @DisplayName("목록은 지급월 내림차순")
  void 목록은_지급월_내림차순() {
    given(repository.findByUserIdOrderByPaidMonthDesc(USER_ID))
        .willReturn(
            List.of(
                new PerformanceBonus(USER_ID, "202607", 3_000_000L),
                new PerformanceBonus(USER_ID, "202601", 5_000_000L)));

    List<PerformanceBonusResponse> result = service.list(USER_ID);

    assertThat(result)
        .extracting(PerformanceBonusResponse::paidMonth)
        .containsExactly("202607", "202601");
    assertThat(result.get(0).amount()).isEqualTo(3_000_000L);
  }

  @Test
  @DisplayName("현재 월까지는 기록할 수 있다")
  void 현재_월은_기록된다() {
    PerformanceBonusResponse created =
        service.create(USER_ID, new PerformanceBonusRequest("202610", 1_000_000L));

    assertThat(created.paidMonth()).isEqualTo("202610");
    assertThat(created.amount()).isEqualTo(1_000_000L);
  }

  @Test
  @DisplayName("미래 지급월은 거부한다")
  void 미래_지급월은_400() {
    assertThatThrownBy(() -> service.create(USER_ID, new PerformanceBonusRequest("202611", 1L)))
        .isInstanceOf(InvalidPaidMonthException.class);
    verify(repository, never()).save(any());
  }

  @Test
  @DisplayName("월이 13이면 거부한다")
  void 월이_13이면_400() {
    assertThatThrownBy(() -> service.create(USER_ID, new PerformanceBonusRequest("202613", 1L)))
        .isInstanceOf(InvalidPaidMonthException.class);
    assertThatThrownBy(() -> service.create(USER_ID, new PerformanceBonusRequest("202600", 1L)))
        .isInstanceOf(InvalidPaidMonthException.class);
    verify(repository, never()).save(any());
  }

  @Test
  @DisplayName("남의 기록 삭제는 404")
  void 남의_기록_삭제는_404() {
    UUID id = UUID.randomUUID();
    given(repository.findByIdAndUserId(id, USER_ID)).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.delete(USER_ID, id))
        .isInstanceOf(PerformanceBonusNotFoundException.class);
    verify(repository, never()).delete(any(PerformanceBonus.class));
  }

  @Test
  @DisplayName("본인 기록은 삭제된다")
  void 본인_기록은_삭제된다() {
    UUID id = UUID.randomUUID();
    PerformanceBonus own = new PerformanceBonus(USER_ID, "202601", 1L);
    given(repository.findByIdAndUserId(id, USER_ID)).willReturn(Optional.of(own));

    service.delete(USER_ID, id);

    verify(repository).delete(own);
  }
}
