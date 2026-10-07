package com.carix.jobposting.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.carix.jobposting.CollectedVia;
import com.carix.jobposting.JobPosting;
import com.carix.jobposting.JobPostingRepository;
import com.carix.jobposting.RawIngestRepository;
import com.carix.jobposting.Skill;
import com.carix.jobposting.SkillRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

class SeedLoaderJobKoreaTest {

  @TempDir Path outDir;

  private JobPostingRepository jobPostingRepository;
  private SkillRepository skillRepository;
  private RawIngestRepository rawIngestRepository;
  private SeedLoader seedLoader;

  @BeforeEach
  void setUp() {
    jobPostingRepository = mock(JobPostingRepository.class);
    skillRepository = mock(SkillRepository.class);
    rawIngestRepository = mock(RawIngestRepository.class);

    given(jobPostingRepository.findBySourceAndExternalId(anyString(), anyString()))
        .willReturn(Optional.empty());
    given(jobPostingRepository.save(any(JobPosting.class))).willAnswer(i -> i.getArgument(0));
    given(skillRepository.findByStandardNameAndCategory(anyString(), anyString()))
        .willReturn(Optional.empty());
    given(skillRepository.save(any(Skill.class))).willAnswer(i -> i.getArgument(0));

    seedLoader = new SeedLoader(jobPostingRepository, skillRepository, rawIngestRepository);
  }

  @Test
  @DisplayName("load - 잡코리아 CSV 및 상세/목록 JSON 적재 성공")
  void load_jobKorea_withDetailAndListpage() throws IOException {
    Path jkRaw = outDir.resolve("raw/JOBKOREA");
    Files.createDirectories(jkRaw);

    String listingHtml =
        """
        <table>
          <tr class="devloopArea" data-gno="9901">
            <td>
              <p class="etc">
                <span class="cell">경력 3~5년</span>
                <span class="cell">학력무관</span>
                <span class="cell">서울 강남구</span>
                <span class="cell">정규직</span>
                <span class="cell">5,000 ~ 7,000 만원</span>
                <span class="cell">팀원</span>
              </p>
            </td>
          </tr>
        </table>
        """;

    String jsonListing =
        """
        {
          "rawId": "listpage-1",
          "rawContent": "<table><tr class=\\"devloopArea\\" data-gno=\\"9901\\"><td><p class=\\"etc\\"><span class=\\"cell\\">경력 3~5년</span><span class=\\"cell\\">학력무관</span><span class=\\"cell\\">서울 강남구</span><span class=\\"cell\\">정규직</span><span class=\\"cell\\">5,000 ~ 7,000 만원</span><span class=\\"cell\\">팀원</span></p></td></tr></table>"
        }
        """;
    Files.writeString(jkRaw.resolve("listpage-1.json"), jsonListing, StandardCharsets.UTF_8);

    String jsonDetail =
        """
        {
          "rawContent": "<div class=\\"tbList\\"><dl class=\\"tbList\\"><dt>급여</dt><dd>연봉 5,000~7,000만원</dd><dt>직무타입</dt><dd>서버/백엔드개발자</dd><dt>사원수</dt><dd>150명</dd><dt>기업형태</dt><dd>중소기업</dd></dl></div>",
          "collectedAt": "2026-08-09T00:00:00Z"
        }
        """;
    Files.writeString(jkRaw.resolve("detail-9901.json"), jsonDetail, StandardCharsets.UTF_8);

    String csvContent =
        """
        Portal,ID,Company,Title,CareerType,CareerRange,Education,Location,Link,CreatedAt,Skills,SalaryRaw
        JOBKOREA,9901,테스트회사,백엔드 엔지니어,경력 3~5년,5,대졸,서울 강남구,https://test.com,3일 전,Java;Spring Boot,
        """;

    Files.writeString(outDir.resolve("jobkorea_jobs.csv"), csvContent, StandardCharsets.UTF_8);

    int loaded = seedLoader.load(outDir);

    assertThat(loaded).isEqualTo(1);
    assertThat(savedPosting().getCollectedVia()).isEqualTo(CollectedVia.JOBKOREA_SEED);
  }

  private JobPosting savedPosting() {
    ArgumentCaptor<JobPosting> saved = ArgumentCaptor.forClass(JobPosting.class);
    verify(jobPostingRepository, atLeastOnce()).save(saved.capture());
    return saved.getValue();
  }

  @Test
  @DisplayName("load - 파일이 없으면 0 반환")
  void load_noFiles() throws IOException {
    int loaded = seedLoader.load(outDir);
    assertThat(loaded).isEqualTo(0);
  }

  @Test
  @DisplayName("load - 이미 존재하는 공고는 건너뛴다")
  void load_alreadyExists() throws IOException {
    given(jobPostingRepository.findBySourceAndExternalId("JOBKOREA", "9902"))
        .willReturn(Optional.of(JobPosting.builder().build()));

    String csvContent =
        """
        Portal,ID,Company,Title,CareerType,CareerRange,Education,Location,Link,CreatedAt,Skills,SalaryRaw
        JOBKOREA,9902,테스트회사,백엔드 엔지니어,경력무관,0,대졸,서울 강남구,https://test.com,3일 전,Java,
        """;

    Files.writeString(outDir.resolve("jobkorea_jobs.csv"), csvContent, StandardCharsets.UTF_8);

    int loaded = seedLoader.load(outDir);
    assertThat(loaded).isEqualTo(0);
  }

  @Test
  @DisplayName("load - 사람인 CSV 및 목록 JSON 적재 성공")
  void load_saramin_withListpage() throws IOException {
    Path srRaw = outDir.resolve("raw/SARAMIN");
    Files.createDirectories(srRaw);

    String jsonListing =
        """
        {
          "rawId": "listpage-1",
          "rawContent": "<div class=\\"item_recruit\\" value=\\"8801\\"><div class=\\"area_corp\\"><strong class=\\"corp_name\\"><a>사람인회사</a></strong></div><div class=\\"area_job\\"><h2 class=\\"job_tit\\"><a>백엔드 개발자</a></h2><div class=\\"job_condition\\"><span>서울 구로구</span><span>경력 2~4년</span></div><div class=\\"job_sector\\"><a class=\\"track_event\\">Java</a><a class=\\"track_event\\">IT/인터넷</a></div></div></div>",
          "collectedAt": "2026-08-09T00:00:00Z"
        }
        """;
    Files.writeString(srRaw.resolve("listpage-1.json"), jsonListing, StandardCharsets.UTF_8);

    String csvContent =
        """
        Portal,ID,Company,Title,CareerType,CareerRange,Education,Location,Link,CreatedAt,Skills,SalaryRaw
        SARAMIN,8801,사람인회사,백엔드 개발자,경력 2~4년,4,대졸,서울 구로구,https://saramin.com,오늘,Java,
        """;

    Files.writeString(outDir.resolve("saramin_jobs.csv"), csvContent, StandardCharsets.UTF_8);

    int loaded = seedLoader.load(outDir);

    assertThat(loaded).isEqualTo(1);
    assertThat(savedPosting().getCollectedVia()).isEqualTo(CollectedVia.SARAMIN_SEARCH);
  }
}
