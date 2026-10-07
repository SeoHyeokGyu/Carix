package com.carix.jobposting.service;

import com.carix.jobposting.CollectedVia;
import com.carix.jobposting.JobPosting;
import com.carix.jobposting.JobPostingRepository;
import com.carix.jobposting.JobPostingSkillRepository;
import com.carix.jobposting.RawIngest;
import com.carix.jobposting.RawIngestIds;
import com.carix.jobposting.RawIngestRepository;
import com.carix.jobposting.Skill;
import com.carix.jobposting.SkillAliasRepository;
import com.carix.jobposting.SkillRepository;
import com.carix.jobposting.UserSkillRepository;
import com.carix.jobposting.backfill.JobKoreaJsonLd;
import com.carix.jobposting.backfill.JobKoreaOwnPostingParser;
import com.carix.jobposting.backfill.JobKoreaPage;
import com.carix.jobposting.crawler.JobKoreaDetailParser;
import com.carix.jobposting.crawler.SaraminDetailFetcher;
import com.carix.jobposting.normalize.CompanySizeClassifier;
import com.carix.jobposting.normalize.EmploymentTypeClassifier;
import com.carix.jobposting.normalize.ExperienceRange;
import com.carix.jobposting.normalize.JobFamilyClassifier;
import com.carix.jobposting.normalize.JobKoreaFamilyResolver;
import com.carix.jobposting.normalize.PostingSalary;
import com.carix.jobposting.normalize.PostingSalaryClassifier;
import com.carix.jobposting.normalize.SalaryDisclosure;
import com.carix.jobposting.normalize.SkillTaxonomy;
import com.carix.jobposting.seed.JobKoreaListingEnricher;
import com.carix.jobposting.seed.JobKoreaListingFields;
import com.carix.jobposting.seed.JobPostingDateParser;
import com.carix.jobposting.seed.SaraminListingEnricher;
import com.carix.jobposting.seed.SaraminListingFields;
import com.carix.pipeline.dto.CrawlRequest;
import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
public class JobPostingIngestService {

  private static final DateTimeFormatter YM_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");

  private static final String JOBKOREA_LIST_ENDPOINT =
      "https://www.jobkorea.co.kr/Recruit/Home/_GI_List/";
  private static final long SARAMIN_DETAIL_DELAY_MS = 1_000L;
  private static final String DUTY_CATEGORY_IT = "10031";
  private static final String JOBKOREA_LIST_REFERER =
      "https://www.jobkorea.co.kr/recruit/joblist?menucode=duty&dutyCtgr=" + DUTY_CATEGORY_IT;

  private final JobPostingRepository jobPostingRepository;
  private final SkillRepository skillRepository;
  private final RawIngestRepository rawIngestRepository;
  private final JobPostingSkillRepository jobPostingSkillRepository;
  private final SkillAliasRepository skillAliasRepository;
  private final UserSkillRepository userSkillRepository;
  private final SkillTaxonomy skillTaxonomy;

  private final JobKoreaListingEnricher jobKoreaListingEnricher = new JobKoreaListingEnricher();
  private final SaraminListingEnricher saraminListingEnricher = new SaraminListingEnricher();
  private final JobKoreaDetailParser jobKoreaDetailParser = new JobKoreaDetailParser();
  private final JobKoreaOwnPostingParser jobKoreaOwnPostingParser = new JobKoreaOwnPostingParser();
  private final TransactionOperations tx;
  private final PostingSalaryClassifier salaryClassifier = new PostingSalaryClassifier();
  private final SaraminDetailFetcher saraminDetailFetcher =
      new SaraminDetailFetcher(SARAMIN_DETAIL_DELAY_MS);
  private final JobFamilyClassifier jobFamilyClassifier = new JobFamilyClassifier();
  private final JobKoreaFamilyResolver jobKoreaFamilyResolver = JobKoreaFamilyResolver.bundled();
  private final CompanySizeClassifier companySizeClassifier = new CompanySizeClassifier();
  private final EmploymentTypeClassifier employmentTypeClassifier = new EmploymentTypeClassifier();

