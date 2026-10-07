package com.carix.pipeline.explore;

/** 공고 목록 필터. {@code sort} 는 recent|source 화이트리스트 값만 들어온다. {@code sampled} null 이면 표본·정기 수집 모두. */
public record PostingFilter(
    String month,
    String source,
    Boolean sampled,
    String family,
    String company,
    String skill,
    String disclosure,
    String sort,
    int page,
    int size) {}
