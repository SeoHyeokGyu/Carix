package com.carix.jobposting;

/** 표본이 존재하는 (수집 원천, 표본 월 YYYYMM) 한 쌍. */
public record SourceMonth(String source, String sampledMonth) {}
