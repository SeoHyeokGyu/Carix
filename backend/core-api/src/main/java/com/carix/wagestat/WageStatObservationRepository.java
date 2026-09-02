package com.carix.wagestat;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** 공식 임금통계 기준선(core.wage_stat_observation) 조회 리포지토리. */
@Repository
public interface WageStatObservationRepository extends JpaRepository<WageStatObservation, UUID> {

  /**
   * 주어진 직무, 경력 브래킷, 지역에 대해 가장 최신 통계 개정판(stat_vintage DESC)의 임금통계를 조회합니다.
   *
   * @param jobFamily 직무 카테고리
   * @param experienceBracket 경력 구간 브래킷
   * @param region 지역명
   * @return 최신 임금통계 관측치
   */
  @Query(
      """
      SELECT w FROM WageStatObservation w
      WHERE w.jobFamily = :jobFamily
        AND w.experienceBracket = :experienceBracket
        AND w.region = :region
      ORDER BY w.basePeriod DESC, w.statVintage DESC
      LIMIT 1
      """)
  Optional<WageStatObservation> findLatestBaseline(
      @Param("jobFamily") String jobFamily,
      @Param("experienceBracket") String experienceBracket,
      @Param("region") String region);
}
