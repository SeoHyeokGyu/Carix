package com.carix.snapshot;

import com.carix.snapshot.repository.IndexSnapshotRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 스냅샷이 있는 사용자 전원을 최초 소득월부터 다시 계산한다. 규칙·산식·트리거를 바꾼 배포 뒤 저장된 행을 새 계산으로 맞출 때 쓴다 — 코드만 배포해서는 저장된 행이 바뀌지
 * 않는다.
 *
 * <p>사용자마다 {@link SnapshotRecalculationService#recalculateSnapshot} 의 트랜잭션이 따로 열리므로 한 사용자의 실패가 나머지를
 * 되돌리지 않는다. 다시 돌려도 새 데이터 버전이 발급될 뿐이라 반복 실행해도 된다.
 */
@Component
public class SnapshotBulkRecalculator {

  private static final Logger log = LoggerFactory.getLogger(SnapshotBulkRecalculator.class);

  private final IndexSnapshotRepository indexSnapshotRepository;
  private final SnapshotRangeResolver rangeResolver;
  private final SnapshotRecalculationService recalculationService;

  SnapshotBulkRecalculator(
      IndexSnapshotRepository indexSnapshotRepository,
      SnapshotRangeResolver rangeResolver,
      SnapshotRecalculationService recalculationService) {
    this.indexSnapshotRepository = indexSnapshotRepository;
    this.rangeResolver = rangeResolver;
    this.recalculationService = recalculationService;
  }

  public BulkRecalcResult recalculateAll() {
    var userIds = indexSnapshotRepository.findDistinctUserIds();
    int failed = 0;
    for (UUID userId : userIds) {
      try {
        recalculationService.recalculateSnapshot(userId, rangeResolver.earliestFrom(userId));
      } catch (RuntimeException e) {
        failed++;
        // 사용자 ID 는 남기지 않는다 — 예외 종류만으로 원인 분류는 된다.
        log.warn("스냅샷 일괄 재계산 중 한 사용자 실패: {}", e.getClass().getName());
      }
    }
    return new BulkRecalcResult(userIds.size(), failed);
  }

  public record BulkRecalcResult(int users, int failed) {}
}
