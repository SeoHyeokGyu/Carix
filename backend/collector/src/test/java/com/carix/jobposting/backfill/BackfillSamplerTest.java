package com.carix.jobposting.backfill;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BackfillSamplerTest {

  private static final YearMonth MONTH = YearMonth.of(2026, 3);

  /** 짝수 ID 만 IT 공고, 3의 배수는 빈 페이지, 그 외는 비IT. 게시일은 모두 그 달. */
  private static class FakePages implements BackfillSampler.Pages {
    final List<Long> saved = new ArrayList<>();
    final Set<Long> existing;

    FakePages(Set<Long> existing) {
      this.existing = existing;
    }

    @Override
    public boolean exists(long id) {
      return existing.contains(id);
    }

    @Override
    public Optional<BackfillSampler.Candidate> fetch(long id) {
      if (id % 3 == 0) {
        return Optional.empty();
      }
      return Optional.of(new BackfillSampler.Candidate(id, MONTH.atDay(10), id % 2 == 0));
    }

    @Override
    public void save(BackfillSampler.Candidate candidate, YearMonth month) {
      saved.add(candidate.id());
    }
  }

  @Test
  @DisplayName("IT 공고만 목표 건수까지 저장한다")
  void savesItPostingsUpToTarget() {
    FakePages pages = new FakePages(Set.of());
    BackfillSampler sampler = new BackfillSampler(pages, new Random(1));

    BackfillSampler.Result result = sampler.sample(MONTH, 0, 10_000, 20, 5_000);

    assertThat(result.saved()).isEqualTo(20);
    assertThat(pages.saved).hasSize(20).allMatch(id -> id % 2 == 0);
  }

  @Test
  @DisplayName("요청 상한에 닿으면 목표를 못 채워도 멈춘다")
  void stopsAtRequestCap() {
    FakePages pages = new FakePages(Set.of());
    BackfillSampler sampler = new BackfillSampler(pages, new Random(1));

    BackfillSampler.Result result = sampler.sample(MONTH, 0, 10_000, 1_000, 30);

    assertThat(result.requests()).isEqualTo(30);
    assertThat(result.saved()).isLessThan(30);
  }

  @Test
  @DisplayName("이미 있는 ID 와 같은 ID 는 다시 요청하지 않는다")
  void skipsExistingAndDuplicates() {
    FakePages pages = new FakePages(Set.of(2L, 4L, 6L, 8L));
    BackfillSampler sampler = new BackfillSampler(pages, new Random(1));

    BackfillSampler.Result result = sampler.sample(MONTH, 0, 10, 100, 100);

    assertThat(pages.saved).doesNotContain(2L, 4L, 6L, 8L).doesNotHaveDuplicates();
    assertThat(result.requests()).isLessThanOrEqualTo(10);
  }

  @Test
  @DisplayName("게시일이 그 달이 아니면 저장하지 않는다")
  void rejectsOtherMonth() {
    BackfillSampler.Pages outOfMonth =
        new FakePages(Set.of()) {
          @Override
          public Optional<BackfillSampler.Candidate> fetch(long id) {
            return Optional.of(new BackfillSampler.Candidate(id, LocalDate.of(2026, 4, 1), true));
          }
        };
    BackfillSampler sampler = new BackfillSampler(outOfMonth, new Random(1));

    assertThat(sampler.sample(MONTH, 0, 100, 10, 50).saved()).isZero();
  }
}
