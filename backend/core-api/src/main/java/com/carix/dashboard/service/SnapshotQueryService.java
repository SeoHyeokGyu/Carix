package com.carix.dashboard.service;

import com.carix.snapshot.domain.IndexSnapshot;
import com.carix.snapshot.repository.IndexSnapshotRepository;
import com.carix.snapshot.repository.IndexSnapshotSelector;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 스냅샷 읽기 경로의 공통 계층. `/dashboard/summary`·`/history`·`/trends` 셋이 같은 행을 본다.
 *
 * <p>한 월에 여러 행이 쌓이는 것이 정상이다 — 재계산이 새 {@code data_version} 으로 다시 쓰기 때문이다. 어느 행이 유효한지는 {@link
 * IndexSnapshotSelector} 가 규정한 순서(최신 {@code data_version} → 최신 {@code rule_version})를 따르며, 세 화면이 각자
 * 고르면 같은 달에 다른 값을 보여주게 되므로 여기서 한 번만 고른다.
 */
@Service
@RequiredArgsConstructor
public class SnapshotQueryService {

  private final IndexSnapshotRepository indexSnapshotRepository;

  /**
   * 사용자의 모든 월을 최신월부터 훑되 월마다 유효한 한 행만 남긴다.
   *
   * <p>스냅샷이 하나도 없는 것은 오류가 아니다 — 온보딩 직후이거나 재계산이 아직 한 번도 돌지 않은 사용자다. 트리거 배선 자체는 끝나 있다 (기술 명세 §1.3).
   */
  @Transactional(readOnly = true)
  public List<IndexSnapshot> latestPerMonthDesc(UUID userId) {
    Map<String, List<IndexSnapshot>> byMonth = new LinkedHashMap<>();
    for (IndexSnapshot snapshot :
        indexSnapshotRepository.findByUserIdOrderByBaseYearMonthDesc(userId)) {
      byMonth.computeIfAbsent(snapshot.getBaseYearMonth(), key -> new ArrayList<>()).add(snapshot);
    }

    List<IndexSnapshot> selected = new ArrayList<>();
    for (List<IndexSnapshot> sameMonth : byMonth.values()) {
      IndexSnapshotSelector.latest(sameMonth).ifPresent(selected::add);
    }
    selected.sort(Comparator.comparing(IndexSnapshot::getBaseYearMonth).reversed());
    return selected;
  }

  /** 기준월의 유효한 스냅샷. 그 달을 계산한 적이 없으면 empty 다. */
  public Optional<IndexSnapshot> forMonth(
      List<IndexSnapshot> latestPerMonth, String baseYearMonth) {
    return latestPerMonth.stream()
        .filter(snapshot -> snapshot.getBaseYearMonth().equals(baseYearMonth))
        .findFirst();
  }

  /** 기준월 직전에 계산된 달. 달이 연속이라는 보장이 없어 "전월"이 아니라 "그 앞의 계산된 달"이다. */
  public Optional<IndexSnapshot> previousOf(
      List<IndexSnapshot> latestPerMonth, String baseYearMonth) {
    return latestPerMonth.stream()
        .filter(snapshot -> snapshot.getBaseYearMonth().compareTo(baseYearMonth) < 0)
        .findFirst();
  }
}
