package com.carix.snapshot;

import java.util.UUID;

/**
 * 소득·소비·스킬·프로필 저장이 스냅샷 재계산을 요청한다는 사실. 저장 트랜잭션이 커밋된 뒤에 {@link SnapshotRecalculationListener} 가 처리한다.
 *
 * @param fromYearMonth 이 달부터 현재월까지 다시 계산한다. {@code YYYYMM} 6자
 */
public record SnapshotRecalculationRequested(UUID userId, String fromYearMonth) {}
