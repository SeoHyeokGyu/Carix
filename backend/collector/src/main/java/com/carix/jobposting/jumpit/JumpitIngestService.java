package com.carix.jobposting.jumpit;

import com.carix.jobposting.CollectedVia;
import com.carix.jobposting.JobPosting;
import com.carix.jobposting.JobPostingRepository;
import com.carix.jobposting.RawIngest;
import com.carix.jobposting.RawIngestIds;
import com.carix.jobposting.RawIngestRepository;
import com.carix.jobposting.Skill;
import com.carix.jobposting.crawler.SaraminDetailFetcher;
import com.carix.jobposting.normalize.CompanySizeClassifier;
import com.carix.jobposting.normalize.EmploymentTypeClassifier;
import com.carix.jobposting.normalize.PostingSalary;
import com.carix.jobposting.normalize.PostingSalaryClassifier;
import com.carix.jobposting.normalize.SkillTaxonomy;
import com.carix.jobposting.service.JobPostingIngestService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionOperations;

/**
 * 점핏 목록을 끝까지 돌며 사람인 공고를 보강하거나 새로 만든다(설계 §5.2). 점핏 공고 번호는 사람인 공고 번호와 같아 공고 행은 {@code SARAMIN} 이고 수집
 * 이력만 {@code JUMPIT} 이다.
 *
 * <p>공고 하나의 조회·보강·저장은 한 트랜잭션이다(스킬 연결이 지연 로딩이고 호출 스레드에는 트랜잭션이 없다). HTTP 호출은 트랜잭션 밖에서 끝낸다.
 */
@Slf4j
public class JumpitIngestService {

  static final String RAW_SOURCE = "JUMPIT";
  private static final String POSTING_SOURCE = "SARAMIN";
  private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");

  /** 목록 끝을 못 만나도 멈추는 안전 상한. 활성 공고 약 770건·페이지당 16건이라 50 안팎에서 끝난다. */
  private static final int MAX_PAGES = 200;

  private enum Outcome {
    CREATED,
    ENRICHED
  }

  private final JumpitClient client;
  private final JumpitParser parser;
  private final JumpitFamilyResolver familyResolver;
  private final JobPostingRepository postings;
  private final RawIngestRepository rawIngests;
  private final JobPostingIngestService skills;
  private final SkillTaxonomy taxonomy;
  private final SaraminDetailFetcher saraminDetail;
  private final TransactionOperations tx;
  private final PostingSalaryClassifier salaryClassifier = new PostingSalaryClassifier();

  public JumpitIngestService(
      JumpitClient client,
      JumpitParser parser,
      JumpitFamilyResolver familyResolver,
      JobPostingRepository postings,
      RawIngestRepository rawIngests,
      JobPostingIngestService skills,
      SkillTaxonomy taxonomy,
      SaraminDetailFetcher saraminDetail,
      TransactionOperations tx) {
    this.client = client;
    this.parser = parser;
    this.familyResolver = familyResolver;
    this.postings = postings;
    this.rawIngests = rawIngests;
    this.skills = skills;
    this.taxonomy = taxonomy;
    this.saraminDetail = saraminDetail;
    this.tx = tx;
  }

  public JumpitIngestSummary ingestAll() {
    String month = YearMonth.now().format(YM);
    Set<String> seen = new HashSet<>();
    Map<String, Integer> dropped = new TreeMap<>();
    int pages = 0;
    int created = 0;
    int enriched = 0;
    int skipped = 0;
    int errors = 0;

    for (int page = 1; page <= MAX_PAGES; page++) {
      Optional<String> body = client.fetchListPage(page);
      if (body.isEmpty()) {
        log.warn("[점핏] 목록 {}페이지를 받지 못해 순회를 멈춥니다", page);
        break;
      }
      JumpitParser.Page parsed = parser.parsePage(body.get());
      if (parsed.empty()) {
        break;
      }
      pages++;
      Instant now = Instant.now();
      recordRawIngest(RawIngestIds.datedListpage("listpage-" + page, now), now, month);
      skipped += parsed.skipped();

      for (JumpitPosition position : parsed.positions()) {
        if (!seen.add(position.id())) {
          continue;
        }
        try {
          if (process(position, month, dropped) == Outcome.CREATED) {
            created++;
          } else {
            enriched++;
          }
        } catch (RuntimeException e) {
          errors++;
          if (errors == 1) {
            log.warn("[점핏] 공고 {} 처리 실패", position.id(), e);
          } else {
            log.warn("[점핏] 공고 {} 처리 실패: {}", position.id(), e.getMessage());
          }
        }
      }
    }
    log.info(
        "[점핏] 수집 완료: 페이지 {} · 공고 {} · 신규 {} · 보강 {} · 건너뜀 {} · 실패 {}",
        pages,
        seen.size(),
        created,
        enriched,
        skipped,
        errors);
    return new JumpitIngestSummary(pages, seen.size(), created, enriched, skipped, errors, dropped);
  }

