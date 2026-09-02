package com.carix.index;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carix.index.dto.ExcludedSkill;
import com.carix.index.dto.MviResult;
import com.carix.index.dto.PpiResult;
import com.carix.index.dto.SourceCoverage;
import com.carix.index.enums.IndexStatus;
import com.carix.index.enums.SkillExclusionReason;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IndexResultTest {

  @Test
  void 계산_불가는_값을_null_로_두고_0이나_100으로_채우지_않는다() {
    assertThat(PpiResult.notAvailable("202608").value()).isNull();
    assertThat(MviResult.notAvailable("202608").value()).isNull();
  }

  @Test
  void 계산_불가여도_대상_월은_유지한다() {
    assertThat(PpiResult.notAvailable("202608").baseYearMonth()).isEqualTo("202608");
    assertThat(MviResult.notAvailable("202608").status()).isEqualTo(IndexStatus.NOT_AVAILABLE);
  }

  @Test
  void 원천별_커버리지는_목록이다_완화가_원천마다_독립이기_때문이다() {
    MviResult result =
        new MviResult(
            "202608",
            null,
            IndexStatus.INSUFFICIENT_SAMPLE,
            List.of(
                new SourceCoverage("JOBKOREA", 1, 42, "서울 강남구 / MID / 정규직", null),
                new SourceCoverage("SARAMIN", 5, 117, "서울 전체 / MID / 회사규모 무관", null)),
            List.of(),
            null,
            null);

    assertThat(result.sources()).extracting(SourceCoverage::fallbackLevel).containsExactly(1, 5);
  }

  @Test
  void 목록은_null_을_받아도_빈_목록이_된다() {
    MviResult result =
        new MviResult("202608", null, IndexStatus.NOT_AVAILABLE, null, null, null, null);

    assertThat(result.sources()).isEmpty();
    assertThat(result.excludedSkills()).isEmpty();
  }

  @Test
  void 목록은_방어적으로_복사돼_바깥에서_바뀌지_않는다() {
    List<ExcludedSkill> mutable = new ArrayList<>();
    mutable.add(
        new ExcludedSkill(
            UUID.randomUUID(), "CustomSkillX", SkillExclusionReason.CUSTOM_SKILL_NO_STATS));

    MviResult result =
        new MviResult("202608", null, IndexStatus.NOT_AVAILABLE, List.of(), mutable, null, null);
    mutable.clear();

    assertThat(result.excludedSkills()).hasSize(1);
    assertThatThrownBy(() -> result.excludedSkills().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
