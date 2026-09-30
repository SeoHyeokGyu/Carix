package com.carix.index;

import com.carix.index.dto.TopPriceDriver;
import java.util.Optional;
import java.util.UUID;

/** 물가 기여도 1위 산출기. 산출 불가는 예외가 아니라 빈 값이다 — 스냅샷 저장을 막지 않는다. */
public interface TopPriceDriverProvider {

  Optional<TopPriceDriver> find(UUID userId, String baseYearMonth);
}
