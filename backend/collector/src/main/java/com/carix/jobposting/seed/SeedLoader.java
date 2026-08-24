package com.carix.jobposting.seed;

import com.carix.crawler.JobKoreaDetailParser;
import com.carix.jobposting.JobPosting;
import com.carix.jobposting.JobPostingRepository;
import com.carix.jobposting.RawIngest;
import com.carix.jobposting.RawIngestIds;
import com.carix.jobposting.RawIngestRepository;
import com.carix.jobposting.Skill;
import com.carix.jobposting.SkillRepository;
import com.carix.jobposting.normalize.CompanySizeClassifier;
import com.carix.jobposting.normalize.EmploymentTypeClassifier;
import com.carix.jobposting.normalize.ExperienceRange;
import com.carix.jobposting.normalize.JobFamilyClassifier;
import com.carix.jobposting.normalize.JobKoreaPayParser;
import com.carix.jobposting.normalize.SalaryRange;
import com.carix.jobposting.normalize.SkillTaxonomy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

public class SeedLoader {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final String SEED_MONTH = "202608";

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

  public SeedLoader(
      JobPostingRepository jobPostingRepository,
      SkillRepository skillRepository,
      RawIngestRepository rawIngestRepository) {
    this.jobPostingRepository = jobPostingRepository;
    this.skillRepository = skillRepository;
    this.rawIngestRepository = rawIngestRepository;
  }

  public int load(Path verificationOutDir) throws IOException {
    ListingPageIndex listingIndex = new ListingPageIndex();
    listingIndex.build(verificationOutDir.resolve("raw"));

    int loaded = 0;
    loaded +=
        loadCsv(
            verificationOutDir.resolve("jobkorea_jobs.csv"),
            "JOBKOREA",
            listingIndex,
            verificationOutDir.resolve("raw/JOBKOREA"));
    loaded +=
        loadCsv(
            verificationOutDir.resolve("saramin_jobs.csv"),
            "SARAMIN",
            listingIndex,
            verificationOutDir.resolve("raw/SARAMIN"));
    return loaded;
  }

