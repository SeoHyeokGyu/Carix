package com.carix.jobposting.service;

import com.carix.crawler.JobKoreaDetailParser;
import com.carix.jobposting.JobPosting;
import com.carix.jobposting.JobPostingRepository;
import com.carix.jobposting.RawIngest;
import com.carix.jobposting.RawIngestIds;
import com.carix.jobposting.RawIngestPayloads;
import com.carix.jobposting.RawIngestRepository;
import com.carix.jobposting.Skill;
import com.carix.jobposting.SkillRepository;
import com.carix.jobposting.normalize.CompanySizeClassifier;
import com.carix.jobposting.normalize.EmploymentTypeClassifier;
import com.carix.jobposting.normalize.JobFamilyClassifier;
import com.carix.jobposting.normalize.JobKoreaPayParser;
import com.carix.jobposting.normalize.SalaryRange;
import com.carix.jobposting.normalize.SkillTaxonomy;
import com.carix.jobposting.seed.JobKoreaListingEnricher;
import com.carix.jobposting.seed.JobKoreaListingFields;
import com.carix.jobposting.seed.JobPostingDateParser;
import com.carix.jobposting.seed.SaraminListingEnricher;
import com.carix.jobposting.seed.SaraminListingFields;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobPostingIngestService {

  private static final Logger log = LoggerFactory.getLogger(JobPostingIngestService.class);
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final String SCHEMA_VERSION = "v1-live";
  private static final DateTimeFormatter YM_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");

  private static final String JOBKOREA_LIST_ENDPOINT =
      "https://www.jobkorea.co.kr/Recruit/Home/_GI_List/";
  private static final String DUTY_CATEGORY_IT = "10031";
  private static final String JOBKOREA_LIST_REFERER =
      "https://www.jobkorea.co.kr/recruit/joblist?menucode=duty&dutyCtgr=" + DUTY_CATEGORY_IT;

  private final JobPostingRepository jobPostingRepository;
  private final SkillRepository skillRepository;
  private final RawIngestRepository rawIngestRepository;

  private final JobKoreaListingEnricher jobKoreaListingEnricher = new JobKoreaListingEnricher();
  private final SaraminListingEnricher saraminListingEnricher = new SaraminListingEnricher();
  private final JobKoreaDetailParser jobKoreaDetailParser = new JobKoreaDetailParser();
  private final JobKoreaPayParser jobKoreaPayParser = new JobKoreaPayParser();
  private final JobFamilyClassifier jobFamilyClassifier = new JobFamilyClassifier();
  private final CompanySizeClassifier companySizeClassifier = new CompanySizeClassifier();
  private final EmploymentTypeClassifier employmentTypeClassifier = new EmploymentTypeClassifier();
  private final SkillTaxonomy skillTaxonomy = new SkillTaxonomy();

  public JobPostingIngestService(
      JobPostingRepository jobPostingRepository,
      SkillRepository skillRepository,
      RawIngestRepository rawIngestRepository) {
    this.jobPostingRepository = jobPostingRepository;
    this.skillRepository = skillRepository;
    this.rawIngestRepository = rawIngestRepository;
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
            "JOBKOREA",
            RawIngestIds.datedListpage("listpage-" + page, now),
            RawIngestPayloads.listpage(listDoc.outerHtml()),
            now,
            currentMonth);

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
          Integer careerRange = extractCareerYears(careerType);

          String createdAtRaw = "";
          Element timeSpan = row.selectFirst("td.odd span.time");
          if (timeSpan != null) {
            createdAtRaw = timeSpan.text().strip();
          }

          // 상세 페이지 수집 및 DB 저장
          String detailHtml = fetchDetailHtml(link);
          if (detailHtml != null) {
            persistRawIngestIfAbsent(
                "JOBKOREA", "detail-" + externalId, detailHtml, now, currentMonth);
          }

          JobKoreaListingFields fields =
              jobKoreaListingEnricher.extract(listDoc.outerHtml(), externalId);

          ingestJobKoreaPosting(
              externalId,
              title,
              companyName,
              location,
              careerRange,
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
            "SARAMIN",
            RawIngestIds.datedListpage("listpage-" + page, now),
            RawIngestPayloads.listpage(doc.outerHtml()),
            now,
            currentMonth);

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
          Integer careerRange = extractCareerYears(career);

          Element dateSpan = item.selectFirst("div.area_job div.job_date span.date");
          String createdAtRaw = dateSpan != null ? dateSpan.text().strip() : "";

          SaraminListingFields fields = saraminListingEnricher.extract(doc.outerHtml(), externalId);

          ingestSaraminPosting(
              externalId,
              title,
              companyName,
              location,
              careerRange,
              createdAtRaw,
              fields,
              now,
              currentMonth);

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

  private void ingestJobKoreaPosting(
      String externalId,
      String title,
      String companyName,
      String location,
      Integer careerRange,
      String createdAtRaw,
      JobKoreaListingFields fields,
      String detailHtml,
      Instant now,
      String currentMonth) {
    SalaryRange salary =
        detailHtml != null
            ? jobKoreaPayParser.parse(jobKoreaDetailParser.extractSalaryRaw(detailHtml))
            : null;

    Long salaryMin = salary != null ? salary.min() : fields.salaryMin();
    Long salaryMax = salary != null ? salary.max() : fields.salaryMax();

    String jobFamily =
        detailHtml != null
            ? jobFamilyClassifier.classify(jobKoreaDetailParser.extractJobTypeName(detailHtml))
            : JobFamilyClassifier.OTHER;

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
      posting.applyDisclosedSalary(salaryMin, salaryMax);
      posting.classifyAs(jobFamily);
      posting.classifyCompanySizeAs(companySize);
      posting.classifyEmploymentTypeAs(employmentTypeClassifier.classify(fields.employmentType()));
    } else {
      posting =
          new JobPosting(
              "JOBKOREA",
              externalId,
              title,
              companyName,
              salaryMin,
              salaryMax,
              location,
              careerRange,
              null,
              employmentTypeClassifier.classify(fields.employmentType()),
              companySize,
              fields.companyBadge(),
              fields.positionLevel(),
              jobFamily,
              null,
              JobPostingDateParser.parseCreatedAt("JOBKOREA", createdAtRaw, now),
              JobPostingDateParser.parseDeadline("JOBKOREA", fields.postedEndDateRaw(), now),
              currentMonth,
              currentMonth,
              now);
    }

    List<String> rawSkills =
        detailHtml != null ? jobKoreaDetailParser.extractSkills(detailHtml) : List.of();

    applySkillsToPosting(posting, rawSkills);
    jobPostingRepository.save(posting);
  }

  private void ingestSaraminPosting(
      String externalId,
      String title,
      String companyName,
      String location,
      Integer careerRange,
      String createdAtRaw,
      SaraminListingFields fields,
      Instant now,
      String currentMonth) {
    String jobFamily = jobFamilyClassifier.classifyTags(fields.skillTags());

    Optional<JobPosting> existingOpt =
        jobPostingRepository.findBySourceAndExternalId("SARAMIN", externalId);

    JobPosting posting;
    if (existingOpt.isPresent()) {
      posting = existingOpt.get();
      posting.classifyAs(jobFamily);
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
              careerRange,
              null,
              employmentTypeClassifier.classify(fields.employmentType()),
              CompanySizeClassifier.UNKNOWN,
              null,
              null,
              jobFamily,
              null,
              JobPostingDateParser.parseCreatedAt("SARAMIN", createdAtRaw, now),
              JobPostingDateParser.parseDeadline("SARAMIN", fields.postedEndDateRaw(), now),
              currentMonth,
              currentMonth,
              now);
    }

    applySkillsToPosting(posting, fields.skillTags());
    for (String industryName : distinct(fields.industryTags())) {
      posting.addSkill(upsertSkill(skillTaxonomy.canonicalName(industryName), "INDUSTRY"));
    }

    jobPostingRepository.save(posting);
  }

  private void applySkillsToPosting(JobPosting posting, List<String> rawSkills) {
    Set<String> applied = new LinkedHashSet<>();
    for (String rawName : rawSkills) {
      String canonicalName = skillTaxonomy.canonicalName(rawName);
      if (canonicalName == null || canonicalName.isBlank()) {
        continue;
      }
      String category = skillTaxonomy.category(canonicalName);
      if (applied.add(canonicalName + " " + category)) {
        posting.addSkill(upsertSkill(canonicalName, category));
      }
    }
  }

  private Skill upsertSkill(String standardName, String category) {
    return skillRepository
        .findByStandardNameAndCategory(standardName, category)
        .orElseGet(() -> skillRepository.save(new Skill(standardName, category, false)));
  }

  private void persistRawIngestIfAbsent(
      String source, String sourceItemId, String html, Instant collectedAt, String month) {
    if (rawIngestRepository.findBySourceAndSourceItemId(source, sourceItemId).isPresent()) {
      return;
    }
    try {
      String payload = OBJECT_MAPPER.writeValueAsString(Map.of("html", html));
      rawIngestRepository.save(
          new RawIngest(source, sourceItemId, payload, SCHEMA_VERSION, month, month, collectedAt));
    } catch (IOException e) {
      log.error(
          "Failed to serialize raw_ingest for {}/{}: {}", source, sourceItemId, e.getMessage());
    }
  }

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
      log.warn("Failed to fetch detail html for link {}: {}", link, e.getMessage());
      return null;
    }
  }

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
      log.warn("Failed to fetch Saramin session cookies: {}", e.getMessage());
      return new HashMap<>();
    }
  }

  private int extractCareerYears(String careerText) {
    if (careerText == null
        || careerText.isBlank()
        || careerText.contains("신입")
        || careerText.contains("무관")) {
      return 0;
    }
    Matcher matcher = Pattern.compile("(\\d+)년").matcher(careerText);
    if (matcher.find()) {
      return Integer.parseInt(matcher.group(1));
    }
    return 0;
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
