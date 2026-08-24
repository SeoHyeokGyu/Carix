package com.carix.jobposting.seed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.carix.jobposting.JobPosting;
import com.carix.jobposting.JobPostingRepository;
import com.carix.jobposting.RawIngest;
import com.carix.jobposting.RawIngestRepository;
import com.carix.jobposting.Skill;
import com.carix.jobposting.SkillRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

/**
 * 목록페이지 원본이 회차별로 보존되는지 본다. 08-09 회차가 이미 `listpage-1` 을 차지한 상태에서 08-21 회차를 적재하면, 회차를 구분하지 않는 한 두 번째
 * 목록 HTML 이 유니크 제약에 걸려 사라지고 그 목록에서만 나오는 공고들이 원본 없이 남는다.
 */
class SeedLoaderRawIngestIdTest {

  private static final String SARAMIN_ID = "54616506";

  @TempDir Path outDir;

  private JobPostingRepository jobPostingRepository;
  private SkillRepository skillRepository;
  private RawIngestRepository rawIngestRepository;

  @BeforeEach
  void setUp() throws IOException {
    jobPostingRepository = mock(JobPostingRepository.class);
    skillRepository = mock(SkillRepository.class);
    rawIngestRepository = mock(RawIngestRepository.class);

    when(jobPostingRepository.findBySourceAndExternalId(anyString(), anyString()))
        .thenReturn(Optional.empty());
    when(jobPostingRepository.save(any(JobPosting.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(skillRepository.findByStandardNameAndCategory(anyString(), anyString()))
        .thenReturn(Optional.empty());
    when(skillRepository.save(any(Skill.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(rawIngestRepository.findBySourceAndSourceItemId(anyString(), anyString()))
        .thenReturn(Optional.empty());

    writeSnapshot("2026-08-21T01:15:07Z");
  }

  @Test
  void tagsTheSaraminListpageRawWithItsCollectionDate() throws IOException {
    new SeedLoader(jobPostingRepository, skillRepository, rawIngestRepository).load(outDir);

    assertEquals(List.of("listpage-1-20260821"), savedSourceItemIds());
  }

  /** 같은 목록페이지를 다시 크롤한 회차는 앞 회차와 다른 id 로 들어가야 한다. */
  @Test
  void doesNotReuseTheIdOfAnEarlierCrawlOfTheSamePage() throws IOException {
    writeSnapshot("2026-08-09T13:32:33Z");
    new SeedLoader(jobPostingRepository, skillRepository, rawIngestRepository).load(outDir);

    assertEquals(List.of("listpage-1-20260809"), savedSourceItemIds());
  }

  /** 목록페이지 원문의 대부분은 파서가 안 읽는 script·style 이다. 저장 전에 떼어낸다. */
  @Test
  void storesTheListpageWithoutScriptOrStyle() throws IOException {
    new SeedLoader(jobPostingRepository, skillRepository, rawIngestRepository).load(outDir);

    String payload = savedPayloads().get(0);
    assertFalse(payload.contains("__INITIAL__"), "script 본문이 저장됐다");
    assertFalse(payload.contains("color:#333"), "style 본문이 저장됐다");
    assertTrue(payload.contains("item_recruit"), "목록 항목이 사라졌다");
  }

  /**
   * 사람인은 `경력 3~10년` 처럼 범위를 준다. CSV `CareerRange` 열에는 크롤러가 뭉갠 상한(10)이 들어 있어 그대로 쓰면 하한 자리에 상한이 저장된다 —
   * 3년차도 지원 가능한 공고가 시니어로 분류된다. 원천 표기(`CareerType`)에서 다시 가른다.
   */
  @Test
  void storesBothEndsOfTheExperienceRange() throws IOException {
    new SeedLoader(jobPostingRepository, skillRepository, rawIngestRepository).load(outDir);

    JobPosting saved = savedPostings().get(0);
    assertEquals(3, saved.getExperienceMin(), "하한 자리에 상한이 들어갔다");
    assertEquals(10, saved.getExperienceMax());
  }

  private List<JobPosting> savedPostings() {
    ArgumentCaptor<JobPosting> captor = ArgumentCaptor.forClass(JobPosting.class);
    org.mockito.Mockito.verify(jobPostingRepository, org.mockito.Mockito.atLeastOnce())
        .save(captor.capture());
    return captor.getAllValues();
  }

  private List<String> savedPayloads() {
    ArgumentCaptor<RawIngest> captor = ArgumentCaptor.forClass(RawIngest.class);
    org.mockito.Mockito.verify(rawIngestRepository, org.mockito.Mockito.atLeastOnce())
        .save(captor.capture());
    List<String> payloads = new ArrayList<>();
    for (RawIngest saved : captor.getAllValues()) {
      payloads.add(saved.getPayload());
    }
    return payloads;
  }

  private List<String> savedSourceItemIds() {
    ArgumentCaptor<RawIngest> captor = ArgumentCaptor.forClass(RawIngest.class);
    org.mockito.Mockito.verify(rawIngestRepository, org.mockito.Mockito.atLeastOnce())
        .save(captor.capture());
    List<String> ids = new ArrayList<>();
    for (RawIngest saved : captor.getAllValues()) {
      ids.add(saved.getSourceItemId());
    }
    return ids;
  }

  private void writeSnapshot(String collectedAt) throws IOException {
    Path saraminRaw = outDir.resolve("raw/SARAMIN");
    Files.createDirectories(saraminRaw);

    String listingHtml =
        """
        <style>.job_sector{color:#333}</style>
        <script>window.__INITIAL__={heavy:"수백 KB"};</script>
        <div class="item_recruit" value="%s">
          <div class="area_job">
            <div class="job_sector">
              <a href="/zf_user/search/recruit?cat_kewd=84">Python</a>
              <a href="/zf_user/search/recruit?ind_key=201">SI</a>
            </div>
            <div class="job_date"><span class="date">~ 08/31(월)</span></div>
          </div>
        </div>
        """
            .formatted(SARAMIN_ID);

    Files.writeString(
        saraminRaw.resolve("listpage-1.json"),
        """
        {"rawId":"listpage-1","source":"SARAMIN","collectedAt":"%s","rawContent":%s}
        """
            .formatted(collectedAt, quote(listingHtml)),
        StandardCharsets.UTF_8);

    Files.writeString(
        outDir.resolve("saramin_jobs.csv"),
        """
        Portal,ID,Company,Title,CareerType,CareerRange,Education,Location,Link,CreatedAt,Skills,SalaryRaw
        SARAMIN,%s,씨메스로보틱스(주),Backend 개발자,경력 3~10년,10,대졸↑,서울 강남구,https://example.test,수정일 26/08/02,,
        """
            .formatted(SARAMIN_ID),
        StandardCharsets.UTF_8);
  }

  private static String quote(String value) {
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
  }
}
