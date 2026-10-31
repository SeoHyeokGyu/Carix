package com.carix.jobposting.programmers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.carix.jobposting.JobPosting;
import com.carix.jobposting.JobPostingRepository;
import com.carix.jobposting.Skill;
import com.carix.jobposting.normalize.SkillTaxonomy;
import com.carix.jobposting.service.PostingSkillService;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ProgrammersPostingWriterTest {

  private JobPostingRepository postings;
  private PostingSkillService skills;
  private SkillTaxonomy taxonomy;
  private ProgrammersPostingWriter writer;

  @BeforeEach
  void setUp() {
    postings = mock(JobPostingRepository.class);
    skills = mock(PostingSkillService.class);
    taxonomy = mock(SkillTaxonomy.class);
    writer = new ProgrammersPostingWriter(postings, skills, taxonomy);
  }

  @Test
  @DisplayName("exists 는 해당 ID 의 공고 존재 여부를 반환한다")
  void testExists() {
    given(postings.findBySourceAndExternalId("PROGRAMMERS", "101")).willReturn(Optional.empty());
    given(postings.findBySourceAndExternalId("PROGRAMMERS", "102"))
        .willReturn(Optional.of(mock(JobPosting.class)));

    assertThat(writer.exists("101")).isFalse();
    assertThat(writer.exists("102")).isTrue();
  }

  @Test
  @DisplayName("신규 공고 등록 시 사전에 없는 스킬은 droppedOut 에 누적된다")
  void testDroppedSkills() {
    given(postings.findBySourceAndExternalId("PROGRAMMERS", "201")).willReturn(Optional.empty());
    given(taxonomy.matchKnownSkills("Java")).willReturn(List.of("Java"));
    given(taxonomy.matchKnownSkills("UnknownTech")).willReturn(List.of());
    given(skills.upsertKnownSkill("Java")).willReturn(new Skill("Java", "Java", false));

    ProgrammersPosition pos =
        new ProgrammersPosition(
            "201",
            "시니어 백엔드",
            "그렙",
            "서울 강남구",
            5,
            10,
            List.of("Java", "UnknownTech"),
            List.of("백엔드"),
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 12, 31),
            true);

    Map<String, Integer> dropped = new HashMap<>();
    boolean created = writer.upsertListed(pos, "202610", dropped);

    assertThat(created).isTrue();
    assertThat(dropped).containsEntry("UnknownTech", 1);

    ArgumentCaptor<JobPosting> captor = ArgumentCaptor.forClass(JobPosting.class);
    verify(postings).save(captor.capture());
    JobPosting saved = captor.getValue();
    assertThat(saved.getTitle()).isEqualTo("시니어 백엔드");
    assertThat(saved.getSourceJobTags()).isEqualTo("백엔드");
  }

  @Test
  @DisplayName("이미 존재하는 공고는 upsertListed 호출 시 생성하지 않고 갱신(touch) 후 false 를 반환한다")
  void testUpsertExistingReturnsFalse() {
    JobPosting existing = mock(JobPosting.class);
    given(postings.findBySourceAndExternalId("PROGRAMMERS", "301"))
        .willReturn(Optional.of(existing));

    ProgrammersPosition pos =
        new ProgrammersPosition(
            "301", "데이터 엔지니어", "그렙", "서울 강남구", 3, null, null, null, null, null, true);

    Map<String, Integer> dropped = new HashMap<>();
    boolean created = writer.upsertListed(pos, "202610", dropped);

    assertThat(created).isFalse();
    verify(existing).updateLastSeenMonth("202610");
    verify(postings).save(existing);
  }

  @Test
  @DisplayName("미등록 공고에 대해 touchListed 호출 시 false 를 반환한다")
  void testTouchListedNotFoundReturnsFalse() {
    given(postings.findBySourceAndExternalId("PROGRAMMERS", "999")).willReturn(Optional.empty());
    assertThat(writer.touchListed("999", "202610")).isFalse();
  }

  @Test
  @DisplayName("태그와 카테고리가 null 이어도 예외 없이 신규 저장된다")
  void testUpsertWithNullTagsAndCategories() {
    given(postings.findBySourceAndExternalId("PROGRAMMERS", "401")).willReturn(Optional.empty());

    ProgrammersPosition pos =
        new ProgrammersPosition(
            "401", "신입 개발자", "스타트업", "서울", null, null, null, null, null, null, true);

    Map<String, Integer> dropped = new HashMap<>();
    boolean created = writer.upsertListed(pos, "202610", dropped);

    assertThat(created).isTrue();
    ArgumentCaptor<JobPosting> captor = ArgumentCaptor.forClass(JobPosting.class);
    verify(postings).save(captor.capture());
    JobPosting saved = captor.getValue();
    assertThat(saved.getSourceJobTags()).isNull();
  }
}
