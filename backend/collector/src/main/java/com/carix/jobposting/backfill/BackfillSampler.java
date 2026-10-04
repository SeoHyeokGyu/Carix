package com.carix.jobposting.backfill;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * 한 달치 ID 구간에서 무작위로 ID 를 골라 목표 건수만큼 대상 공고를 모은다. 요청 상한에 닿거나 구간을 다 뽑으면 목표를 못 채워도 멈춘다.
 *
 * <p>이미 적재된 ID 는 요청하지 않는다. 게시일이 그 달이 아니면(경계 근사 오차) 저장하지 않는다.
 */
public class BackfillSampler {

  /** 표본기가 바깥 세계와 닿는 세 지점. 테스트는 가짜로 바꿔 끼운다. */
  public interface Pages {
    boolean exists(long id);

    Optional<Candidate> fetch(long id);

    void save(Candidate candidate, YearMonth month);
  }

  /** {@code page} 는 저장에 쓰는 원문이며 테스트에서는 비어 있어도 된다. */
  public record Candidate(long id, LocalDate datePosted, boolean target, JobKoreaPage page) {
    public Candidate(long id, LocalDate datePosted, boolean target) {
      this(id, datePosted, target, null);
    }
  }

  public record Result(
      int requests, int saved, int existing, int empty, int otherMonth, int nonTarget) {}

  private final Pages pages;
  private final Random random;

  public BackfillSampler(Pages pages, Random random) {
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
    int existing = 0;
    int empty = 0;
    int otherMonth = 0;
    int nonTarget = 0;

    while (saved < target && requests < maxRequests && tried.size() < span) {
      long id = minId + (long) (random.nextDouble() * span);
      if (!tried.add(id)) {
        continue;
      }
      if (pages.exists(id)) {
        existing++;
        continue;
      }
      requests++;
      Optional<Candidate> fetched = pages.fetch(id);
      if (fetched.isEmpty()) {
        empty++;
        continue;
      }
      Candidate candidate = fetched.get();
      if (!YearMonth.from(candidate.datePosted()).equals(month)) {
        otherMonth++;
        continue;
      }
      if (!candidate.target()) {
        nonTarget++;
        continue;
      }
      pages.save(candidate, month);
      saved++;
    }
    return new Result(requests, saved, existing, empty, otherMonth, nonTarget);
  }
}
