package com.carix.datasource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

/**
 * 쓰기 전용 커넥션 풀(CarixWritePool)과 읽기 전용 전용 풀(CarixReadOnlyPool)을 분리 구성한다.
 *
 * <p>가상 스레드 기반 팬아웃 조회가 메인 트랜잭션의 커넥션 풀을 고갈시키지 않도록 격리하여, {@code pg_advisory_xact_lock} 점유 상태에서 발생하는
 * 커넥션 타임아웃/데드락 절벽을 원천 방지한다 (b17).
 */
@Configuration
public class DataSourceConfig {

  @Bean
  @Primary
  @ConfigurationProperties("spring.datasource")
  public DataSourceProperties dataSourceProperties() {
    return new DataSourceProperties();
  }

  @Bean(name = "writeHikariConfig")
  @ConfigurationProperties("spring.datasource.write.hikari")
  public HikariConfig writeHikariConfig() {
    HikariConfig config = new HikariConfig();
    config.setPoolName("CarixWritePool");
    config.setMaximumPoolSize(15);
    config.setMinimumIdle(3);
    return config;
  }

  @Bean(name = "readOnlyHikariConfig")
  @ConfigurationProperties("spring.datasource.readonly.hikari")
  public HikariConfig readOnlyHikariConfig() {
    HikariConfig config = new HikariConfig();
    config.setPoolName("CarixReadOnlyPool");
    config.setReadOnly(true);
    config.setMaximumPoolSize(25);
    config.setMinimumIdle(5);
    return config;
  }

  @Bean(name = "writeDataSource")
  public DataSource writeDataSource(
      DataSourceProperties properties, HikariConfig writeHikariConfig) {
    writeHikariConfig.setJdbcUrl(properties.determineUrl());
    writeHikariConfig.setUsername(properties.determineUsername());
    writeHikariConfig.setPassword(properties.determinePassword());
    String driverClassName = properties.determineDriverClassName();
    if (driverClassName != null && !driverClassName.isBlank()) {
      writeHikariConfig.setDriverClassName(driverClassName);
    }
    return new HikariDataSource(writeHikariConfig);
  }

  @Bean(name = "readOnlyDataSource")
  public DataSource readOnlyDataSource(
      DataSourceProperties properties, HikariConfig readOnlyHikariConfig) {
    readOnlyHikariConfig.setJdbcUrl(properties.determineUrl());
    readOnlyHikariConfig.setUsername(properties.determineUsername());
    readOnlyHikariConfig.setPassword(properties.determinePassword());
    String driverClassName = properties.determineDriverClassName();
    if (driverClassName != null && !driverClassName.isBlank()) {
      readOnlyHikariConfig.setDriverClassName(driverClassName);
    }
    return new HikariDataSource(readOnlyHikariConfig);
  }

  @Bean
  public RoutingDataSource routingDataSource(
      @Qualifier("writeDataSource") DataSource writeDataSource,
      @Qualifier("readOnlyDataSource") DataSource readOnlyDataSource) {
    RoutingDataSource routingDataSource = new RoutingDataSource();
    Map<Object, Object> targetDataSources = new HashMap<>();
    targetDataSources.put(DataSourceType.WRITE, writeDataSource);
    targetDataSources.put(DataSourceType.READ_ONLY, readOnlyDataSource);
    routingDataSource.setTargetDataSources(targetDataSources);
    routingDataSource.setDefaultTargetDataSource(writeDataSource);
    return routingDataSource;
  }

  @Bean
  @Primary
  public DataSource dataSource(RoutingDataSource routingDataSource) {
    return new LazyConnectionDataSourceProxy(routingDataSource);
  }
}