  private Outcome process(JumpitPosition position, String month, Map<String, Integer> dropped) {
    // 있는 공고는 HTTP 가 필요 없다. 없을 때만 게시일·급여 근거를 트랜잭션 밖에서 미리 받는다.
    boolean alreadyKnown =
        postings.findBySourceAndExternalId(POSTING_SOURCE, position.id()).isPresent();
    LocalDate publishedAt = null;
    PostingSalary fetchedSalary = null;
    if (!alreadyKnown) {
      publishedAt = publishedAt(position.id());
      fetchedSalary =
          salaryClassifier.classifySaramin(saraminDetail.fetch(position.id()).orElse(null));
    }
    LocalDate published = publishedAt;
    PostingSalary salary = fetchedSalary;
    // 실패한 공고의 미등록 스택은 총계에 넣지 않으므로 공고별로 모았다가 성공 뒤에 합친다.
    Map<String, Integer> positionDropped = new TreeMap<>();
    Outcome outcome =
        tx.execute(
            status -> {
              positionDropped.clear();
              Optional<JobPosting> existing =
                  postings.findBySourceAndExternalId(POSTING_SOURCE, position.id());
              List<Skill> known = knownSkills(position.techStacks(), positionDropped);
              if (existing.isPresent()) {
                enrich(existing.get(), position, known, month);
                return Outcome.ENRICHED;
              }
              // 사전 확인 뒤 행이 사라지면 급여를 받지 않았으므로 미상으로 둔다.
              PostingSalary postingSalary =
                  salary != null ? salary : salaryClassifier.classifySaramin(null);
              postings.save(create(position, known, published, postingSalary, month));
              return Outcome.CREATED;
            });
    positionDropped.forEach((name, count) -> dropped.merge(name, count, Integer::sum));
    return outcome;
  }

  /** 상세 응답을 못 읽으면 게시일만 비우고 계속한다. */
  private LocalDate publishedAt(String id) {
    try {
      return client.fetchDetail(id).flatMap(parser::parsePublishedAt).orElse(null);
    } catch (IllegalStateException e) {
      log.warn("[점핏] 공고 {} 상세를 읽지 못해 게시일 없이 저장합니다: {}", id, e.getMessage());
      return null;
    }
  }

  private void enrich(
      JobPosting posting, JumpitPosition position, List<Skill> known, String month) {
    posting.updateLastSeenMonth(month);
    posting.addSkillsIfAbsent(known);
    posting.fillJobFamilyIfEmpty(
        familyResolver.resolve(position.jobCategories(), position.title()));
    posting.fillExperienceIfEmpty(experienceMin(position), position.maxCareer());
    recordSourceJobTags(posting, position);
    posting.addCollectedVia(CollectedVia.JUMPIT);
    postings.save(posting);
  }

  private JobPosting create(
      JumpitPosition position,
      List<Skill> known,
      LocalDate published,
      PostingSalary salary,
      String month) {
    JobPosting posting =
        new JobPosting(
            POSTING_SOURCE,
            position.id(),
            position.title(),
            position.companyName(),
            null,
            null,
            position.location(),
            experienceMin(position),
            position.maxCareer(),
            EmploymentTypeClassifier.UNKNOWN,
            CompanySizeClassifier.UNKNOWN,
            null,
            null,
            familyResolver.resolve(position.jobCategories(), position.title()),
            null,
            null,
            month,
            month,
            Instant.now());
    posting.applyPostedDates(published, position.closedAt());
    posting.applySalary(salary);
    recordSourceJobTags(posting, position);
    posting.addCollectedVia(CollectedVia.JUMPIT);
    posting.addSkillsIfAbsent(known);
    return posting;
  }

  /** 카테고리가 없는 포지션은 다른 원천이 남긴 출처 태그를 지우지 않는다. */
  private static void recordSourceJobTags(JobPosting posting, JumpitPosition position) {
    if (!position.jobCategories().isEmpty()) {
      posting.recordSourceJobTags(String.join(",", position.jobCategories()));
    }
  }

  /** 신입 공고는 경력 하한 0. 점핏은 신입에 minCareer 0 을 주지만 비어 오는 경우를 막는다. */
  private static Integer experienceMin(JumpitPosition position) {
    return position.newcomer() && position.minCareer() == null
        ? Integer.valueOf(0)
        : position.minCareer();
  }

  private List<Skill> knownSkills(List<String> techStacks, Map<String, Integer> dropped) {
    List<Skill> out = new ArrayList<>();
    for (String raw : techStacks) {
      List<String> names = taxonomy.matchKnownSkills(raw);
      if (names.isEmpty()) {
        dropped.merge(raw, 1, Integer::sum);
        continue;
      }
      names.forEach(n -> out.add(skills.upsertKnownSkill(n)));
    }
    return out;
  }

  private void recordRawIngest(String itemId, Instant now, String month) {
    if (rawIngests.findBySourceAndSourceItemId(RAW_SOURCE, itemId).isEmpty()) {
      rawIngests.save(new RawIngest(RAW_SOURCE, itemId, month, month, now));
    }
  }
}
