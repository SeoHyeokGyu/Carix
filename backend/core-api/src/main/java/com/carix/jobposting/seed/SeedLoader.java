package com.carix.jobposting.seed;

import com.carix.jobposting.JobPosting;
import com.carix.jobposting.JobPostingRepository;
import com.carix.jobposting.RawIngest;
import com.carix.jobposting.RawIngestRepository;
import com.carix.jobposting.Skill;
import com.carix.jobposting.SkillRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

public class SeedLoader {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final String SEED_MONTH = "202608";
  private static final String SCHEMA_VERSION = "v1-seed";

  private final JobPostingRepository jobPostingRepository;
  private final SkillRepository skillRepository;
  private final RawIngestRepository rawIngestRepository;
  private final JobKoreaListingEnricher jobKoreaListingEnricher = new JobKoreaListingEnricher();
  private final SaraminListingEnricher saraminListingEnricher = new SaraminListingEnricher();

  public SeedLoader(JobPostingRepository jobPostingRepository, SkillRepository skillRepository,
      RawIngestRepository rawIngestRepository) {
    this.jobPostingRepository = jobPostingRepository;
    this.skillRepository = skillRepository;
    this.rawIngestRepository = rawIngestRepository;
  }

  public int load(Path verificationOutDir) throws IOException {
    ListingPageIndex listingIndex = new ListingPageIndex();
    listingIndex.build(verificationOutDir.resolve("raw"));

    int loaded = 0;
    loaded += loadCsv(verificationOutDir.resolve("jobkorea_jobs.csv"), "JOBKOREA", listingIndex,
        verificationOutDir.resolve("raw/JOBKOREA"));
    loaded += loadCsv(verificationOutDir.resolve("saramin_jobs.csv"), "SARAMIN", listingIndex,
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

  private boolean loadRow(CSVRecord record, String source, ListingPageIndex listingIndex,
      Path detailRawDir) throws IOException {
    String externalId = record.get("ID");
    if (jobPostingRepository.findBySourceAndExternalId(source, externalId).isPresent()) {
      return false;
    }

    if (source.equals("JOBKOREA")) {
      return loadJobKoreaRow(record, externalId, listingIndex, detailRawDir);
    }
    return loadSaraminRow(record, externalId, listingIndex);
  }

  private boolean loadJobKoreaRow(CSVRecord record, String externalId,
      ListingPageIndex listingIndex, Path detailRawDir) throws IOException {
    Path detailFile = detailRawDir.resolve("detail-" + externalId + ".json");
    Instant collectedAt = Files.exists(detailFile)
        ? persistRawIngestFromDetailFile("JOBKOREA", "detail-" + externalId, detailFile)
        : Instant.parse("2026-08-09T00:00:00Z");

    String listingHtml = listingIndex.htmlForJobKorea(externalId);
    JobKoreaListingFields fields = listingHtml != null
        ? jobKoreaListingEnricher.extract(listingHtml, externalId)
        : new JobKoreaListingFields(null, null, null, null, null, null);

    JobPosting posting = new JobPosting(
        "JOBKOREA", externalId, record.get("Title"), record.get("Company"),
        fields.salaryMin(), fields.salaryMax(), record.get("Location"),
        parseIntOrNull(record.get("CareerRange")), null,
        fields.employmentType(), null, fields.companyBadge(), fields.positionLevel(), null, null,
        JobPostingDateParser.parseCreatedAt("JOBKOREA", record.get("CreatedAt"), collectedAt),
        JobPostingDateParser.parseDeadline("JOBKOREA", fields.postedEndDateRaw(), collectedAt),
        SEED_MONTH, SEED_MONTH, collectedAt);

    for (String skillName : distinct(splitSkills(record.get("Skills")))) {
      posting.addSkill(upsertSkill(skillName, "SKILL"));
    }

    jobPostingRepository.save(posting);
    return true;
  }

  private boolean loadSaraminRow(CSVRecord record, String externalId,
      ListingPageIndex listingIndex) throws IOException {
    ListingPageIndex.ListpageEntry listpage = listingIndex.listpageForSaramin(externalId);
    Instant collectedAt = listpage != null
        ? persistRawIngestFromListpage("SARAMIN", listpage)
        : Instant.parse("2026-08-09T00:00:00Z");

    SaraminListingFields fields = listpage != null
        ? saraminListingEnricher.extract(listpage.html(), externalId)
        : new SaraminListingFields(null, null, List.of(), List.of());

    JobPosting posting = new JobPosting(
        "SARAMIN", externalId, record.get("Title"), record.get("Company"),
        null, null, record.get("Location"),
        parseIntOrNull(record.get("CareerRange")), null,
        fields.employmentType(), null, null, null, null, null,
        JobPostingDateParser.parseCreatedAt("SARAMIN", record.get("CreatedAt"), collectedAt),
        JobPostingDateParser.parseDeadline("SARAMIN", fields.postedEndDateRaw(), collectedAt),
        SEED_MONTH, SEED_MONTH, collectedAt);

    for (String skillName : distinct(fields.skillTags())) {
      posting.addSkill(upsertSkill(skillName, "SKILL"));
    }
    for (String industryName : distinct(fields.industryTags())) {
      posting.addSkill(upsertSkill(industryName, "INDUSTRY"));
    }

    jobPostingRepository.save(posting);
    return true;
  }

  private Skill upsertSkill(String standardName, String category) {
    return skillRepository.findByStandardNameAndCategory(standardName, category)
        .orElseGet(() -> skillRepository.save(new Skill(standardName, category, true)));
  }

  private List<String> splitSkills(String csvSkillsCell) {
    if (csvSkillsCell == null || csvSkillsCell.isBlank()) {
      return List.of();
    }
    return List.of(csvSkillsCell.split(";"));
  }

  /** 원본 목록페이지 HTML에 동일 태그가 두 번 실려 있는 경우가 있어(예: 사람인),
   *  같은 posting에 같은 skill을 두 번 addSkill하면 (job_posting_id, skill_id) 유니크
   *  제약을 위반한다. */
  private List<String> distinct(List<String> values) {
    return values.stream().distinct().toList();
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

  /** 잡코리아 상세페이지 1건 = raw_ingest 1행(1:1). */
  private Instant persistRawIngestFromDetailFile(String source, String sourceItemId, Path detailFile)
      throws IOException {
    JsonNode node = OBJECT_MAPPER.readTree(detailFile.toFile());
    Instant collectedAt = Instant.parse(node.path("collectedAt").asText());
    persistRawIngestIfAbsent(source, sourceItemId, node.path("rawContent").asText(), collectedAt);
    return collectedAt;
  }

  /**
   * 사람인 목록페이지 1개 = raw_ingest 1행을 여러 공고가 공유(N:1). 같은 listpage를
   * 참조하는 두 번째 이후 공고는 findBySourceAndSourceItemId에서 이미 있으니 skip된다.
   */
  private Instant persistRawIngestFromListpage(String source, ListingPageIndex.ListpageEntry listpage) {
    persistRawIngestIfAbsent(source, listpage.sourceItemId(), listpage.html(), listpage.collectedAt());
    return listpage.collectedAt();
  }

  private void persistRawIngestIfAbsent(String source, String sourceItemId, String html,
      Instant collectedAt) {
    if (rawIngestRepository.findBySourceAndSourceItemId(source, sourceItemId).isPresent()) {
      return;
    }
    String payload;
    try {
      payload = OBJECT_MAPPER.writeValueAsString(Map.of("html", html));
    } catch (IOException e) {
      throw new IllegalStateException("Failed to serialize raw_ingest payload for "
          + source + "/" + sourceItemId, e);
    }
    rawIngestRepository.save(new RawIngest(
        source, sourceItemId, payload, SCHEMA_VERSION, SEED_MONTH, SEED_MONTH, collectedAt));
  }
}
