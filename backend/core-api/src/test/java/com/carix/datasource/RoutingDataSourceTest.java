package com.carix.datasource;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class RoutingDataSourceTest {

  private final RoutingDataSource routingDataSource = new RoutingDataSource();

  @AfterEach
  void tearDown() {
    ReadOnlyRoutingContext.clear();
    TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
  }

  @Test
  @DisplayName("트랜잭션이 readOnly이면 READ_ONLY를 반환한다")
  void determineLookupKeyWithReadOnlyTx() {
    TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
    try {
      assertThat(routingDataSource.determineCurrentLookupKey()).isEqualTo(DataSourceType.READ_ONLY);
    } finally {
      TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
    }
  }

  @Test
  @DisplayName("읽기 전용 컨텍스트도 아니고 읽기 전용 트랜잭션도 아니면 WRITE를 반환한다")
  void determineLookupKeyDefaultWrite() {
    assertThat(routingDataSource.determineCurrentLookupKey()).isEqualTo(DataSourceType.WRITE);
  }
}
