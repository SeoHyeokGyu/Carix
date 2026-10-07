package com.carix.jobposting.wanted;

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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionOperations;

/**
 * 원티드 개발(518) 목록을 끝까지 돌며 새 공고를 만든다. 이미 있는 공고는 HTTP 없이 관측월·확인 경로만 갱신한다.
 *
 * <p>공고 하나의 저장은 한 트랜잭션이다(스킬 연결이 지연 로딩이고 호출 스레드에는 트랜잭션이 없다). HTTP 호출은 트랜잭션 밖에서 끝낸다.
 */
@Slf4j
public class WantedIngestService {

  static final String RAW_SOURCE = "WANTED";

  /** 목록 API 는 개발 태그로 거르지만 정본은 공고 페이지의 상위 태그다. */
  static final int DEVELOPMENT_PARENT_TAG = 518;

  private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");

  /** 목록 끝을 못 만나도 멈추는 안전 상한. */
  private static final int MAX_PAGES = 100;

  private enum Outcome {
    CREATED,
    SEEN,
    SKIPPED
  }

  private final WantedClient client;
  private final WantedParser parser;
  private final WantedPostingWriter writer;
  private final RawIngestRepository rawIngests;
  private final TransactionOperations tx;

  public WantedIngestService(
      WantedClient client,
      WantedParser parser,
      JobPostingRepository postings,
      RawIngestRepository rawIngests,
      PostingSkillService skills,
      SkillTaxonomy taxonomy,
      TransactionOperations tx) {
    this.client = client;
    this.parser = parser;
    this.writer = new WantedPostingWriter(postings, skills, taxonomy);
    this.rawIngests = rawIngests;
    this.tx = tx;
  }

  public WantedCrawlSummary ingestAll() {
    String month = YearMonth.now().format(YM);
    Set<String> seenIds = new HashSet<>();
    Map<String, Integer> dropped = new TreeMap<>();
    int pages = 0;
    int created = 0;
    int seen = 0;
    int skipped = 0;
    int errors = 0;
    boolean listFailed = false;

    for (int page = 0; page < MAX_PAGES; page++) {
      Optional<String> body = client.fetchList(page * WantedClient.LIST_LIMIT);
      if (body.isEmpty()) {
        log.warn("[원티드] 목록 {}페이지를 받지 못해 순회를 멈춥니다", page);
        listFailed = true;
        break;
      }
      WantedParser.ListPage parsed = parser.parseList(body.get());
      if (parsed.items().isEmpty()) {
        break;
      }
      pages++;
      Instant now = Instant.now();
      recordRawIngest(RawIngestIds.datedListpage("listpage-" + pages, now), now, month);

      for (WantedListItem item : parsed.items()) {
        if (!seenIds.add(item.id())) {
          continue;
        }
        try {
          switch (process(item, month, dropped)) {
            case CREATED -> created++;
            case SEEN -> seen++;
            case SKIPPED -> skipped++;
          }
        } catch (RuntimeException e) {
          errors++;
          if (errors == 1) {
            log.warn("[원티드] 공고 {} 처리 실패", item.id(), e);
          } else {
            log.warn("[원티드] 공고 {} 처리 실패: {}", item.id(), e.getMessage());
          }
        }
      }
      if (parsed.last()) {
        break;
      }
    }
    log.info(
        "[원티드] 수집 완료: 페이지 {} · 공고 {} · 신규 {} · 기존 {} · 건너뜀 {} · 실패 {}",
        pages,
        seenIds.size(),
        created,
        seen,
        skipped,
        errors);
    return new WantedCrawlSummary(
        pages, seenIds.size(), created, seen, skipped, errors, dropped, listFailed);
  }

  private Outcome process(WantedListItem item, String month, Map<String, Integer> dropped) {
    if (Boolean.TRUE.equals(tx.execute(s -> writer.touchListed(item.id(), month)))) {
      return Outcome.SEEN;
    }
    long id = Long.parseLong(item.id());
    Optional<WantedPosting> page = client.fetchPage(id).flatMap(parser::parsePage);
    if (page.isEmpty() || page.get().parentTagId() != DEVELOPMENT_PARENT_TAG) {
      return Outcome.SKIPPED;
    }
    if (!page.get().id().equals(item.id())) {
      log.warn("[원티드] 목록 공고 {} 의 페이지 번호가 {} 라 건너뜁니다", item.id(), page.get().id());
      return Outcome.SKIPPED;
    }
    List<String> skillTags =
        client.fetchDetailApi(id).map(parser::parseSkillTags).orElse(List.of());
    // 실패한 공고의 미등록 스택은 총계에 넣지 않으므로 공고별로 모았다가 성공 뒤에 합친다.
    Map<String, Integer> postingDropped = new TreeMap<>();
    boolean isCreated =
        Boolean.TRUE.equals(
            tx.execute(
                s -> {
                  postingDropped.clear();
                  return writer.upsertListed(page.get(), skillTags, month, postingDropped);
                }));
    postingDropped.forEach((name, count) -> dropped.merge(name, count, Integer::sum));
    return isCreated ? Outcome.CREATED : Outcome.SEEN;
  }

  private void recordRawIngest(String itemId, Instant now, String month) {
    if (rawIngests.findBySourceAndSourceItemId(RAW_SOURCE, itemId).isEmpty()) {
      rawIngests.save(new RawIngest(RAW_SOURCE, itemId, month, month, now));
    }
  }
}
