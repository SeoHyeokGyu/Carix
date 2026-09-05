package com.carix.jobposting;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobPostingRepository extends JpaRepository<JobPosting, UUID> {

  /**
   * 동료집단 조건에 걸리는 원 공고를 모은다. 완화 단계(Level 1~5)는 인자를 {@code null} 로 넘겨 표현한다 — 조건을 해제한 차원은 필터에서 빠진다.
   *
   * <p>경력은 구간 코드가 아니라 공고의 연 단위 범위와 겹치는지로 본다. {@code experience_min}/{@code experience_max} 가 비어 있는
   * 공고는 경력 무관으로 보고 남긴다 — 여기서 떨구면 표본이 실제보다 작아진다.
   *
   * @param jobFamily 직무. 해제하지 않는 유일한 차원이다
   * @param regionPrefix 지역 접두사. 상세구는 {@code "서울 강남구"}, 광역 확장은 {@code "서울"} 처럼 넘긴다
   * @param employmentType 고용형태. Level 5 에서 {@code null}
   * @param companySize 회사규모. Level 2 이후 {@code null}
   * @param minYears 경력 범위 하한 (년)
   * @param maxYears 경력 범위 상한 (년)
   */
  @Query(
      """
      SELECT p FROM JobPosting p
      WHERE p.jobFamily = :jobFamily
        AND (:regionPrefix IS NULL OR p.region LIKE CONCAT(:regionPrefix, '%'))
        AND (:employmentType IS NULL OR p.employmentType = :employmentType)
        AND (:companySize IS NULL OR p.companySize = :companySize)
        AND (p.experienceMin IS NULL OR p.experienceMin <= :maxYears)
        AND (p.experienceMax IS NULL OR p.experienceMax >= :minYears)
      """)
  List<JobPosting> findPeerGroupCandidates(
      @Param("jobFamily") String jobFamily,
      @Param("regionPrefix") String regionPrefix,
      @Param("employmentType") String employmentType,
      @Param("companySize") String companySize,
      @Param("minYears") int minYears,
      @Param("maxYears") int maxYears);
}
