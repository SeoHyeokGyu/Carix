package com.carix.market;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** 사전 집계 시장 데이터(core.market_aggregate) 조회 리포지토리. */
@Repository
public interface MarketAggregateRepository extends JpaRepository<MarketAggregate, UUID> {

  /**
   * 동료집단 전체 행(skillId IS NULL)을 완화 조건에 따라 단건 조회합니다.
   *
   * @param source 플랫폼 원천 (JOBKOREA, SARAMIN)
   * @param baseYearMonth 기준년월 (YYYYMM)
   * @param jobFamily 직무
   * @param region 지역명
   * @param experienceBracket 경력 브래킷 (JUNIOR, MID, SENIOR)
   * @param employmentType 고용형태 (NULL 허용 - Level 5)
   * @param companySize 기업규모 (NULL 허용 - Level 2)
   * @return 조건에 일치하는 집계 전체 행 (Optional)
   */
  @Query(
      """
      SELECT m FROM MarketAggregate m
      WHERE m.source = :source
        AND m.baseYearMonth = :baseYearMonth
        AND m.jobFamily = :jobFamily
        AND (m.region = :region OR m.region = 'ALL')
        AND m.experienceBracket = :experienceBracket
        AND ((:employmentType IS NULL AND m.employmentType IS NULL) OR m.employmentType = :employmentType OR m.employmentType = 'ALL')
        AND ((:companySize IS NULL AND m.companySize IS NULL) OR m.companySize = :companySize OR m.companySize = 'ALL')
        AND m.skillId IS NULL
      """)
  Optional<MarketAggregate> findPeerGroupAggregate(
      @Param("source") String source,
      @Param("baseYearMonth") String baseYearMonth,
      @Param("jobFamily") String jobFamily,
      @Param("region") String region,
      @Param("experienceBracket") String experienceBracket,
      @Param("employmentType") String employmentType,
      @Param("companySize") String companySize);

  /** 동료집단 전체 행(skillId IS NULL)을 경력 브래킷 복수 조건(Level 4: 인접 브래킷 확장)으로 조회합니다. */
  @Query(
      """
      SELECT m FROM MarketAggregate m
      WHERE m.source = :source
        AND m.baseYearMonth = :baseYearMonth
        AND m.jobFamily = :jobFamily
        AND (m.region = :region OR m.region = 'ALL')
        AND m.experienceBracket IN :brackets
        AND ((:employmentType IS NULL AND m.employmentType IS NULL) OR m.employmentType = :employmentType OR m.employmentType = 'ALL')
        AND ((:companySize IS NULL AND m.companySize IS NULL) OR m.companySize = :companySize OR m.companySize = 'ALL')
        AND m.skillId IS NULL
      """)
  List<MarketAggregate> findPeerGroupAggregatesByBrackets(
      @Param("source") String source,
      @Param("baseYearMonth") String baseYearMonth,
      @Param("jobFamily") String jobFamily,
      @Param("region") String region,
      @Param("brackets") Collection<String> brackets,
      @Param("employmentType") String employmentType,
      @Param("companySize") String companySize);

  /** 특정 스킬의 집계 행을 특정 기간 목록(months) 및 동료집단 조건에 맞춰 조회합니다. */
  @Query(
      """
      SELECT m FROM MarketAggregate m
      WHERE m.source = :source
        AND m.baseYearMonth IN :months
        AND m.jobFamily = :jobFamily
        AND (m.region = :region OR m.region = 'ALL')
        AND m.experienceBracket IN :brackets
        AND ((:employmentType IS NULL AND m.employmentType IS NULL) OR m.employmentType = :employmentType OR m.employmentType = 'ALL')
        AND ((:companySize IS NULL AND m.companySize IS NULL) OR m.companySize = :companySize OR m.companySize = 'ALL')
        AND m.skillId = :skillId
      """)
  List<MarketAggregate> findSkillAggregates(
      @Param("source") String source,
      @Param("months") Collection<String> months,
      @Param("jobFamily") String jobFamily,
      @Param("region") String region,
      @Param("brackets") Collection<String> brackets,
      @Param("employmentType") String employmentType,
      @Param("companySize") String companySize,
      @Param("skillId") UUID skillId);

  /**
   * 특정 기간(months) 및 동료집단 조건에 대한 전체 공고 기준 행(skillId IS NULL)들을 조회합니다. 스킬 수요율 계산 시 전체 공고 수 합계를 구할 때
   * 사용됩니다.
   */
  @Query(
      """
      SELECT m FROM MarketAggregate m
      WHERE m.source = :source
        AND m.baseYearMonth IN :months
        AND m.jobFamily = :jobFamily
        AND (m.region = :region OR m.region = 'ALL')
        AND m.experienceBracket IN :brackets
        AND ((:employmentType IS NULL AND m.employmentType IS NULL) OR m.employmentType = :employmentType OR m.employmentType = 'ALL')
        AND ((:companySize IS NULL AND m.companySize IS NULL) OR m.companySize = :companySize OR m.companySize = 'ALL')
        AND m.skillId IS NULL
      """)
  List<MarketAggregate> findTotalPostingAggregates(
      @Param("source") String source,
      @Param("months") Collection<String> months,
      @Param("jobFamily") String jobFamily,
      @Param("region") String region,
      @Param("brackets") Collection<String> brackets,
      @Param("employmentType") String employmentType,
      @Param("companySize") String companySize);
}
