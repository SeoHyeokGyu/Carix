package com.carix.jobposting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.carix.jobposting.CollectedVia;
import com.carix.jobposting.JobPosting;
import com.carix.jobposting.JobPostingRepository;
import com.carix.jobposting.RawIngest;
import com.carix.jobposting.RawIngestRepository;
import com.carix.jobposting.Skill;
import com.carix.jobposting.SkillRepository;
import com.carix.jobposting.backfill.JobKoreaJsonLd;
import com.carix.jobposting.backfill.JobKoreaPage;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class JobKoreaBackfillIngestTest {

  private JobPostingRepository jobPostingRepository;
  private RawIngestRepository rawIngestRepository;
  private JobPostingIngestService service;

  private static final JobKoreaPage PAGE =
      new JobKoreaPage(
          "48000001",
          "<script>self.__next_f.push([1,\"\\\"careers\\\":[{\\\"type\\\":\\\"EXPERIENCED\\\","
              + "\\\"range\\\":{\\\"from\\\":3,\\\"to\\\":null}}],\"])</script>",
          new JobKoreaJsonLd(
              "Java 백엔드 개발자",
              "카릭스",
              LocalDate.of(2025, 9, 15),
              LocalDate.of(2025, 10, 15),
              "FULL_TIME",
              "경력 3년↑",
              "서울 강남구"),
          "DEVELOPER");

  @BeforeEach
  void setUp() {
    jobPostingRepository = mock(JobPostingRepository.class);
    SkillRepository skillRepository = mock(SkillRepository.class);
    rawIngestRepository = mock(RawIngestRepository.class);
    given(skillRepository.findByStandardNameAndCategory(anyString(), anyString()))
        .willReturn(Optional.empty());
    given(skillRepository.save(any(Skill.class))).willAnswer(i -> i.getArgument(0));
    given(rawIngestRepository.findBySourceAndSourceItemId(anyString(), anyString()))
        .willReturn(Optional.empty());
    service =
        new JobPostingIngestService(jobPostingRepository, skillRepository, rawIngestRepository);
  }

  @Test
  @DisplayName("백필 공고는 게시일이 속한 달을 처음·마지막 관측 월로 두고 backfill- 원천 항목으로 남긴다")
  void savesWithPostedMonth() {
    given(jobPostingRepository.findBySourceAndExternalId("JOBKOREA", "48000001"))
        .willReturn(Optional.empty());

    boolean saved = service.ingestBackfilledJobKorea(PAGE, "202509", Instant.now());

    assertThat(saved).isTrue();
    ArgumentCaptor<JobPosting> posting = ArgumentCaptor.forClass(JobPosting.class);
    verify(jobPostingRepository).save(posting.capture());
    JobPosting p = posting.getValue();
    assertThat(p.getFirstSeenMonth()).isEqualTo("202509");
    assertThat(p.getSampledMonth()).isEqualTo("202509");
    assertThat(p.getLastSeenMonth()).isEqualTo("202509");
    assertThat(p.getPostedStartDate()).isEqualTo(LocalDate.of(2025, 9, 15));
    assertThat(p.getPostedEndDate()).isEqualTo(LocalDate.of(2025, 10, 15));
    assertThat(p.getEmploymentType()).isEqualTo("FULL_TIME");
    assertThat(p.getExperienceMin()).isEqualTo(3);
    assertThat(p.getJobFamily()).isEqualTo("DEVELOPER");
    assertThat(p.getRegion()).isEqualTo("서울 강남구");
    assertThat(p.getCollectedVia()).isEqualTo(CollectedVia.JOBKOREA_BACKFILL);

    ArgumentCaptor<RawIngest> raw = ArgumentCaptor.forClass(RawIngest.class);
    verify(rawIngestRepository).save(raw.capture());
    assertThat(raw.getValue().getSourceItemId()).isEqualTo("backfill-48000001");
  }

  @Test
  @DisplayName("이미 정기 수집된 공고가 표본에 뽑히면 관측 월은 그대로 두고 표본 월만 단다 — 표본에서 빠지면 표본이 치우친다")
  void marksExistingAsSampled() {
    JobPosting existing = mock(JobPosting.class);
    given(existing.getTitle()).willReturn("Java 백엔드 개발자");
    given(existing.getJobFamily()).willReturn("DEVELOPER");
    given(jobPostingRepository.findBySourceAndExternalId("JOBKOREA", "48000001"))
        .willReturn(Optional.of(existing));

    assertThat(service.ingestBackfilledJobKorea(PAGE, "202509", Instant.now())).isTrue();
    verify(existing).markSampled("202509");
    verify(existing).addCollectedVia(CollectedVia.JOBKOREA_BACKFILL);
    verify(existing, never()).updateLastSeenMonth(any());
    verify(jobPostingRepository).save(existing);
  }

  @Test
  @DisplayName("JSON-LD 고용형태 열거값을 차원 코드로 옮기고, 한글 표기는 기존 분류기로 판정한다")
  void mapsEmploymentType() {
    assertThat(JobPostingIngestService.backfillEmploymentType("FULL_TIME")).isEqualTo("FULL_TIME");
    assertThat(JobPostingIngestService.backfillEmploymentType("CONTRACTOR")).isEqualTo("CONTRACT");
    assertThat(JobPostingIngestService.backfillEmploymentType("INTERN")).isEqualTo("INTERN");
    assertThat(JobPostingIngestService.backfillEmploymentType("계약직")).isEqualTo("CONTRACT");
    assertThat(JobPostingIngestService.backfillEmploymentType(null)).isEqualTo("UNKNOWN");
  }

  @Test
  @DisplayName("상세 재수집은 자기 공고 직무 코드로 직무군을 다시 매기고, 직무군이 같아도 스킬은 다시 뽑아 저장한다")
  void refreshesFamilyAndSkillsFromDetail() {
    JobPosting posting = mock(JobPosting.class);
    given(posting.getTitle()).willReturn("Java 백엔드 개발자");
    given(posting.getJobFamily()).willReturn("OTHER");
    given(jobPostingRepository.findBySourceAndExternalId("JOBKOREA", "1"))
        .willReturn(Optional.of(posting));
    String itPage =
        "<script>self.__next_f.push([1,\"\\\"jobClassificationCodes\\\":[\\\"4010000167\\\"]\"])</script>";

    assertThat(service.refreshJobKoreaFromDetail("1", itPage)).isTrue();
    verify(posting).classifyAs("DEVELOPER");

    given(posting.getJobFamily()).willReturn("DEVELOPER");
    assertThat(service.refreshJobKoreaFromDetail("1", itPage)).isFalse();

    // 직무군이 그대로여도 스킬은 두 번 다 다시 매기고 저장한다 — 정기 수집분을 백필과 같은 추출로 맞추는 게 목적이다.
    verify(posting, org.mockito.Mockito.times(2)).replaceSkills(any());
    verify(jobPostingRepository, org.mockito.Mockito.times(2)).save(posting);
  }
}