  @Autowired
  public JobPostingIngestService(
      JobPostingRepository jobPostingRepository,
      SkillRepository skillRepository,
      RawIngestRepository rawIngestRepository,
      @Autowired(required = false) JobPostingSkillRepository jobPostingSkillRepository,
      @Autowired(required = false) SkillAliasRepository skillAliasRepository,
      @Autowired(required = false) UserSkillRepository userSkillRepository,
      @Autowired(required = false) SkillTaxonomy skillTaxonomy,
      @Autowired(required = false) PlatformTransactionManager transactionManager) {
    this.tx =
        transactionManager != null
            ? new TransactionTemplate(transactionManager)
            : TransactionOperations.withoutTransaction();
    this.jobPostingRepository = jobPostingRepository;
    this.skillRepository = skillRepository;
    this.rawIngestRepository = rawIngestRepository;
    this.jobPostingSkillRepository = jobPostingSkillRepository;
    this.skillAliasRepository = skillAliasRepository;
    this.userSkillRepository = userSkillRepository;
    this.skillTaxonomy =
        skillTaxonomy != null
            ? skillTaxonomy
            : new SkillTaxonomy(skillRepository, skillAliasRepository);
  }

  public JobPostingIngestService(
      JobPostingRepository jobPostingRepository,
      SkillRepository skillRepository,
      RawIngestRepository rawIngestRepository,
      JobPostingSkillRepository jobPostingSkillRepository,
      SkillAliasRepository skillAliasRepository,
      UserSkillRepository userSkillRepository,
      SkillTaxonomy skillTaxonomy) {
    this(
        jobPostingRepository,
        skillRepository,
        rawIngestRepository,
        jobPostingSkillRepository,
        skillAliasRepository,
        userSkillRepository,
        skillTaxonomy,
        null);
  }

  public JobPostingIngestService(
      JobPostingRepository jobPostingRepository,
      SkillRepository skillRepository,
      RawIngestRepository rawIngestRepository) {
    this(
        jobPostingRepository,
        skillRepository,
        rawIngestRepository,
        null,
        null,
        null,
        new SkillTaxonomy(skillRepository, null),
        null);
  }

  /** 크롤링 요청 DTO 기반 전체 크롤링 & DB 실시간 적재 */
  public int crawlAndIngest(CrawlRequest request) {
    String portal =
        request != null && request.portal() != null ? request.portal().toUpperCase() : "ALL";
    int maxPages = request != null && request.maxPages() != null ? request.maxPages() : 5;
    int count = 0;

    if ("JOBKOREA".equals(portal) || "ALL".equals(portal)) {
      count += crawlAndIngestJobKorea("IT개발", maxPages);
    }
    if ("SARAMIN".equals(portal) || "ALL".equals(portal)) {
      count += crawlAndIngestSaramin(maxPages);
    }
    return count;
  }

