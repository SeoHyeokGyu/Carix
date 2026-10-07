package com.carix.jobposting.backfill;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * 한 달치 ID 구간에서 무작위로 ID 를 골라 목표 건수만큼 대상 공고를 모은다. 요청 상한에 닿거나 구간을 다 뽑으면 목표를 못 채워도 멈춘다.
 *
 * <p>이미 표본인 ID 는 요청하지 않는다. 정기 수집만 된 ID 는 다시 열어 표본으로 표시한다. 게시일이 그 달이 아니면(경계 근사 오차) 저장하지 않는다. 저장이 던지면
 * 그 후보만 {@code errors} 로 세고 계속한다. 다만 저장이 연달아 {@value #MAX_CONSECUTIVE_SAVE_FAILURES} 번 실패하면 스키마·DB
 * 문제로 보고 예외를 그대로 던져 실행을 멈춘다.
 */
@Slf4j
public class BackfillSampler<P> {

  /** 표본기가 바깥 세계와 닿는 세 지점. 테스트는 가짜로 바꿔 끼운다. */
  public interface Pages<P> {
    /** 이미 표본으로 뽑힌 공고. 정기 수집만 된 공고는 여기 해당하지 않는다 — 표본에서 빼면 표본이 치우친다. */
    boolean alreadySampled(long id);

    Optional<Candidate<P>> fetch(long id);

    void save(Candidate<P> candidate, YearMonth month);
  }

  /** {@code payload} 는 저장에 쓰는 원천별 원문이며 테스트에서는 비어 있어도 된다. */
  public record Candidate<P>(long id, LocalDate datePosted, boolean target, P payload) {
    public Candidate(long id, LocalDate datePosted, boolean target) {
      this(id, datePosted, target, null);
    }
  }

  public record Result(
      int requests,
      int saved,
      int alreadySampled,
      int empty,
      int otherMonth,
      int nonTarget,
      int errors) {}

  static final int MAX_CONSECUTIVE_SAVE_FAILURES = 5;

  private final Pages<P> pages;
  private final Random random;

  public BackfillSampler(Pages<P> pages, Random random) {
    this.pages = pages;
    this.random = random;
  }

  /** {@code [minId, maxIdExclusive)} 구간에서 뽑는다. */
  public Result sample(
      YearMonth month, long minId, long maxIdExclusive, int target, int maxRequests) {
    long span = maxIdExclusive - minId;
    Set<Long> tried = new HashSet<>();
    int requests = 0;
    int saved = 0;
    int alreadySampled = 0;
    int empty = 0;
    int otherMonth = 0;
    int nonTarget = 0;
    int errors = 0;
    int consecutiveFailures = 0;
    boolean stackLogged = false;

    while (saved < target && requests < maxRequests && tried.size() < span) {
      long id = minId + (long) (random.nextDouble() * span);
      if (!tried.add(id)) {
        continue;
      }
      if (pages.alreadySampled(id)) {
        alreadySampled++;
        continue;
      }
      requests++;
      Optional<Candidate<P>> fetched = pages.fetch(id);
      if (fetched.isEmpty()) {
        empty++;
        continue;
      }
      Candidate<P> candidate = fetched.get();
      if (!YearMonth.from(candidate.datePosted()).equals(month)) {
        otherMonth++;
        continue;
      }
      if (!candidate.target()) {
        nonTarget++;
        continue;
      }
      try {
        pages.save(candidate, month);
        saved++;
        consecutiveFailures = 0;
      } catch (RuntimeException e) {
        // 한 건의 저장 실패가 그 달 전체 표본을 버리지 않게 세고 넘어간다. 연속 실패는 계통 오류이므로 멈춘다.
        errors++;
        consecutiveFailures++;
        if (consecutiveFailures >= MAX_CONSECUTIVE_SAVE_FAILURES) {
          log.error("[백필] 저장이 연속 {}회 실패해 중단합니다", consecutiveFailures);
          throw new IllegalStateException(
              "표본 저장이 연속 " + MAX_CONSECUTIVE_SAVE_FAILURES + "회 실패해 백필을 중단합니다: " + e.getMessage(),
              e);
        }
        if (stackLogged) {
          log.warn("[백필] ID {} 저장 실패: {}", candidate.id(), e.getMessage());
        } else {
          stackLogged = true;
          log.warn("[백필] ID {} 저장 실패: {}", candidate.id(), e.getMessage(), e);
        }
      }
    }
    return new Result(requests, saved, alreadySampled, empty, otherMonth, nonTarget, errors);
  }
}
