package com.carix.jobposting;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JobPostingTest {

  @Test
  @DisplayName("updateLastSeenMonth - 6자리 숫자 월인 경우에만 정상 갱신된다")
  void updateLastSeenMonth_validation() {
    JobPosting posting =
        JobPosting.builder()
            .source("JOBKOREA")
            .externalId("jk-1")
            .firstSeenMonth("202601")
            .lastSeenMonth("202601")
            .collectedAt(Instant.now())
            .build();

    // 정상 6자리
    posting.updateLastSeenMonth("202602");
    assertThat(posting.getLastSeenMonth()).isEqualTo("202602");

    // null 인 경우 기존값 유지
    posting.updateLastSeenMonth(null);
    assertThat(posting.getLastSeenMonth()).isEqualTo("202602");

    // 하이픈 포함 형식이나 유효하지 않은 길이는 무시
    posting.updateLastSeenMonth("2026-03");
    assertThat(posting.getLastSeenMonth()).isEqualTo("202602");

    posting.updateLastSeenMonth("invalid");
    assertThat(posting.getLastSeenMonth()).isEqualTo("202602");
  }

  @Test
  @DisplayName("addSkill - null 스킬은 무시되고 정상 스킬만 추가된다")
  void addSkill_nullHandling() {
    JobPosting posting = JobPosting.builder().build();

    posting.addSkill(null);
    assertThat(posting.getJobPostingSkills()).isEmpty();

    Skill java = Skill.builder().id(UUID.randomUUID()).standardName("Java").build();
    posting.addSkill(java);
    assertThat(posting.getJobPostingSkills()).hasSize(1);
    assertThat(posting.getSkills()).containsExactly(java);
  }

  @Test
  @DisplayName("replaceSkills - null 목록 및 null 원소 방어, 스킬 갱신 검증")
  void replaceSkills_comprehensive() {
    JobPosting posting = JobPosting.builder().build();
    Skill java = Skill.builder().id(UUID.randomUUID()).standardName("Java").build();
    Skill python = Skill.builder().id(UUID.randomUUID()).standardName("Python").build();
    posting.addSkill(java);

    // 1. null 전달 시 기존 스킬 모두 제거
    posting.replaceSkills(null);
    assertThat(posting.getSkills()).isEmpty();

    // 2. null 원소 및 ID 가 null 인 스킬 필터링
    List<Skill> dirtyList = new ArrayList<>();
    dirtyList.add(null);
    dirtyList.add(Skill.builder().id(null).standardName("NullId").build());
    dirtyList.add(python);

    posting.replaceSkills(dirtyList);
    assertThat(posting.getSkills()).containsExactly(python);

    // 3. 기존 스킬 중 일부 유지 및 새 스킬 추가
    posting.replaceSkills(List.of(python, java));
    assertThat(posting.getSkills()).containsExactlyInAnyOrder(python, java);
  }

  @Test
  @DisplayName("도메인 분류 메서드 검증 (고용형태, 기업규모, 중복제거 그룹)")
  void domainClassifications() {
    JobPosting posting = JobPosting.builder().build();

    posting.classifyEmploymentTypeAs("FULL_TIME");
    assertThat(posting.getEmploymentType()).isEqualTo("FULL_TIME");

    posting.classifyCompanySizeAs("LARGE");
    assertThat(posting.getCompanySize()).isEqualTo("LARGE");

    UUID groupId = UUID.randomUUID();
    posting.assignDedupGroup(groupId, "v1");
    assertThat(posting.getDedupGroupId()).isEqualTo(groupId);
    assertThat(posting.getDedupRuleVersion()).isEqualTo("v1");
  }
}
