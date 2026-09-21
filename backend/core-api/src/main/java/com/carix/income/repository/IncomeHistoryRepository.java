package com.carix.income.repository;

import com.carix.income.domain.IncomeHistory;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncomeHistoryRepository extends JpaRepository<IncomeHistory, UUID> {
  List<IncomeHistory> findByUserIdOrderByEffectiveDateDesc(UUID userId);

  boolean existsByUserIdAndEffectiveDate(UUID userId, LocalDate effectiveDate);

  boolean existsByUserIdAndEffectiveDateAndIdNot(
      UUID userId, LocalDate effectiveDate, UUID excludeId);

  /** 그 유효일보다 이른 행이 있는지. 없으면 그 행이 최소 유효일 행이다 — 읽기 시점 INITIAL 파생에 쓴다. */
  boolean existsByUserIdAndEffectiveDateBefore(UUID userId, LocalDate effectiveDate);

  /**
   * 수정 경로용 — 자기 행을 빼고 센다. 유효일을 뒤로 미루는 수정에서 영속성 컨텍스트가 아직 안 비워졌으면 옛 유효일의 자기 행이 "더 이른 행"으로 잡혀 파생이
   * 뒤집힌다.
   */
  boolean existsByUserIdAndEffectiveDateBeforeAndIdNot(
      UUID userId, LocalDate effectiveDate, UUID excludeId);

  void deleteByUserId(UUID userId);
}
