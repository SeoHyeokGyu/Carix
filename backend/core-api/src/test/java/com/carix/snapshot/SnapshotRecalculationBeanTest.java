package com.carix.snapshot;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** A·B 레인이 주입받아 호출할 수 있는 상태인지 확인한다. 배선이 깨지면 3~4주차에 발견된다. */
@SpringBootTest
class SnapshotRecalculationBeanTest {

  @Autowired private SnapshotRecalculationService snapshotRecalculationService;

  @Test
  @DisplayName("스냅샷 재계산 서비스가 애플리케이션 컨텍스트에 빈으로 존재한다")
  void service_isAvailableAsBean() {
    assertThat(snapshotRecalculationService).isNotNull();
  }
}
