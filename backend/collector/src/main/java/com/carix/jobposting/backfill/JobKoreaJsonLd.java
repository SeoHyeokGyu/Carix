package com.carix.jobposting.backfill;

import java.time.LocalDate;

/** 잡코리아 상세페이지 JSON-LD(JobPosting)에서 백필에 쓰는 필드만 담는다. 마감된 공고도 이 블록은 남아 있다. */
public record JobKoreaJsonLd(
    String title,
    String companyName,
    LocalDate datePosted,
    LocalDate validThrough,
    String employmentType,
    String experienceText,
    String region) {}
