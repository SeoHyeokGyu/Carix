package com.carix.jobposting.backfill;

import java.time.LocalDate;
import java.util.Optional;
import java.util.function.LongFunction;

/**
 * 공고 ID 는 등록 순으로 늘어나므로 게시일로 이분 탐색해 월 경계 ID 를 찾는다. 삭제·비공개 ID 는 날짜가 없어 오른쪽으로 몇 칸 비켜 본다.
 *
 * <p>ID 순서와 게시일이 완전히 단조롭지는 않아 경계는 근사다. 표본기가 게시일로 다시 거르므로 경계 오차는 요청 몇 건을 버리는 데 그친다.
 */
public class MonthIdRangeFinder {

  private final LongFunction<Optional<LocalDate>> datePostedOf;
  private final int probeLimit;

  public MonthIdRangeFinder(LongFunction<Optional<LocalDate>> datePostedOf, int probeLimit) {
    this.datePostedOf = datePostedOf;
    this.probeLimit = probeLimit;
  }

  /** {@code [lowId, highId)} 에서 게시일이 {@code date} 이상인 첫 ID. 없으면 {@code highId}. */
  public long findFirstIdOnOrAfter(LocalDate date, long lowId, long highId) {
    long lo = lowId;
    long hi = highId;
    while (lo < hi) {
      long mid = lo + (hi - lo) / 2;
      Probe probe = probe(mid, hi);
      if (probe == null) {
        lo = mid + 1;
      } else if (probe.datePosted().isBefore(date)) {
        lo = probe.id() + 1;
      } else {
        hi = mid;
      }
    }
    return lo;
  }

  private Probe probe(long fromId, long highId) {
    long until = Math.min(highId, fromId + probeLimit);
    for (long id = fromId; id < until; id++) {
      Optional<LocalDate> date = datePostedOf.apply(id);
      if (date.isPresent()) {
        return new Probe(id, date.get());
      }
    }
    return null;
  }

  private record Probe(long id, LocalDate datePosted) {}
}
