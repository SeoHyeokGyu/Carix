package com.carix.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.carix.snapshot.SnapshotBulkRecalculator.BulkRecalcResult;
import com.carix.snapshot.repository.IndexSnapshotRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SnapshotBulkRecalculatorTest {

  private final IndexSnapshotRepository snapshots = mock(IndexSnapshotRepository.class);
  private final SnapshotRangeResolver ranges = mock(SnapshotRangeResolver.class);
  private final SnapshotRecalculationService recalculation =
      mock(SnapshotRecalculationService.class);

  @Test
  @DisplayName("한 사용자의 재계산이 실패해도 나머지 사용자는 최초 소득월부터 다시 계산한다")
  void 실패한_사용자가_나머지를_막지_않는다() {
    UUID ok1 = UUID.randomUUID();
    UUID broken = UUID.randomUUID();
    UUID ok2 = UUID.randomUUID();
    given(snapshots.findDistinctUserIds()).willReturn(List.of(ok1, broken, ok2));
    given(ranges.earliestFrom(ok1)).willReturn("202303");
    given(ranges.earliestFrom(broken)).willReturn("202401");
    given(ranges.earliestFrom(ok2)).willReturn("202503");
    given(recalculation.recalculateSnapshot(broken, "202401"))
        .willThrow(new IllegalStateException("boom"));

    BulkRecalcResult result =
        new SnapshotBulkRecalculator(snapshots, ranges, recalculation).recalculateAll();

    assertThat(result).isEqualTo(new BulkRecalcResult(3, 1));
    verify(recalculation).recalculateSnapshot(ok1, "202303");
    verify(recalculation).recalculateSnapshot(ok2, "202503");
  }
}
