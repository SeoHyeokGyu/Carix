package com.carix.jobposting.backfill;

import com.carix.jobposting.JobPosting;
import com.carix.jobposting.JobPostingRepository;
import com.carix.jobposting.normalize.JobFamilyClassifier;
import com.carix.jobposting.normalize.JobKoreaFamilyResolver;
import com.carix.jobposting.service.JobPostingIngestService;
import com.carix.pipeline.dto.BackfillRequest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 수집 시작 전 달의 공고를 잡코리아 상세페이지 ID 표본으로 채운다. MVI 의 직전 12개월 창이 비어 계산되지 않는 문제를 메우려는 초기 데이터다.
 *
 * <p>목록 크롤러가 매달 보는 「그 달 새로 열린 공고」와 같은 뜻이 되도록 게시일이 속한 달을 first·last seen 으로 둔다. 표본이라 건수는 실제 공고 수가
 * 아니다 — 수요율(스킬 보유 공고 ÷ 전체)만 의미가 있다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobKoreaBackfillService {

  static final String SOURCE = "JOBKOREA";
  static final Set<String> TARGET_FAMILIES =
      Set.of(JobFamilyClassifier.AI, JobFamilyClassifier.DATA, JobFamilyClassifier.DEVELOPER);

  private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");
  private static final int DEFAULT_TARGET = 250;
  private static final int DEFAULT_MAX_REQUESTS = 5_000;
  private static final long DEFAULT_DELAY_MS = 500;
  private static final long DEFAULT_MIN_ID = 47_600_000L;
  private static final long DEFAULT_MAX_ID = 50_200_000L;
  private static final int PROBE_LIMIT = 20;

  private final JobPostingRepository jobPostingRepository;
  private final JobPostingIngestService jobPostingIngestService;

  private final JobKoreaJsonLdParser jsonLdParser = new JobKoreaJsonLdParser();
  private final JobKoreaFamilyResolver familyResolver = JobKoreaFamilyResolver.bundled();

  public Map<String, Object> run(BackfillRequest request) {
    YearMonth from = YearMonth.parse(request.fromMonth(), YM);
    YearMonth to = YearMonth.parse(request.toMonth(), YM);
    if (from.isAfter(to)) {
      throw new IllegalArgumentException("fromMonth 가 toMonth 보다 늦습니다");
    }
    int target = orDefault(request.perMonthTarget(), DEFAULT_TARGET);
    int maxRequests = orDefault(request.maxRequestsPerMonth(), DEFAULT_MAX_REQUESTS);
    long minId = request.minId() != null ? request.minId() : DEFAULT_MIN_ID;
    long maxIdExclusive = (request.maxId() != null ? request.maxId() : DEFAULT_MAX_ID) + 1;
    JobKoreaPageFetcher fetcher =
        new JobKoreaPageFetcher(request.delayMs() != null ? request.delayMs() : DEFAULT_DELAY_MS);

    Map<Long, Optional<LocalDate>> dateCache = new HashMap<>();
    MonthIdRangeFinder finder =
        new MonthIdRangeFinder(
            id ->
                dateCache.computeIfAbsent(
                    id,
                    key ->
                        fetcher
                            .fetch(key)
                            .flatMap(jsonLdParser::parse)
                            .map(JobKoreaJsonLd::datePosted)),
            PROBE_LIMIT);
    BackfillSampler sampler = new BackfillSampler(new Pages(fetcher), new Random());

    Map<String, Object> summary = new LinkedHashMap<>();
    long monthStartId = finder.findFirstIdOnOrAfter(from.atDay(1), minId, maxIdExclusive);
    for (YearMonth month = from; !month.isAfter(to); month = month.plusMonths(1)) {
      long nextStartId =
          finder.findFirstIdOnOrAfter(month.plusMonths(1).atDay(1), monthStartId, maxIdExclusive);
      BackfillSampler.Result result =
          sampler.sample(month, monthStartId, nextStartId, target, maxRequests);
      log.info("[백필] {} ID [{}, {}) → {}", month.format(YM), monthStartId, nextStartId, result);
      summary.put(month.format(YM), result);
      monthStartId = nextStartId;
    }
    return summary;
  }

  /**
   * 기존 잡코리아 공고 전부를 상세페이지를 다시 열어 백필과 같은 규칙으로 직무군·스킬을 다시 매긴다. 원문을 보관하지 않아 다시 받는 수밖에 없다. 열리지 않는 공고는
   * 그대로 둔다.
   */
  public Map<String, Object> reclassifyExisting(Long delayMs) {
    JobKoreaPageFetcher fetcher =
        new JobKoreaPageFetcher(delayMs != null ? delayMs : DEFAULT_DELAY_MS);
    List<String> externalIds =
        jobPostingRepository.findBySource(SOURCE).stream().map(JobPosting::getExternalId).toList();
    int changed = 0;
    int refreshed = 0;
    int unreachable = 0;
    for (String externalId : externalIds) {
      Optional<String> html = fetcher.fetch(Long.parseLong(externalId));
      if (html.isEmpty()) {
        unreachable++;
        continue;
      }
      if (jobPostingIngestService.refreshJobKoreaFromDetail(externalId, html.get())) {
        changed++;
      }
      refreshed++;
    }
    log.info(
        "[재분류] 대상 {} / 스킬 재추출 {} / 직무군 변경 {} / 열리지 않음 {}",
        externalIds.size(),
        refreshed,
        changed,
        unreachable);
    return Map.of(
        "total",
        externalIds.size(),
        "skillsRefreshed",
        refreshed,
        "changed",
        changed,
        "unreachable",
        unreachable);
  }

  private static int orDefault(Integer value, int fallback) {
    return value != null ? value : fallback;
  }

  private final class Pages implements BackfillSampler.Pages {
    private final JobKoreaPageFetcher fetcher;

    private Pages(JobKoreaPageFetcher fetcher) {
      this.fetcher = fetcher;
    }

    @Override
    public boolean alreadySampled(long id) {
      return jobPostingRepository
          .findBySourceAndExternalId(SOURCE, String.valueOf(id))
          .map(posting -> posting.getSampledMonth() != null)
          .orElse(false);
    }

    @Override
    public Optional<BackfillSampler.Candidate> fetch(long id) {
      return fetcher.fetch(id).flatMap(html -> toCandidate(id, html));
    }

    @Override
    public void save(BackfillSampler.Candidate candidate, YearMonth month) {
      jobPostingIngestService.ingestBackfilledJobKorea(
          candidate.page(), month.format(YM), Instant.now());
    }
  }

  private Optional<BackfillSampler.Candidate> toCandidate(long id, String html) {
    return jsonLdParser
        .parse(html)
        .map(
            ld -> {
              String family = familyResolver.resolve(html, ld.title());
              LocalDate posted = ld.datePosted();
              JobKoreaPage page = new JobKoreaPage(String.valueOf(id), html, ld, family);
              return new BackfillSampler.Candidate(
                  id, posted, TARGET_FAMILIES.contains(family), page);
            });
  }
}
