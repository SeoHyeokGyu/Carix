package com.carix.jobposting.wanted;

import com.carix.jobposting.JobPostingRepository;
import com.carix.jobposting.backfill.BackfillSampler;
import com.carix.jobposting.backfill.MonthIdRangeFinder;
import com.carix.jobposting.normalize.JobFamilyClassifier;
import com.carix.jobposting.normalize.SkillTaxonomy;
import com.carix.jobposting.service.PostingSkillService;
import com.carix.pipeline.dto.BackfillRequest;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.LongFunction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionOperations;

/**
 * 수집 시작 전 달의 원티드 공고를 공고 번호 무작위 표본으로 채운다(잡코리아 백필과 같은 골격). 개발(518) 직군 중 AI·데이터·개발 대표 직무만 표본이다.
 *
 * <p>HTTP 는 트랜잭션 밖에서 끝내고, 공고 하나의 저장만 한 트랜잭션이다. 저장 실패는 그 공고만 {@code errors} 로 세고 달은 계속한다.
 */
@Slf4j
public class WantedBackfillService {

  private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");
  private static final Set<String> TARGET_FAMILIES =
      Set.of(JobFamilyClassifier.AI, JobFamilyClassifier.DATA, JobFamilyClassifier.DEVELOPER);
  private static final int DEFAULT_TARGET = 250;
  private static final int DEFAULT_MAX_REQUESTS = 2_000;
  private static final long DEFAULT_DELAY_MS = 1_000L;
  private static final long DEFAULT_MIN_ID = 280_000L;
  private static final long DEFAULT_MAX_ID = 420_000L;
  private static final int PROBE_LIMIT = 20;

  private final LongFunction<WantedClient> clientFactory;
  private final WantedParser parser;
  private final WantedPostingWriter writer;
  private final TransactionOperations tx;
  private final WantedFamilyResolver families = new WantedFamilyResolver();

  /** {@code clientFactory} 는 요청 간격(ms)을 받아 클라이언트를 만든다. */
  public WantedBackfillService(
      LongFunction<WantedClient> clientFactory,
      WantedParser parser,
      JobPostingRepository postings,
      PostingSkillService skills,
      SkillTaxonomy taxonomy,
      TransactionOperations tx) {
    this.clientFactory = clientFactory;
    this.parser = parser;
    this.writer = new WantedPostingWriter(postings, skills, taxonomy);
    this.tx = tx;
  }

  public Map<String, Object> run(BackfillRequest request) {
    return run(request, (nextStartId, cachedIds) -> {});
  }

  /** {@code afterMonth} 는 달마다 캐시 정리 직후 (다음 달 시작 ID, 남은 캐시 ID) 를 받는다. 캐시 정리 검증용. */
  Map<String, Object> run(BackfillRequest request, BiConsumer<Long, Set<Long>> afterMonth) {
    YearMonth from = YearMonth.parse(request.fromMonth(), YM);
    YearMonth to = YearMonth.parse(request.toMonth(), YM);
    if (from.isAfter(to)) {
      throw new IllegalArgumentException("fromMonth 가 toMonth 보다 늦습니다");
    }
    int target = request.perMonthTarget() != null ? request.perMonthTarget() : DEFAULT_TARGET;
    int maxRequests =
        request.maxRequestsPerMonth() != null
            ? request.maxRequestsPerMonth()
            : DEFAULT_MAX_REQUESTS;
    long minId = request.minId() != null ? request.minId() : DEFAULT_MIN_ID;
    WantedClient client =
        clientFactory.apply(request.delayMs() != null ? request.delayMs() : DEFAULT_DELAY_MS);
    long maxIdExclusive =
        (request.maxId() != null ? request.maxId() : newestPostingIdOrDefault(client)) + 1;

    Run run = new Run(client);
    MonthIdRangeFinder finder =
        new MonthIdRangeFinder(id -> run.page(id).map(WantedPosting::postedDate), PROBE_LIMIT);
    BackfillSampler<WantedPosting> sampler = new BackfillSampler<>(run, new Random());

    Map<String, Object> summary = new LinkedHashMap<>();
    long monthStartId = finder.findFirstIdOnOrAfter(from.atDay(1), minId, maxIdExclusive);
    for (YearMonth month = from; !month.isAfter(to); month = month.plusMonths(1)) {
      long nextStartId =
          finder.findFirstIdOnOrAfter(month.plusMonths(1).atDay(1), monthStartId, maxIdExclusive);
      BackfillSampler.Result result =
          sampler.sample(month, monthStartId, nextStartId, target, maxRequests);
      log.info("[원티드 백필] {} ID [{}, {}) → {}", month.format(YM), monthStartId, nextStartId, result);
      summary.put(month.format(YM), result);
      monthStartId = nextStartId;
      run.evictBefore(nextStartId);
      afterMonth.accept(nextStartId, Set.copyOf(run.pageCache.keySet()));
    }
    summary.put("droppedSkills", run.dropped);
    summary.put("apiMisses", run.apiMisses);
    return summary;
  }

