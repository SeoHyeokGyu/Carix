package com.carix.jobposting.backfill;

/** 상세페이지 원문과 그 안의 JSON-LD. 직무군·회사 규모·급여·스킬은 원문의 RSC 페이로드에서 읽는다. */
public record JobKoreaPage(
    String externalId, String html, JobKoreaJsonLd jsonLd, String jobFamily) {}