  private int loadCsv(Path csvPath, String source, ListingPageIndex listingIndex, Path detailRawDir)
      throws IOException {
    if (!Files.exists(csvPath)) {
      return 0;
    }
    int count = 0;
    try (Reader reader = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8)) {
      CSVFormat format = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build();
      try (CSVParser parser = new CSVParser(reader, format)) {
        for (CSVRecord record : parser) {
          if (loadRow(record, source, listingIndex, detailRawDir)) {
            count++;
          }
        }
      }
    }
    return count;
  }

  private boolean loadRow(
      CSVRecord record, String source, ListingPageIndex listingIndex, Path detailRawDir)
      throws IOException {
    String externalId = record.get("ID");
    if (jobPostingRepository.findBySourceAndExternalId(source, externalId).isPresent()) {
      return false;
    }

    if (source.equals("JOBKOREA")) {
      return loadJobKoreaRow(record, externalId, listingIndex, detailRawDir);
    }
    return loadSaraminRow(record, externalId, listingIndex);
  }

  private boolean loadJobKoreaRow(
      CSVRecord record, String externalId, ListingPageIndex listingIndex, Path detailRawDir)
      throws IOException {
    Path detailFile = detailRawDir.resolve("detail-" + externalId + ".json");
    String detailHtml = null;
    Instant collectedAt = Instant.parse("2026-08-09T00:00:00Z");
    if (Files.exists(detailFile)) {
      JsonNode detail = OBJECT_MAPPER.readTree(detailFile.toFile());
      detailHtml = detail.path("rawContent").asText(null);
      collectedAt = Instant.parse(detail.path("collectedAt").asText());
      persistRawIngestIfAbsent("JOBKOREA", "detail-" + externalId, collectedAt);
    }

    String listingHtml = listingIndex.htmlForJobKorea(externalId);
    JobKoreaListingFields fields =
        listingHtml != null
            ? jobKoreaListingEnricher.extract(listingHtml, externalId)
            : new JobKoreaListingFields(null, null, null, null, null, null);

    // 상세페이지 급여 블록이 목록페이지 셀보다 커버리지가 훨씬 넓다(실측 400건 중 317 대 36).
    SalaryRange salary =
        detailHtml == null
            ? null
            : jobKoreaPayParser.parse(jobKoreaDetailParser.extractSalaryRaw(detailHtml));
    // 상세페이지가 있으면 그 급여 블록이 판정 권위다. 목록페이지 셀은 상세가 없을 때만 쓴다.
    Long salaryMin = detailHtml != null ? valueOf(salary, SalaryRange::min) : fields.salaryMin();
    Long salaryMax = detailHtml != null ? valueOf(salary, SalaryRange::max) : fields.salaryMax();
    String jobFamily =
        detailHtml == null
            ? JobFamilyClassifier.OTHER
            : jobFamilyClassifier.classify(jobKoreaDetailParser.extractJobTypeName(detailHtml));
    String companySize =
        detailHtml == null
            ? CompanySizeClassifier.UNKNOWN
            : companySizeClassifier.classify(
                jobKoreaDetailParser.extractEmployeeCount(detailHtml),
                jobKoreaDetailParser.extractCompanyTypeName(detailHtml));

    // CSV `CareerRange` 는 크롤러가 범위를 한 값으로 뭉갠 결과다. 원천 표기에서 다시 가른다.
    ExperienceRange experience = ExperienceRange.parse(record.get("CareerType"));

    JobPosting posting =
        new JobPosting(
            "JOBKOREA",
            externalId,
            record.get("Title"),
            record.get("Company"),
            salaryMin,
            salaryMax,
            record.get("Location"),
            experience.min(),
            experience.max(),
            employmentTypeClassifier.classify(fields.employmentType()),
            companySize,
            fields.companyBadge(),
            fields.positionLevel(),
            jobFamily,
            JobPostingDateParser.parseCreatedAt("JOBKOREA", record.get("CreatedAt"), collectedAt),
            JobPostingDateParser.parseDeadline("JOBKOREA", fields.postedEndDateRaw(), collectedAt),
            SEED_MONTH,
            SEED_MONTH,
            collectedAt);

    // CSV 의 Skills 열에는 스킬 유형이 없어 인성 역량을 걸러낼 수 없다. 상세페이지가 있으면
    // 거기서 직무 역량만 뽑고, 없을 때만 CSV 로 물러선다.
    List<String> skillNames =
        detailHtml != null
            ? jobKoreaDetailParser.extractSkills(detailHtml)
            : splitSkills(record.get("Skills"));
    addSkills(posting, skillNames);

    jobPostingRepository.save(posting);
    return true;
  }

  private boolean loadSaraminRow(CSVRecord record, String externalId, ListingPageIndex listingIndex)
      throws IOException {
    ListingPageIndex.ListpageEntry listpage = listingIndex.listpageForSaramin(externalId);
    Instant collectedAt =
        listpage != null
            ? persistRawIngestFromListpage("SARAMIN", listpage)
            : Instant.parse("2026-08-09T00:00:00Z");

    SaraminListingFields fields =
        listpage != null
            ? saraminListingEnricher.extract(listpage.html(), externalId)
            : new SaraminListingFields(null, null, null, List.of(), List.of());

    ExperienceRange experience = ExperienceRange.parse(record.get("CareerType"));

    JobPosting posting =
        new JobPosting(
            "SARAMIN",
            externalId,
            record.get("Title"),
            record.get("Company"),
            null,
            null,
            record.get("Location"),
            experience.min(),
            experience.max(),
            employmentTypeClassifier.classify(fields.employmentType()),
            CompanySizeClassifier.UNKNOWN,
            null,
            null,
            jobFamilyClassifier.classifyTags(fields.skillTags()),
            JobPostingDateParser.parseCreatedAt("SARAMIN", record.get("CreatedAt"), collectedAt),
            JobPostingDateParser.parseDeadline("SARAMIN", fields.postedEndDateRaw(), collectedAt),
            SEED_MONTH,
            SEED_MONTH,
            collectedAt);

    addSkills(posting, fields.skillTags());
    for (String industryName : distinct(fields.industryTags())) {
      posting.addSkill(upsertSkill(skillTaxonomy.canonicalName(industryName), "INDUSTRY"));
    }

    jobPostingRepository.save(posting);
    return true;
  }

  /**
   * 원천 표기를 표준 표기로 합치고 분류를 매겨 공고에 붙인다. 직무 카테고리 태그는 버리지 않고 `JOB_CATEGORY` 로 분리해 스킬 수요 집계에서만 빠지게 한다.
   */
  private void addSkills(JobPosting posting, List<String> rawNames) {
    Set<String> applied = new LinkedHashSet<>();
    for (String rawName : rawNames) {
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

  /** 수집으로 관측된 스킬이므로 `is_custom` 은 false 다 — 사용자 직접 입력분이 아니다. */
  private Skill upsertSkill(String standardName, String category) {
    return skillRepository
        .findByStandardNameAndCategory(standardName, category)
        .orElseGet(() -> skillRepository.save(new Skill(standardName, category, false)));
  }

  private List<String> splitSkills(String csvSkillsCell) {
    if (csvSkillsCell == null || csvSkillsCell.isBlank()) {
      return List.of();
    }
    return List.of(csvSkillsCell.split(";"));
  }

  /**
   * 원본 목록페이지 HTML에 동일 태그가 두 번 실려 있는 경우가 있어(예: 사람인), 같은 posting에 같은 skill을 두 번 addSkill하면
   * (job_posting_id, skill_id) 유니크 제약을 위반한다.
   */
  private List<String> distinct(List<String> values) {
    return values.stream().distinct().toList();
  }

  private Long valueOf(SalaryRange salary, java.util.function.Function<SalaryRange, Long> field) {
    return salary == null ? null : field.apply(salary);
  }

  private Integer parseIntOrNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /**
   * 사람인 목록페이지 1개 = raw_ingest 1행을 여러 공고가 공유(N:1). 같은 listpage를 참조하는 두 번째 이후 공고는
   * findBySourceAndSourceItemId에서 이미 있으니 skip된다.
   */
  private Instant persistRawIngestFromListpage(
      String source, ListingPageIndex.ListpageEntry listpage) {
    persistRawIngestIfAbsent(
        source,
        RawIngestIds.datedListpage(listpage.sourceItemId(), listpage.collectedAt()),
        listpage.collectedAt());
    return listpage.collectedAt();
  }

  private void persistRawIngestIfAbsent(String source, String sourceItemId, Instant collectedAt) {
    if (rawIngestRepository.findBySourceAndSourceItemId(source, sourceItemId).isPresent()) {
      return;
    }
    rawIngestRepository.save(
        new RawIngest(source, sourceItemId, SEED_MONTH, SEED_MONTH, collectedAt));
  }
}
