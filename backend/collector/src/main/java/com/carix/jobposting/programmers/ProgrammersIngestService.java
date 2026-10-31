package com.carix.jobposting.programmers;

import com.carix.jobposting.JobPostingRepository;
import com.carix.jobposting.RawIngest;
import com.carix.jobposting.RawIngestIds;
import com.carix.jobposting.RawIngestRepository;
import com.carix.jobposting.normalize.SkillTaxonomy;
import com.carix.jobposting.service.PostingSkillService;
import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionOperations;

/** 프로그래머스 채용 목록을 순회하며 공고를 수집/적재한다. 이미 저장된 공고는 관측월과 수집 경로만 갱신한다. */
@Slf4j
public class ProgrammersIngestService {

  static final String RAW_SOURCE = "PROGRAMMERS";
  private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");
  private static final int DEFAULT_MAX_PAGES = 100;

  private final ProgrammersClient client;
  private final ProgrammersParser parser;
  private final ProgrammersPostingWriter writer;
  private final RawIngestRepository rawIngests;
  private final TransactionOperations tx;

  public ProgrammersIngestService(
      ProgrammersClient client,
      ProgrammersParser parser,
      JobPostingRepository postings,
      RawIngestRepository rawIngests,
      PostingSkillService skills,
      SkillTaxonomy taxonomy,
      TransactionOperations tx) {
    this.client = client;
    this.parser = parser;
    this.writer = new ProgrammersPostingWriter(postings, skills, taxonomy);
    this.rawIngests = rawIngests;
    this.tx = tx;
  }

  public ProgrammersCrawlSummary ingestAll() {
    return ingest(DEFAULT_MAX_PAGES);
  }

  public ProgrammersCrawlSummary ingest(Integer maxPagesOverride) {
    int maxPages =
        (maxPagesOverride != null && maxPagesOverride > 0) ? maxPagesOverride : DEFAULT_MAX_PAGES;
    String month = YearMonth.now().format(YM);
    Set<String> seenIds = new HashSet<>();
    Map<String, Integer> dropped = new TreeMap<>();

    int pages = 0;
    int fetched = 0;
    int created = 0;
    int seen = 0;
    int skipped = 0;
    int errors = 0;
    boolean listFailed = false;

    for (int page = 1; page <= maxPages; page++) {
      Optional<String> body = client.fetchListPage(page);
      if (body.isEmpty()) {
        log.warn("[프로그래머스] 목록 {}페이지 응답을 받지 못해 순회를 중단합니다", page);
        listFailed = true;
        break;
      }

      ProgrammersParser.Page parsed;
      try {
        parsed = parser.parsePage(body.get());
      } catch (Exception e) {
        log.warn("[프로그래머스] {}페이지 파싱 실패: {}", page, e.getMessage());
        listFailed = true;
        break;
      }

      if (parsed.empty() || parsed.positions().isEmpty()) {
        break;
      }

      pages++;
      fetched += parsed.positions().size();
      skipped += parsed.skipped();

      Instant now = Instant.now();
      recordRawIngest(RawIngestIds.datedListpage("programmers-listpage-" + page, now), now, month);

      for (ProgrammersPosition pos : parsed.positions()) {
        if (!seenIds.add(pos.id())) {
          continue;
        }

        try {
          Boolean wasCreated = tx.execute(status -> writer.upsertListed(pos, month, dropped));
          if (Boolean.TRUE.equals(wasCreated)) {
            created++;
          } else {
            seen++;
          }
        } catch (Exception e) {
          errors++;
          log.warn("[프로그래머스] 공고 id={} 적재 실패: {}", pos.id(), e.getMessage());
        }
      }

      if (parsed.totalPages() > 0 && page >= parsed.totalPages()) {
        break;
      }
    }

    return new ProgrammersCrawlSummary(
        pages, fetched, created, seen, skipped, errors, dropped, listFailed);
  }

  private void recordRawIngest(String itemId, Instant now, String month) {
    try {
      tx.executeWithoutResult(
          status -> rawIngests.save(new RawIngest(RAW_SOURCE, itemId, month, month, now)));
    } catch (Exception e) {
      log.warn("[프로그래머스] RawIngest 기록 실패 (진행은 계속): {}", e.getMessage());
    }
  }
}