  /** 상한을 지정하지 않으면 목록 API 의 최신 공고 번호를 쓴다. 다음 달 경계가 없는 최근 달이 없는 번호까지 퍼지지 않게 한다. */
  private long newestPostingIdOrDefault(WantedClient client) {
    try {
      return client
          .fetchList(0)
          .map(parser::parseList)
          .flatMap(
              page ->
                  page.items().stream()
                      .map(WantedListItem::id)
                      .filter(id -> id.chars().allMatch(Character::isDigit))
                      .map(Long::parseLong)
                      .max(Long::compare))
          .orElseGet(
              () -> {
                log.warn("[원티드 백필] 목록에서 최신 공고 번호를 못 읽어 기본 상한 {} 을 쓴다", DEFAULT_MAX_ID);
                return DEFAULT_MAX_ID;
              });
    } catch (RuntimeException e) {
      log.warn("[원티드 백필] 목록 해석 실패, 기본 상한 {} 을 쓴다: {}", DEFAULT_MAX_ID, e.getMessage());
      return DEFAULT_MAX_ID;
    }
  }

  /** 한 번의 실행 상태. 같은 ID 의 페이지는 범위 탐색과 표본기가 한 번만 받도록 캐시한다. */
  private final class Run implements BackfillSampler.Pages<WantedPosting> {
    private final WantedClient client;
    private final Map<Long, Optional<WantedPosting>> pageCache = new HashMap<>();
    private final Map<String, Integer> dropped = new TreeMap<>();
    private int apiMisses;

    private Run(WantedClient client) {
      this.client = client;
    }

    Optional<WantedPosting> page(long id) {
      Optional<WantedPosting> cached = pageCache.get(id);
      if (cached == null) {
        // 요청한 ID 와 페이지의 ID 가 다르면(리다이렉트 등) 다른 공고를 이 ID 로 저장하지 않는다.
        cached =
            client
                .fetchPage(id)
                .flatMap(parser::parsePage)
                .filter(p -> String.valueOf(id).equals(p.id()));
        pageCache.put(id, cached);
      }
      return cached;
    }

    /** 다음 달 시작 ID 보다 작은 페이지는 다시 읽지 않으므로 메모리에서 뺀다. */
    void evictBefore(long nextStartId) {
      pageCache.keySet().removeIf(id -> id < nextStartId);
    }

    @Override
    public boolean alreadySampled(long id) {
      return Boolean.TRUE.equals(tx.execute(s -> writer.isSampled(String.valueOf(id))));
    }

    @Override
    public Optional<BackfillSampler.Candidate<WantedPosting>> fetch(long id) {
      return page(id)
          .map(
              p ->
                  new BackfillSampler.Candidate<>(
                      id,
                      p.postedDate(),
                      p.parentTagId() == WantedIngestService.DEVELOPMENT_PARENT_TAG
                          && TARGET_FAMILIES.contains(families.resolve(p.childTagIds(), p.title())),
                      p));
    }

    @Override
    public void save(BackfillSampler.Candidate<WantedPosting> candidate, YearMonth month) {
      WantedPosting posting = candidate.payload();
      // 이미 있는 행은 표본 표시만 달면 되므로 상세 API 를 부르지 않는다.
      boolean existing =
          Boolean.TRUE.equals(tx.execute(s -> writer.exists(String.valueOf(candidate.id()))));
      List<String> skillTags =
          existing
              ? List.of()
              : client.fetchDetailApi(candidate.id()).map(parser::parseSkillTags).orElse(List.of());
      // 실패한 공고의 미등록 스택은 총계에 넣지 않으므로 공고별로 모았다가 성공 뒤에 합친다.
      Map<String, Integer> postingDropped = new TreeMap<>();
      tx.execute(
          s -> {
            postingDropped.clear();
            return writer.upsertSampled(posting, skillTags, month, postingDropped);
          });
      postingDropped.forEach((name, count) -> dropped.merge(name, count, Integer::sum));
      if (!existing && skillTags.isEmpty()) {
        apiMisses++;
      }
    }
  }
}
