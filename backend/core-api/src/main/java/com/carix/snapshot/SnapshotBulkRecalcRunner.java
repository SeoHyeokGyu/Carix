package com.carix.snapshot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * {@code carix.snapshot.recalculate-all=true} 로 기동할 때만 한 번 돈다. core-api 에는 관리자 경로가 없어 새 권한 체계를 만드는
 * 대신 운영자가 일회성 기동 옵션으로 실행한다. 운영 실행은 매번 사람이 판단한다 — 기본값은 꺼짐이다.
 */
@Component
@ConditionalOnProperty(name = "carix.snapshot.recalculate-all", havingValue = "true")
class SnapshotBulkRecalcRunner implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(SnapshotBulkRecalcRunner.class);

  private final SnapshotBulkRecalculator recalculator;

  SnapshotBulkRecalcRunner(SnapshotBulkRecalculator recalculator) {
    this.recalculator = recalculator;
  }

  @Override
  public void run(ApplicationArguments args) {
    var result = recalculator.recalculateAll();
    log.info("스냅샷 일괄 재계산 완료: 사용자 {}명, 실패 {}명", result.users(), result.failed());
  }
}
