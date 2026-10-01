package com.carix.index.dto;

import java.math.BigDecimal;

/**
 * 그 사용자의 물가 상승에 가장 크게 기여한 카테고리 (기능 명세 US-D1·§4.3). {@code index_snapshot.rationale} 에 저장된다 — 금액을 담지
 * 않는다.
 *
 * @param category {@code ExpenseCategoryEnum} 이름
 * @param label 화면 문구. 예: {@code 교통 부담률 최대 상승}
 * @param estimated 그 카테고리 가중치가 기본값({@code DEFAULT_CPI})으로 채워졌는지
 * @param cpiMonth 사용한 CPI 기준월 {@code YYYYMM}
 * @param contribution 가중치 × 전년비. 무단위다
 */
public record TopPriceDriver(
    String category, String label, boolean estimated, String cpiMonth, BigDecimal contribution) {}