  /** 잡코리아 크롤링 & DB 실시간 직접 적재 */
  @Transactional
  public int crawlAndIngestJobKorea(String keyword, int pageLimit) {
    String currentMonth = YearMonth.now().format(YM_FORMATTER);
    int ingestedCount = 0;
    Set<String> collectedIds = new HashSet<>();

    log.info("[JobKorea] 크롤링 & DB 적재 시작 (최대 {} 페이지)", pageLimit);

    for (int page = 1; page <= pageLimit; page++) {
      try {
        Document listDoc =
            Jsoup.connect(JOBKOREA_LIST_ENDPOINT)
                .userAgent(
                    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
                .header("X-Requested-With", "XMLHttpRequest")
                .referrer(JOBKOREA_LIST_REFERER)
                .data("page", String.valueOf(page))
                .data("condition[dutyCtgr]", DUTY_CATEGORY_IT)
                .data("condition[menucode]", "")
                .data("direct", "0")
                .data("order", "20")
                .data("pagesize", "40")
                .data("tabindex", "0")
                .data("onePick", "0")
                .data("confirm", "0")
                .data("profile", "0")
                .timeout(10000)
                .post();

        Instant now = Instant.now();
        persistRawIngestIfAbsent(
            "JOBKOREA", RawIngestIds.datedListpage("listpage-" + page, now), now, currentMonth);

        Elements rows = listDoc.select("tr.devloopArea");
        if (rows.isEmpty()) {
          log.info("[JobKorea] 페이지 {}에 공고 목록이 없어 크롤링을 종료합니다.", page);
          break;
        }

        for (Element row : rows) {
          String externalId = row.attr("data-gno").strip();
          if (externalId.isEmpty() || !collectedIds.add(externalId)) {
            continue;
          }

          String companyName = row.select(".tplCo a.link").text().strip();
          Element titleLink = row.selectFirst(".tplTit strong a.link");
          if (titleLink == null) {
            continue;
          }
          String title = titleLink.text().strip();
          String link = titleLink.attr("href").strip();
          if (link.startsWith("/")) {
            link = "https://www.jobkorea.co.kr" + link;
          }

          Elements cellSpans = row.select(".tplTit p.etc span.cell");
          String careerType = cellSpans.size() > 0 ? cellSpans.get(0).text().strip() : "무관";
          String location = cellSpans.size() > 2 ? cellSpans.get(2).text().strip() : "전국";
          ExperienceRange experience = ExperienceRange.parse(careerType);

          String createdAtRaw = "";
          Element timeSpan = row.selectFirst("td.odd span.time");
          if (timeSpan != null) {
            createdAtRaw = timeSpan.text().strip();
          }

          // 상세 페이지 수집 및 DB 저장
          String detailHtml = fetchDetailHtml(link);
          if (detailHtml != null) {
            persistRawIngestIfAbsent("JOBKOREA", "detail-" + externalId, now, currentMonth);
          }

          JobKoreaListingFields fields =
              jobKoreaListingEnricher.extract(listDoc.outerHtml(), externalId);

          ingestJobKoreaPosting(
              externalId,
              title,
              companyName,
              location,
              experience,
              createdAtRaw,
              fields,
              detailHtml,
              now,
              currentMonth);

          ingestedCount++;
          sleep(500);
        }

        sleep(1000);
      } catch (Exception e) {
        log.error("[JobKorea] 페이지 {} 처리 중 오류 발생: {}", page, e.getMessage(), e);
      }
    }

    log.info("[JobKorea] 크롤링 & DB 적재 완료: 총 {}건", ingestedCount);
    return ingestedCount;
  }

  /** 사람인 크롤링 & DB 실시간 직접 적재 */
  @Transactional
  public int crawlAndIngestSaramin(int pageLimit) {
    String currentMonth = YearMonth.now().format(YM_FORMATTER);
    int ingestedCount = 0;
    Set<String> collectedIds = new HashSet<>();
    Map<String, String> sessionCookies = initSaraminSessionCookies();

    log.info("[Saramin] 크롤링 & DB 적재 시작 (최대 {} 페이지)", pageLimit);

    for (int page = 1; page <= pageLimit; page++) {
      try {
        String url =
            String.format(
                "https://www.saramin.co.kr/zf_user/search/recruit?searchword=%%EA%%B0%%9C%%EB%%B0%%9C%%EC%%9E%%90&job_cd=84&job_cd=86&job_cd=87&job_cd=92&recruitPage=%d&recruitSort=relation&recruitPageCount=40",
                page);

        Document doc =
            Jsoup.connect(url)
                .userAgent(
                    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "ko-KR,ko;q=0.8,en-US;q=0.5,en;q=0.3")
                .cookies(sessionCookies)
                .timeout(10000)
                .get();

        Instant now = Instant.now();
        persistRawIngestIfAbsent(
            "SARAMIN", RawIngestIds.datedListpage("listpage-" + page, now), now, currentMonth);

        Elements items = doc.select("div.item_recruit");
        if (items.isEmpty()) {
          log.info("[Saramin] 페이지 {}에 공고 목록이 없어 크롤링을 종료합니다.", page);
          break;
        }

        for (Element item : items) {
          String externalId = item.attr("value");
          if (externalId == null || externalId.trim().isEmpty()) {
            externalId = item.attr("id");
          }
          if (externalId == null || externalId.trim().isEmpty() || !collectedIds.add(externalId)) {
            continue;
          }

          Element corpLink = item.selectFirst("div.area_corp strong.corp_name a");
          String companyName = corpLink != null ? corpLink.text().strip() : "";

          Element titleLink = item.selectFirst("div.area_job h2.job_tit a");
          if (titleLink == null) {
            continue;
          }
          String title = titleLink.text().strip();

          Elements jobConditions = item.select("div.area_job div.job_condition span");
          String location = jobConditions.size() > 0 ? jobConditions.get(0).text().strip() : "전국";
          String career = jobConditions.size() > 1 ? jobConditions.get(1).text().strip() : "무관";
          ExperienceRange experience = ExperienceRange.parse(career);

          String createdAtRaw = saraminCreatedAtRaw(item);

          SaraminListingFields fields = saraminListingEnricher.extract(doc.outerHtml(), externalId);

          // 상세페이지는 아직 DB 에 없는 공고만 연다 — 기존 공고는 급여를 다시 판정하지 않는다.
          PostingSalary salaryForNew =
              jobPostingRepository.findBySourceAndExternalId("SARAMIN", externalId).isPresent()
                  ? null
                  : salaryClassifier.classifySaramin(
                      saraminDetailFetcher.fetch(externalId).orElse(null));

          ingestSaraminPosting(
              externalId,
              title,
              companyName,
              location,
              experience,
              createdAtRaw,
              fields,
              now,
              currentMonth,
              salaryForNew);

          ingestedCount++;
        }

        sleep(1000);
      } catch (Exception e) {
        log.error("[Saramin] 페이지 {} 처리 중 오류 발생: {}", page, e.getMessage(), e);
      }
    }

    log.info("[Saramin] 크롤링 & DB 적재 완료: 총 {}건", ingestedCount);
    return ingestedCount;
  }

  /** 공고 하나의 조회·갱신·저장을 한 트랜잭션으로 묶는다. 목록 크롤링은 this 호출이라 호출 스레드에 트랜잭션이 없다. */
  void ingestJobKoreaPosting(
      String externalId,
      String title,
      String companyName,
      String location,
      ExperienceRange experience,
      String createdAtRaw,
      JobKoreaListingFields fields,
      String detailHtml,
      Instant now,
      String currentMonth) {
    tx.executeWithoutResult(
        status ->
            upsertJobKoreaPosting(
                externalId,
                title,
                companyName,
                location,
                experience,
                createdAtRaw,
                fields,
                detailHtml,
                now,
                currentMonth));
  }

  private void upsertJobKoreaPosting(
      String externalId,
      String title,
      String companyName,
      String location,
      ExperienceRange experience,
      String createdAtRaw,
      JobKoreaListingFields fields,
      String detailHtml,
      Instant now,
      String currentMonth) {
    PostingSalary salary =
        PostingSalaryClassifier.withListingFallback(
            salaryClassifier.classifyJobKorea(detailHtml), fields.salaryMin(), fields.salaryMax());

    String jobFamily = jobKoreaFamilyResolver.resolve(detailHtml, title);

    String companySize =
        detailHtml != null
            ? companySizeClassifier.classify(
                jobKoreaDetailParser.extractEmployeeCount(detailHtml),
                jobKoreaDetailParser.extractCompanyTypeName(detailHtml))
            : CompanySizeClassifier.UNKNOWN;

    Optional<JobPosting> existingOpt =
        jobPostingRepository.findBySourceAndExternalId("JOBKOREA", externalId);

    JobPosting posting;
    if (existingOpt.isPresent()) {
      posting = existingOpt.get();
      posting.updateLastSeenMonth(currentMonth);
      applySalaryIfKnown(posting, salary);
      posting.classifyAs(jobFamily);
      posting.classifyCompanySizeAs(companySize);
      posting.applyExperienceRange(experience.min(), experience.max());
      posting.classifyEmploymentTypeAs(employmentTypeClassifier.classify(fields.employmentType()));
    } else {
      posting =
          new JobPosting(
              "JOBKOREA",
              externalId,
              title,
              companyName,
              null,
              null,
              location,
              experience.min(),
              experience.max(),
              employmentTypeClassifier.classify(fields.employmentType()),
              companySize,
              fields.companyBadge(),
              fields.positionLevel(),
              jobFamily,
              JobPostingDateParser.parseCreatedAt("JOBKOREA", createdAtRaw, now),
              JobPostingDateParser.parseDeadline("JOBKOREA", fields.postedEndDateRaw(), now),
              currentMonth,
              currentMonth,
              now);
      posting.applySalary(salary);
    }

    List<String> rawSkills =
        detailHtml != null ? jobKoreaDetailParser.extractSkills(detailHtml) : List.of();

    applySkillsToPosting(posting, title, rawSkills);
    posting.addCollectedVia(CollectedVia.JOBKOREA_LIST);
    jobPostingRepository.save(posting);
  }

  /**
   * 백필 표본 한 건을 저장한다. 이미 정기 수집된 공고면 관측 월은 그대로 두고 표본 월만 단다 — 관측 월을 과거로 되돌리지 않으면서 표본에서도 빠지지 않게 한다. 관측
   * 월은 게시일이 속한 달이고 원천 항목 ID 는 {@code backfill-<id>} 로 남겨 정기 수집과 구분한다.
   *
   * @return 새로 저장했으면 true
   */
  @Transactional
  public boolean ingestBackfilledJobKorea(JobKoreaPage page, String postedMonth, Instant now) {
    Optional<JobPosting> existing =
        jobPostingRepository.findBySourceAndExternalId("JOBKOREA", page.externalId());
    if (existing.isPresent()) {
      // 정기 수집으로 이미 들어온 공고. 관측 월은 그대로 두고 표본 월만 단다 — 직무군·스킬은 같은 상세페이지로 맞춘다.
      JobPosting posting = existing.get();
      posting.markSampled(postedMonth);
      applySalaryIfKnown(posting, salaryClassifier.classifyJobKorea(page.html()));
      posting.classifyAs(page.jobFamily());
      applySkillsToPosting(
          posting, posting.getTitle(), jobKoreaDetailParser.extractSkills(page.html()));
      posting.addCollectedVia(CollectedVia.JOBKOREA_BACKFILL);
      jobPostingRepository.save(posting);
      persistRawIngestIfAbsent("JOBKOREA", "backfill-" + page.externalId(), now, postedMonth);
      return true;
    }
    JobKoreaJsonLd ld = page.jsonLd();
    String html = page.html();
    ExperienceRange experience = jobKoreaOwnPostingParser.extractExperience(html);

    JobPosting posting =
        new JobPosting(
            "JOBKOREA",
            page.externalId(),
            ld.title(),
            ld.companyName(),
            null,
            null,
            ld.region(),
            experience.min(),
            experience.max(),
            backfillEmploymentType(ld.employmentType()),
            companySizeClassifier.classify(
                jobKoreaDetailParser.extractEmployeeCount(html),
                jobKoreaDetailParser.extractCompanyTypeName(html)),
            null,
            null,
            page.jobFamily(),
            ld.datePosted(),
            ld.validThrough(),
            postedMonth,
            postedMonth,
            now);

    posting.applySalary(salaryClassifier.classifyJobKorea(html));
    posting.markSampled(postedMonth);
    applySkillsToPosting(posting, ld.title(), jobKoreaDetailParser.extractSkills(html));
    posting.addCollectedVia(CollectedVia.JOBKOREA_BACKFILL);
    jobPostingRepository.save(posting);
    persistRawIngestIfAbsent("JOBKOREA", "backfill-" + page.externalId(), now, postedMonth);
    return true;
  }

  /**
   * 기존 잡코리아 공고를 지금의 상세페이지로 다시 매긴다 — 직무군(자기 공고 직무 코드 + 제목)과 스킬(상세 스킬 + 제목, 현재 사전).
   *
   * <p>정기 수집분은 수집 당시의 파서·사전으로 스킬이 저장돼, 오늘의 추출로 저장되는 백필분과 같은 잣대가 아니다. 두 창의 수요율을 같은 추출로 맞추려고 직무군이
   * 그대로여도 스킬은 늘 다시 뽑는다.
   *
   * @return 직무군이 바뀌었으면 true
   */
  @Transactional
  public boolean refreshJobKoreaFromDetail(String externalId, String detailHtml) {
    Optional<JobPosting> found =
        jobPostingRepository.findBySourceAndExternalId("JOBKOREA", externalId);
    if (found.isEmpty()) {
      return false;
    }
    JobPosting posting = found.get();
    String family = jobKoreaFamilyResolver.resolve(detailHtml, posting.getTitle());
    boolean familyChanged = !family.equals(posting.getJobFamily());
    if (familyChanged) {
      posting.classifyAs(family);
    }
    applySkillsToPosting(
        posting, posting.getTitle(), jobKoreaDetailParser.extractSkills(detailHtml));
    jobPostingRepository.save(posting);
    return familyChanged;
  }

  /** JSON-LD 는 schema.org 열거값(`FULL_TIME`)을 준다. 목록의 한글 표기 분류기는 이를 모르므로 먼저 옮기고, 아니면 한글로 판정한다. */
  static String backfillEmploymentType(String raw) {
    if (raw == null || raw.isBlank()) {
      return EmploymentTypeClassifier.UNKNOWN;
    }
    return switch (raw.strip().toUpperCase()) {
      case "FULL_TIME" -> EmploymentTypeClassifier.FULL_TIME;
      case "CONTRACTOR", "TEMPORARY" -> EmploymentTypeClassifier.CONTRACT;
      case "INTERN" -> EmploymentTypeClassifier.INTERN;
      default -> new EmploymentTypeClassifier().classify(raw);
    };
  }

  /**
   * 사람인 목록의 등록일 원문. {@code .job_date .date} 는 마감일이라 등록일로 쓰면 연도 보정이 등록일을 1년 전, 마감일을 올해로 갈라 365일 창을
   * 만든다. 등록일은 {@code .job_sector .job_day}(「등록일 YY/MM/DD」)에서 읽는다 — {@code SaraminCrawler} 와 같은 규약.
   * 없으면 빈 문자열이고 마감일로 대신하지 않는다.
   */
  static String saraminCreatedAtRaw(Element item) {
    Element daySpan = item.selectFirst(".area_job .job_sector .job_day");
    if (daySpan == null) {
      return "";
    }
    return daySpan.text().replace("등록일", "").strip();
  }

  /** 기존 공고는 판정 불가(UNKNOWN)면 앞서 판정한 급여를 그대로 둔다 — 상세페이지를 못 받은 재수집이 급여를 지우지 않게. */
  private static void applySalaryIfKnown(JobPosting posting, PostingSalary salary) {
    if (salary.disclosure() != SalaryDisclosure.UNKNOWN) {
      posting.applySalary(salary);
    }
  }

  /**
   * 공고 하나의 조회·갱신·저장을 한 트랜잭션으로 묶는다. 호출 스레드에 트랜잭션이 없어도 지연 로딩 스킬 컬렉션을 갱신할 수 있어야 한다(HTTP 상세 조회는 호출 전에
   * 끝낸다).
   */
  void ingestSaraminPosting(
      String externalId,
      String title,
      String companyName,
      String location,
      ExperienceRange experience,
      String createdAtRaw,
      SaraminListingFields fields,
      Instant now,
      String currentMonth,
      PostingSalary salaryForNew) {
    tx.executeWithoutResult(
        status ->
            upsertSaraminPosting(
                externalId,
                title,
                companyName,
                location,
                experience,
                createdAtRaw,
                fields,
                now,
                currentMonth,
                salaryForNew));
  }

  private void upsertSaraminPosting(
      String externalId,
      String title,
      String companyName,
      String location,
      ExperienceRange experience,
      String createdAtRaw,
      SaraminListingFields fields,
      Instant now,
      String currentMonth,
      PostingSalary salaryForNew) {
    String jobFamily = jobFamilyClassifier.classifyTags(fields.skillTags());

    Optional<JobPosting> existingOpt =
        jobPostingRepository.findBySourceAndExternalId("SARAMIN", externalId);

    JobPosting posting;
    if (existingOpt.isPresent()) {
      posting = existingOpt.get();
      posting.updateLastSeenMonth(currentMonth);
      if (posting.hasCollectedVia(CollectedVia.JUMPIT)) {
        // 점핏이 먼저 판정한 직무군·경력은 사람인 목록이 덮어쓰지 않는다.
        posting.fillJobFamilyIfEmpty(jobFamily);
        posting.fillExperienceIfEmpty(experience.min(), experience.max());
      } else {
        posting.classifyAs(jobFamily);
        posting.applyExperienceRange(experience.min(), experience.max());
      }
      posting.classifyCompanySizeAs(CompanySizeClassifier.UNKNOWN);
      posting.classifyEmploymentTypeAs(employmentTypeClassifier.classify(fields.employmentType()));
    } else {
      posting =
          new JobPosting(
              "SARAMIN",
              externalId,
              title,
              companyName,
              null,
              null,
              location,
              experience.min(),
              experience.max(),
              employmentTypeClassifier.classify(fields.employmentType()),
              CompanySizeClassifier.UNKNOWN,
              null,
              null,
              jobFamily,
              JobPostingDateParser.parseCreatedAt("SARAMIN", createdAtRaw, now),
              JobPostingDateParser.parseDeadline("SARAMIN", fields.postedEndDateRaw(), now),
              currentMonth,
              currentMonth,
              now);
      if (salaryForNew != null) {
        posting.applySalary(salaryForNew);
      }
    }

    applySkillsToPosting(posting, title, fields.skillTags());
    for (String industryName : distinct(fields.industryTags())) {
      String canonical = skillTaxonomy.canonicalName(industryName);
      if (canonical != null && !canonical.isBlank()) {
        Skill indSkill = upsertSkill(canonical, "INDUSTRY");
        if (posting.getSkills().stream().noneMatch(s -> s.getId().equals(indSkill.getId()))) {
          posting.addSkill(indSkill);
        }
      }
    }

    posting.addCollectedVia(CollectedVia.SARAMIN_SEARCH);
    jobPostingRepository.save(posting);
  }

  /** 공고 제목과 원천 스킬 태그 목록을 결합하여 표준 스킬을 추출하고 스킬 행을 준비합니다. 불필요한 1글자 잡음이나 노이즈는 제외됩니다. */
  public List<Skill> resolveSkills(String title, List<String> rawSkills) {
    Set<String> combinedCanonicalSkills = new LinkedHashSet<>();

    // 1. 공고 제목(자연어)에서 단어 경계를 준수하여 사전 기반 스킬 추출
    if (title != null && !title.isBlank()) {
      combinedCanonicalSkills.addAll(skillTaxonomy.extractSkillsFromSentence(title));
    }

    // 2. 크롤링된 태그/상세 원천 스킬 목록에서 추출
    if (rawSkills != null) {
      for (String rawName : rawSkills) {
        combinedCanonicalSkills.addAll(skillTaxonomy.extractCanonicalSkills(rawName));
      }
    }

    List<Skill> targetSkills = new ArrayList<>();
    Set<String> appliedKeys = new LinkedHashSet<>();
    for (String canonicalName : combinedCanonicalSkills) {
      if (canonicalName == null || canonicalName.isBlank()) {
        continue;
      }
      String category = skillTaxonomy.category(canonicalName);
      if (appliedKeys.add(canonicalName + " " + category)) {
        targetSkills.add(upsertSkill(canonicalName, category));
      }
    }

    return targetSkills;
  }

  /** 사전에 있는 표준 스킬 이름으로 스킬 행을 찾거나 만든다. */
  public Skill upsertKnownSkill(String canonicalName) {
    return upsertSkill(canonicalName, skillTaxonomy.category(canonicalName));
  }

  /** 공고 스킬을 원천 재파싱 결과에 맞춘다. 점핏으로 보강된 공고는 점핏 기술 스택이 사람인 목록에 없으므로 더하기만 한다 — 바꾸면 점핏 스킬이 지워진다. */
  private void applySkillsToPosting(JobPosting posting, String title, List<String> rawSkills) {
    List<Skill> targetSkills = resolveSkills(title, rawSkills);
    if (posting.hasCollectedVia(CollectedVia.JUMPIT)) {
      posting.addSkillsIfAbsent(targetSkills);
    } else {
      posting.replaceSkills(targetSkills);
    }
  }

  /**
   * DB에 적재된 모든 채용공고의 스킬을 공고 제목과 기존 태그를 바탕으로 일괄 재정제(Refinement)합니다. 크롤링 완료 후 스킬이 누락되었거나 노이즈가 섞인 공고들을
   * 깨끗하게 복구합니다.
   */
  @Transactional
  public Map<String, Object> refineAllPostingSkills() {
    // 0. 기존 DB에 누적된 중복 standard_name 스킬 병합 정리
    int deduped = deduplicateSkills();

    // 1. 최신 스킬 사전 캐시 갱신
    skillTaxonomy.reloadTaxonomy();

    List<JobPosting> postings = jobPostingRepository.findAll();
    int total = postings.size();
    int refinedCount = 0;
    int withSkillsCount = 0;
    int totalSkillLinks = 0;

    log.info("[스킬 재정제] 총 {}건의 공고를 대상으로 재정제를 시작합니다. (중복 정리 {}건)", total, deduped);

    for (JobPosting posting : postings) {
      List<String> currentSkillNames =
          posting.getSkills().stream().map(Skill::getStandardName).toList();

      applySkillsToPosting(posting, posting.getTitle(), currentSkillNames);
      jobPostingRepository.save(posting);

      int count = posting.getSkills().size();
      if (count > 0) {
        withSkillsCount++;
        totalSkillLinks += count;
      }
      refinedCount++;
    }

    log.info(
        "[스킬 재정제] 완료: 총 {}건 대상 중 스킬 보유 공고 {}건, 총 매핑 링크 {}건",
        refinedCount,
        withSkillsCount,
        totalSkillLinks);

    return Map.of(
        "totalPostings", total,
        "refinedPostings", refinedCount,
        "postingsWithSkills", withSkillsCount,
        "totalSkillLinks", totalSkillLinks,
        "dedupedSkills", deduped);
  }

  /**
   * DB에 동일한 standard_name(대소문자 무시)으로 여러 행이 존재하는 경우, 가장 구체적인 카테고리를 가진 행(SKILL 이외)을 대표로 남기고 나머지를 벌크
   * 쿼리로 안전하게 병합·정리합니다. (영속성 컨텍스트 충돌 및 외래키 제약 오류 방지)
   */
  @Transactional
  public int deduplicateSkills() {
    List<Skill> allSkills = skillRepository.findAll();
    Map<String, List<Skill>> byName = new HashMap<>();
    for (Skill s : allSkills) {
      byName
          .computeIfAbsent(
              s.getStandardName().strip().toLowerCase(java.util.Locale.ROOT),
              k -> new ArrayList<>())
          .add(s);
    }

    int removedDuplicates = 0;
    for (List<Skill> dupGroup : byName.values()) {
      if (dupGroup.size() <= 1) {
        continue;
      }

      // 1. 대표 스킬 선택: 카테고리가 "SKILL"이 아닌 것 우선, 없으면 첫 번째
      Skill canonical =
          dupGroup.stream()
              .filter(s -> !"SKILL".equalsIgnoreCase(s.getCategory()))
              .findFirst()
              .orElse(dupGroup.get(0));

      for (Skill dup : dupGroup) {
        if (dup.getId().equals(canonical.getId())) {
          continue;
        }

        UUID dupId = dup.getId();
        UUID canonicalId = canonical.getId();

        // 2. JobPostingSkill 링크 정리 (벌크 쿼리 지원 시 안전하게 선행 삭제 후 재할당)
        if (jobPostingSkillRepository != null) {
          jobPostingSkillRepository.deleteDuplicateLinks(dupId, canonicalId);
          jobPostingSkillRepository.reassignSkill(dupId, canonical);
        } else {
          for (JobPosting posting : jobPostingRepository.findAll()) {
            boolean hasDup = posting.getSkills().stream().anyMatch(s -> s.getId().equals(dupId));
            if (hasDup) {
              List<Skill> updatedSkills = new ArrayList<>(posting.getSkills());
              updatedSkills.removeIf(s -> s.getId().equals(dupId));
              if (updatedSkills.stream().noneMatch(s -> s.getId().equals(canonicalId))) {
                updatedSkills.add(canonical);
              }
              posting.replaceSkills(updatedSkills);
              jobPostingRepository.save(posting);
            }
          }
        }

        // 3. SkillAlias 별칭 링크 정리
        if (skillAliasRepository != null) {
          try {
            skillAliasRepository.reassignSkill(dupId, canonical);
          } catch (Exception e) {
            log.trace("[스킬 중복제거] 별칭 정리 건너뜀: {}", e.getMessage());
          }
        }

        // 4. UserSkill (사용자 보유 스킬) 링크 정리
        if (userSkillRepository != null) {
          try {
            userSkillRepository.deleteDuplicateLinks(dupId, canonicalId);
            userSkillRepository.reassignSkill(dupId, canonical);
          } catch (Exception e) {
            log.trace("[스킬 중복제거] user_skill 정리 건너뜀: {}", e.getMessage());
          }
        }

        // 5. 미참조 상태가 된 중복 스킬 엔티티 직접 삭제
        try {
          skillRepository.deleteSkillDirectly(dupId);
          removedDuplicates++;
        } catch (Exception e) {
          log.warn("[스킬 중복제거] 스킬 ID {} 삭제 중 건너뜀: {}", dupId, e.getMessage());
        }
      }
    }

    if (removedDuplicates > 0) {
      log.info("[스킬 중복제거] 총 {}건의 중복 스킬 엔티티를 병합 및 삭제했습니다.", removedDuplicates);
    }
    return removedDuplicates;
  }

  private Skill upsertSkill(String standardName, String category) {
    Optional<Skill> byName = skillRepository.findFirstByStandardNameIgnoreCase(standardName);
    if (byName.isPresent()) {
      Skill existing = byName.get();
      if ("SKILL".equalsIgnoreCase(existing.getCategory())
          && category != null
          && !"SKILL".equalsIgnoreCase(category)) {
        existing.recategorize(category);
        return skillRepository.save(existing);
      }
      return existing;
    }
    return skillRepository
        .findByStandardNameAndCategory(standardName, category)
        .orElseGet(() -> skillRepository.save(new Skill(standardName, category, false)));
  }

  /** 수집 이력을 raw_ingest 테이블에 중복 없이 기록 및 보존합니다. */
  private void persistRawIngestIfAbsent(
      String source, String sourceItemId, Instant collectedAt, String month) {
    if (rawIngestRepository.findBySourceAndSourceItemId(source, sourceItemId).isPresent()) {
      return;
    }
    rawIngestRepository.save(new RawIngest(source, sourceItemId, month, month, collectedAt));
  }

  /** 공고 상세페이지 URL에 접근하여 원문 HTML을 가져옵니다. */
  private String fetchDetailHtml(String link) {
    try {
      Document detailDoc =
          Jsoup.connect(link)
              .userAgent(
                  "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
              .timeout(10000)
              .get();
      return detailDoc.outerHtml();
    } catch (Exception e) {
      log.warn("[잡코리아] 상세페이지 HTML 수집 실패 (링크: {}): {}", link, e.getMessage());
      return null;
    }
  }

  /** 사람인 크롤링 방어 우회를 위한 세션 쿠키를 초기화합니다. */
  private Map<String, String> initSaraminSessionCookies() {
    try {
      org.jsoup.Connection.Response res =
          Jsoup.connect("https://www.saramin.co.kr/")
              .userAgent(
                  "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
              .timeout(10000)
              .execute();
      return res.cookies();
    } catch (Exception e) {
      log.warn("[사람인] 세션 쿠키 발급 실패 (헤더 없이 진행): {}", e.getMessage());
      return new HashMap<>();
    }
  }

  private List<String> distinct(List<String> list) {
    return list.stream().distinct().toList();
  }

  private void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
