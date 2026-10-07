package com.carix.pipeline.dto;

/**
 * 과거 공고 표본 백필 요청. 값이 비면 기본값을 쓴다. ID 구간은 잡코리아 상세페이지 ID 이고 월 경계는 이 안에서 게시일로 찾는다.
 *
 * @param fromMonth 시작 월(yyyyMM, 포함)
 * @param toMonth 끝 월(yyyyMM, 포함)
 * @param perMonthTarget 월마다 저장할 IT 공고 수
 * @param maxRequestsPerMonth 월마다 상세페이지 요청 상한
 * @param delayMs 상세페이지 요청 간격
 * @param minId ID 탐색 하한
 * @param maxId ID 탐색 상한(포함)
 * @param source 표본 원천({@code JOBKOREA} 기본, {@code WANTED}). ID 구간은 그 원천의 공고 번호
 */
public record BackfillRequest(
    String fromMonth,
    String toMonth,
    Integer perMonthTarget,
    Integer maxRequestsPerMonth,
    Long delayMs,
    Long minId,
    Long maxId,
    String source) {}
