package com.carix.jobposting;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
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
  @DisplayName("replaceSkills - 같은 id 의 스킬이 두 번 들어와도 연결은 한 번만 만든다")
  void replaceSkills_dedupesById() {
    JobPosting posting = JobPosting.builder().build();
    UUID id = UUID.randomUUID();
    Skill first = Skill.builder().id(id).standardName("보안").build();
    Skill second = Skill.builder().id(id).standardName("보안").build();

    posting.replaceSkills(List.of(first, second));

    assertThat(posting.getSkills()).hasSize(1);
  }
}
