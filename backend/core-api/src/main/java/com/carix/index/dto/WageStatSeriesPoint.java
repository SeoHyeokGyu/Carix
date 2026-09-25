package com.carix.index.dto;

import com.carix.wagestat.WageStatSource;

/**
 * 공식 임금통계 연도별 관측치 및 전년 대비 구간 상승률 (US-D8, 기능 명세 §4.9).
 *
 * @param year 통계 기준 연도 (YYYY)
 * @param wageMedian 연간 세전 중앙값 (원 단위)
 * @param wageDefinition 임금 정의 문구 (예: ANNUAL_GROSS_MEDIAN)
 * @param source 관측치 출처 (KOSIS, FALLBACK, UNKNOWN)
 * @param statVintage 통계 개정판 (YYYYMM)
 * @param growthRatePercent 전년 대비 구간 상승률 (%). 직전 연도와 해당 연도가 모두 KOSIS 실측이고 연속 연도일 때만 산출되며, 그 외에는 null
 *     (0% 로 대체 금지, 기능 명세 §4.9)
 */
public record WageStatSeriesPoint(
    String year,
    long wageMedian,
    String wageDefinition,
    WageStatSource source,
    String statVintage,
    Double growthRatePercent) {}
