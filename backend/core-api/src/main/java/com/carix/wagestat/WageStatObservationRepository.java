package com.carix.wagestat;

import java.util.List;
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
   * 주어진 직무, 경력 브래킷, 지역에 대해 가장 최신 통계 개정판의 임금통계를 조회합니다.
   *
   * <p>출처 우선순위(KOSIS 실측 > UNKNOWN 이전 적재 > FALLBACK 상수)를 최우선으로 적용하고, 최신 연도({@code basePeriod DESC})
   * 및 최신 개정판({@code statVintage DESC}) 순으로 정렬합니다.
   *
   * <p>NOTE: (cjkang) collector {@code DataExploreService.ENGINE_BASELINE_ORDER} 가 관리자 화면의 「엔진
   * 기준선」을 이 순서로 재현한다. 순서를 바꾸면 함께 고친다.
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
      ORDER BY CASE WHEN w.source = com.carix.wagestat.WageStatSource.KOSIS THEN 1
                    WHEN w.source = com.carix.wagestat.WageStatSource.UNKNOWN THEN 2
                    ELSE 3 END ASC,
        CASE WHEN w.wageDefinition = 'ANNUAL_GROSS_MEDIAN' THEN 1 ELSE 2 END ASC,
        w.basePeriod DESC,
        w.statVintage DESC
      LIMIT 1
      """)
  Optional<WageStatObservation> findLatestBaseline(
      @Param("jobFamily") String jobFamily,
      @Param("experienceBracket") String experienceBracket,
      @Param("region") String region);

  /**
   * 주어진 직무, 경력 브래킷, 지역 및 특정 기준 연도(basePeriod)에 대해 임금통계를 조회합니다 (b14 경력 사다리 연도 축).
   *
   * @param jobFamily 직무 카테고리
   * @param experienceBracket 경력 구간 브래킷
   * @param region 지역명
   * @param basePeriod 기준 연도
   * @return 해당 연도의 임금통계 관측치
   */
  @Query(
      """
      SELECT w FROM WageStatObservation w
      WHERE w.jobFamily = :jobFamily
        AND w.experienceBracket = :experienceBracket
        AND w.region = :region
        AND w.basePeriod = :basePeriod
      ORDER BY CASE WHEN w.source = com.carix.wagestat.WageStatSource.KOSIS THEN 1
                    WHEN w.source = com.carix.wagestat.WageStatSource.UNKNOWN THEN 2
                    ELSE 3 END ASC,
        CASE WHEN w.wageDefinition = 'ANNUAL_GROSS_MEDIAN' THEN 1 ELSE 2 END ASC,
        w.statVintage DESC
      LIMIT 1
      """)
  Optional<WageStatObservation> findBaselineByYear(
      @Param("jobFamily") String jobFamily,
      @Param("experienceBracket") String experienceBracket,
      @Param("region") String region,
      @Param("basePeriod") String basePeriod);

  /**
   * 주어진 직무에 대해 등록된 모든 기준 연도(basePeriod) 목록을 오름차순으로 조회합니다 (b14 경력 사다리 연도 축).
   *
   * @param jobFamily 직무 카테고리
   * @return 기준 연도 목록
   */
  @Query(
      """
      SELECT DISTINCT w.basePeriod FROM WageStatObservation w
      WHERE w.jobFamily = :jobFamily
        AND w.basePeriod IS NOT NULL
      ORDER BY w.basePeriod ASC
      """)
  List<String> findDistinctBasePeriodsByJobFamily(@Param("jobFamily") String jobFamily);

  /**
   * 주어진 직무, 경력 브래킷, 지역에 대해 연도별 시계열 임금통계를 조회합니다.
   *
   * <p>연도({@code basePeriod}) 오름차순, 동일 연도 내에서는 출처 우선순위(KOSIS > UNKNOWN > FALLBACK) 및 최신 vintage 순으로
   * 정렬합니다.
   *
   * @param jobFamily 직무 카테고리
   * @param experienceBracket 경력 구간 브래킷
   * @param region 지역명
   * @param wageDefinition 임금 정의 문구 (nullable)
   * @return 시계열 임금통계 관측치 목록
   */
  @Query(
      """
      SELECT w FROM WageStatObservation w
      WHERE w.jobFamily = :jobFamily
        AND w.experienceBracket = :experienceBracket
        AND w.region = :region
        AND (:wageDefinition IS NULL OR w.wageDefinition = :wageDefinition)
      ORDER BY w.basePeriod ASC,
        CASE WHEN w.source = com.carix.wagestat.WageStatSource.KOSIS THEN 1
             WHEN w.source = com.carix.wagestat.WageStatSource.UNKNOWN THEN 2
             ELSE 3 END ASC,
        w.statVintage DESC
      """)
  List<WageStatObservation> findSeriesObservations(
      @Param("jobFamily") String jobFamily,
      @Param("experienceBracket") String experienceBracket,
      @Param("region") String region,
      @Param("wageDefinition") String wageDefinition);

  default List<WageStatObservation> findSeriesObservations(
      String jobFamily, String experienceBracket, String region) {
    return findSeriesObservations(jobFamily, experienceBracket, region, "ANNUAL_GROSS_MEDIAN");
  }
}
