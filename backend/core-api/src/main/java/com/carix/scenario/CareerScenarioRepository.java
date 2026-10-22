package com.carix.scenario;

import com.carix.scenario.domain.CareerScenario;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CareerScenarioRepository extends JpaRepository<CareerScenario, UUID> {

  /**
   * 저장분 전부(초안 제외), 최근 순 (API 규격 §6.3).
   *
   * <p>{@code id} 를 2차 정렬로 두는 이유는 {@code calculated_at} 이 {@code CURRENT_TIMESTAMP} 기본값이라 같은 트랜잭션에서
   * 두 행이 들어가면 값이 같아서다. 그때 순서가 정해지지 않으면 목록이 흔들린다.
   */
  List<CareerScenario> findByUserIdAndDraftFalseOrderByCalculatedAtDescIdDesc(UUID userId);

  /** 사용자의 초안. 운영 DB 는 부분 유니크 인덱스가 1건을 보장하지만 H2 는 못 잡으므로 서비스도 1건만 만든다. */
  Optional<CareerScenario> findFirstByUserIdAndDraftTrue(UUID userId);

  void deleteByUserId(UUID userId);
}
