package com.carix.scenario;

import com.carix.scenario.domain.CareerScenario;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CareerScenarioRepository extends JpaRepository<CareerScenario, UUID> {

  /**
   * 가장 최근 시나리오 1건 (API 규격 §6.2 — {@code calculated_at DESC, id DESC} 순 첫 행).
   *
   * <p>{@code id} 를 2차 정렬로 두는 이유는 {@code calculated_at} 이 {@code CURRENT_TIMESTAMP} 기본값이라 같은 트랜잭션에서
   * 두 행이 들어가면 값이 같아서다. 그때 순서가 정해지지 않으면 조회가 흔들린다.
   */
  Optional<CareerScenario> findFirstByUserIdOrderByCalculatedAtDescIdDesc(UUID userId);

  /**
   * 저장분 전부, 최근 순 (API 규격 §6.3). 정렬은 {@link #findFirstByUserIdOrderByCalculatedAtDescIdDesc} 와 같다.
   */
  List<CareerScenario> findByUserIdOrderByCalculatedAtDescIdDesc(UUID userId);

  void deleteByUserId(UUID userId);
}
