package com.carix.datasource;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 트랜잭션의 readOnly 속성 또는 {@link ReadOnlyRoutingContext}에 따라 쓰기용/읽기용 커넥션 풀을 분기한다. */
public class RoutingDataSource extends AbstractRoutingDataSource {

  @Override
  public Object determineCurrentLookupKey() {
    if (ReadOnlyRoutingContext.isReadOnly()
        || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
      return DataSourceType.READ_ONLY;
    }
    return DataSourceType.WRITE;
  }
}
