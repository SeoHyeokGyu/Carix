package com.carix.jobposting.wanted;

import java.time.LocalDate;
import java.util.List;

/** 원티드 공고 페이지({@code /wd/{id}}) 한 건. 스킬 태그는 페이지에 없어 상세 API 로 따로 읽는다. */
public record WantedPosting(
    String id,
    String company,
    String title,
    LocalDate postedDate,
    LocalDate endDate,
    int parentTagId,
    List<Integer> childTagIds,
    List<String> childTagNames,
    Integer annualFrom,
    Integer annualTo,
    boolean newbie,
    String region,
    String employmentType,
    String bodyText) {}
