package com.carix.datasource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

class DataSourceConfigTest {

  private final DataSourceConfig config = new DataSourceConfig();

  @Test
  @DisplayName("writeHikariConfig 기본 설정이 CarixWritePool로 올바르게 초기화된다")
  void verifyWriteHikariConfigDefaults() {
    HikariConfig hikariConfig = config.writeHikariConfig();
    assertThat(hikariConfig.getPoolName()).isEqualTo("CarixWritePool");
    assertThat(hikariConfig.getMaximumPoolSize()).isEqualTo(15);
    assertThat(hikariConfig.getMinimumIdle()).isEqualTo(3);
    assertThat(hikariConfig.isReadOnly()).isFalse();
  }

  @Test
  @DisplayName("readOnlyHikariConfig 기본 설정이 CarixReadOnlyPool로 올바르게 초기화된다")
  void verifyReadOnlyHikariConfigDefaults() {
    HikariConfig hikariConfig = config.readOnlyHikariConfig();
    assertThat(hikariConfig.getPoolName()).isEqualTo("CarixReadOnlyPool");
    assertThat(hikariConfig.getMaximumPoolSize()).isEqualTo(25);
    assertThat(hikariConfig.getMinimumIdle()).isEqualTo(5);
    assertThat(hikariConfig.isReadOnly()).isTrue();
  }

  @Test
  @DisplayName("writeDataSource 및 readOnlyDataSource 생성 시 DataSourceProperties의 접속 정보가 올바르게 주입된다")
  void verifyDataSourceCreationWithDriver() {
    DataSourceProperties properties = new DataSourceProperties();
    properties.setUrl("jdbc:h2:mem:test_valid;DB_CLOSE_DELAY=-1");
    properties.setUsername("sa_valid");
    properties.setPassword("secret");
    properties.setDriverClassName("org.h2.Driver");

    HikariConfig writeHikari = new HikariConfig();
    writeHikari.setPoolName("TestValidWritePool");
    DataSource writeDs = config.writeDataSource(properties, writeHikari);

    HikariConfig roHikari = new HikariConfig();
    roHikari.setPoolName("TestValidRoPool");
    DataSource roDs = config.readOnlyDataSource(properties, roHikari);

    try {
      assertThat(writeDs).isInstanceOf(HikariDataSource.class);
      HikariDataSource writeHds = (HikariDataSource) writeDs;
      assertThat(writeHds.getJdbcUrl()).isEqualTo("jdbc:h2:mem:test_valid;DB_CLOSE_DELAY=-1");
      assertThat(writeHds.getUsername()).isEqualTo("sa_valid");
      assertThat(writeHds.getDriverClassName()).isEqualTo("org.h2.Driver");

      assertThat(roDs).isInstanceOf(HikariDataSource.class);
      HikariDataSource roHds = (HikariDataSource) roDs;
      assertThat(roHds.getJdbcUrl()).isEqualTo("jdbc:h2:mem:test_valid;DB_CLOSE_DELAY=-1");
      assertThat(roHds.getUsername()).isEqualTo("sa_valid");
      assertThat(roHds.getDriverClassName()).isEqualTo("org.h2.Driver");
    } finally {
      if (writeDs instanceof HikariDataSource hds) {
        hds.close();
      }
      if (roDs instanceof HikariDataSource hds) {
        hds.close();
      }
    }
  }

  @Test
  @DisplayName("driverClassName이 null이어도 writeDataSource 및 readOnlyDataSource가 정상 생성된다")
  void verifyDataSourcesWithNullDriver() {
    DataSourceProperties properties = mock(DataSourceProperties.class);
    org.mockito.Mockito.when(properties.determineUrl())
        .thenReturn("jdbc:h2:mem:test_null;DB_CLOSE_DELAY=-1");
    org.mockito.Mockito.when(properties.determineUsername()).thenReturn("sa_null");
    org.mockito.Mockito.when(properties.determinePassword()).thenReturn("");
    org.mockito.Mockito.when(properties.determineDriverClassName()).thenReturn(null);

    HikariConfig writeHikari = new HikariConfig();
    writeHikari.setPoolName("TestNullWritePool");
    DataSource writeDs = config.writeDataSource(properties, writeHikari);

    HikariConfig roHikari = new HikariConfig();
    roHikari.setPoolName("TestNullRoPool");
    DataSource roDs = config.readOnlyDataSource(properties, roHikari);

    try {
      assertThat(writeDs).isInstanceOf(HikariDataSource.class);
      assertThat(((HikariDataSource) writeDs).getJdbcUrl())
          .isEqualTo("jdbc:h2:mem:test_null;DB_CLOSE_DELAY=-1");

      assertThat(roDs).isInstanceOf(HikariDataSource.class);
      assertThat(((HikariDataSource) roDs).getJdbcUrl())
          .isEqualTo("jdbc:h2:mem:test_null;DB_CLOSE_DELAY=-1");
    } finally {
      if (writeDs instanceof HikariDataSource hds) {
        hds.close();
      }
      if (roDs instanceof HikariDataSource hds) {
        hds.close();
      }
    }
  }

  @Test
  @DisplayName("driverClassName이 빈 문자열이나 공백이어도 정상 동작한다")
  void verifyDataSourceWithBlankDriver() {
    DataSourceProperties properties = mock(DataSourceProperties.class);
    org.mockito.Mockito.when(properties.determineUrl())
        .thenReturn("jdbc:h2:mem:test_blank;DB_CLOSE_DELAY=-1");
    org.mockito.Mockito.when(properties.determineUsername()).thenReturn("sa_blank");
    org.mockito.Mockito.when(properties.determinePassword()).thenReturn("");
    org.mockito.Mockito.when(properties.determineDriverClassName()).thenReturn("   ");

    HikariConfig writeHikari = new HikariConfig();
    writeHikari.setPoolName("TestBlankWritePool");
    DataSource writeDs = config.writeDataSource(properties, writeHikari);

    HikariConfig roHikari = new HikariConfig();
    roHikari.setPoolName("TestBlankRoPool");
    DataSource roDs = config.readOnlyDataSource(properties, roHikari);

    try {
      assertThat(writeDs).isInstanceOf(HikariDataSource.class);
      assertThat(roDs).isInstanceOf(HikariDataSource.class);
    } finally {
      if (writeDs instanceof HikariDataSource hds) {
        hds.close();
      }
      if (roDs instanceof HikariDataSource hds) {
        hds.close();
      }
    }
  }

  @Test
  @DisplayName("routingDataSource 및 primary dataSource 빈 생성이 정상 동작한다")
  void verifyRoutingAndPrimaryDataSourceBeans() {
    DataSource writeDs = mock(DataSource.class);
    DataSource readOnlyDs = mock(DataSource.class);

    RoutingDataSource routingDs = config.routingDataSource(writeDs, readOnlyDs);
    assertThat(routingDs).isNotNull();

    DataSource primaryDs = config.dataSource(routingDs);
    assertThat(primaryDs).isInstanceOf(LazyConnectionDataSourceProxy.class);
    LazyConnectionDataSourceProxy proxy = (LazyConnectionDataSourceProxy) primaryDs;
    assertThat(proxy.getTargetDataSource()).isEqualTo(routingDs);
  }
}
