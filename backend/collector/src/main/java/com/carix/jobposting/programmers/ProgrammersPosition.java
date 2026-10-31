package com.carix.jobposting.programmers;

import java.time.LocalDate;
import java.util.List;

/** 프로그래머스 목록 API 공고 한 건. */
public record ProgrammersPosition(
    String id,
    String title,
    String companyName,
    String address,
    Integer minCareer,
    Integer maxCareer,
    List<String> positionTags,
    List<String> jobCategories,
    LocalDate startAt,
    LocalDate endAt,
    boolean active) {}
