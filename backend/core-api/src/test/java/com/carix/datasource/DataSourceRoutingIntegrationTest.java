package com.carix.datasource;

import static org.assertj.core.api.Assertions.assertThat;

import com.carix.index.TopPriceDriverProvider;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(DataSourceRoutingIntegrationTest.AnnotatedTestService.class)
class DataSourceRoutingIntegrationTest {

  @TestComponent
  static class AnnotatedTestService {
    @Autowired private RoutingDataSource routingDataSource;

    @ReadOnlyRouting
    public DataSourceType lookupWithAnnotation() {
      return (DataSourceType) routingDataSource.determineCurrentLookupKey();
    }
  }

  @Autowired private DataSource dataSource;

  @Autowired
  @Qualifier("writeDataSource")
  private DataSource writeDataSource;

  @Autowired
  @Qualifier("readOnlyDataSource")
  private DataSource readOnlyDataSource;

  @Autowired private RoutingDataSource routingDataSource;

  @Autowired private TransactionTemplate transactionTemplate;

  @Autowired private TopPriceDriverProvider topPriceDriverProvider;

  @Autowired private AnnotatedTestService annotatedTestService;

  @Test
  @DisplayName("쓰기 풀과 읽기 전용 풀이 각각 올바른 풀 이름과 설정으로 초기화된다")
  void verifyPoolProperties() {
    assertThat(writeDataSource).isInstanceOf(HikariDataSource.class);
    HikariDataSource writeHikari = (HikariDataSource) writeDataSource;
    assertThat(writeHikari.getPoolName()).isEqualTo("CarixWritePool");
    assertThat(writeHikari.isReadOnly()).isFalse();

    assertThat(readOnlyDataSource).isInstanceOf(HikariDataSource.class);
    HikariDataSource readOnlyHikari = (HikariDataSource) readOnlyDataSource;
    assertThat(readOnlyHikari.getPoolName()).isEqualTo("CarixReadOnlyPool");
    assertThat(readOnlyHikari.isReadOnly()).isTrue();
  }

  @Test
  @DisplayName("ReadOnlyRoutingContext 설정 시 READ_ONLY 데이터소스로 라우팅된다")
  void routesToReadOnlyWhenContextIsSet() {
    ReadOnlyRoutingContext.setReadOnly(true);
    try {
      Object key = routingDataSource.determineCurrentLookupKey();
      assertThat(key).isEqualTo(DataSourceType.READ_ONLY);
    } finally {
      ReadOnlyRoutingContext.clear();
    }
  }

  @Test
  @DisplayName("쓰기 트랜잭션에서는 WRITE 데이터소스로 라우팅된다")
  void routesToWriteInWriteTransaction() {
    transactionTemplate.execute(
        status -> {
          Object key = routingDataSource.determineCurrentLookupKey();
          assertThat(key).isEqualTo(DataSourceType.WRITE);
          return null;
        });
  }

  @Test
  @DisplayName("읽기 전용 트랜잭션에서는 READ_ONLY 데이터소스로 라우팅된다")
  @Transactional(readOnly = true)
  void routesToReadOnlyInReadOnlyTransaction() {
    Object key = routingDataSource.determineCurrentLookupKey();
    assertThat(key).isEqualTo(DataSourceType.READ_ONLY);
  }

  @Test
  @DisplayName("가상 스레드에서 withReadOnlyCallable 실행 시 READ_ONLY 데이터소스로 라우팅된다")
  void routesToReadOnlyInVirtualThreadWithContext() throws Exception {
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      Callable<Object> task =
          ReadOnlyRoutingContext.withReadOnlyCallable(
              () -> routingDataSource.determineCurrentLookupKey());
      Future<Object> future = executor.submit(task);
      assertThat(future.get()).isEqualTo(DataSourceType.READ_ONLY);
    }
  }

  @Test
  @DisplayName("실제 연결 획득 시 읽기 전용 컨텍스트에서는 CarixReadOnlyPool 커넥션을 빌린다")
  void obtainsConnectionFromReadOnlyPool() throws Exception {
    HikariDataSource readOnlyHikari = (HikariDataSource) readOnlyDataSource;
    HikariDataSource writeHikari = (HikariDataSource) writeDataSource;

    int writeBefore = writeHikari.getHikariPoolMXBean().getActiveConnections();
    int readBefore = readOnlyHikari.getHikariPoolMXBean().getActiveConnections();

    ReadOnlyRoutingContext.setReadOnly(true);
    try (Connection conn = routingDataSource.getConnection()) {
      assertThat(conn).isNotNull();
      int writeActive = writeHikari.getHikariPoolMXBean().getActiveConnections();
      int readActive = readOnlyHikari.getHikariPoolMXBean().getActiveConnections();

      assertThat(writeActive).isEqualTo(writeBefore);
      assertThat(readActive).isEqualTo(readBefore + 1);
    } finally {
      ReadOnlyRoutingContext.clear();
    }
  }

  @Test
  @DisplayName("쓰기 트랜잭션 중 가상 스레드 팬아웃 시 부모는 CarixWritePool, 가상 스레드는 CarixReadOnlyPool을 사용한다")
  void isolationBetweenWriteAndReadOnlyPools() {
    HikariDataSource readOnlyHikari = (HikariDataSource) readOnlyDataSource;
    HikariDataSource writeHikari = (HikariDataSource) writeDataSource;

    transactionTemplate.execute(
        status -> {
          // 부모 트랜잭션에서 커넥션 획득 (쓰기 풀)
          try (Connection writeConn = routingDataSource.getConnection()) {
            assertThat(writeConn).isNotNull();
            assertThat(routingDataSource.determineCurrentLookupKey())
                .isEqualTo(DataSourceType.WRITE);

            // 가상 스레드 팬아웃 시뮬레이션
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
              Future<DataSourceType> fanoutFuture =
                  executor.submit(
                      ReadOnlyRoutingContext.withReadOnlyCallable(
                          () -> {
                            DataSourceType key =
                                (DataSourceType) routingDataSource.determineCurrentLookupKey();
                            try (Connection readConn = routingDataSource.getConnection()) {
                              assertThat(readConn).isNotNull();
                            }
                            return key;
                          }));

              assertThat(fanoutFuture.get()).isEqualTo(DataSourceType.READ_ONLY);
            }
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
          return null;
        });
  }

  @Test
  @DisplayName("@ReadOnlyRouting 선언 메서드는 CarixReadOnlyPool로 라우팅된다")
  void testAnnotatedServiceRoutesToReadOnly() {
    assertThat(annotatedTestService.lookupWithAnnotation()).isEqualTo(DataSourceType.READ_ONLY);
  }

  @Test
  @DisplayName("쓰기 트랜잭션 중 DefaultTopPriceDriverProvider.find 호출 시 @ReadOnlyRouting에 의해 정상 격리 실행된다")
  void topPriceDriverProviderRoutesToReadOnlyPool() {
    transactionTemplate.execute(
        status -> {
          try (Connection conn = writeDataSource.getConnection()) {
            assertThat(conn).isNotNull();
            topPriceDriverProvider.find(UUID.randomUUID(), "202607");
            return null;
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }
}
