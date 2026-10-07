package com.carix.jobposting.jumpit;

import java.time.LocalDate;
import java.util.List;

/** 점핏 목록 API 공고 한 건. 공고 번호는 사람인 공고 번호와 같다. */
public record JumpitPosition(
    String id,
    String title,
    String companyName,
    List<String> jobCategories,
    List<String> techStacks,
    Integer minCareer,
    Integer maxCareer,
    boolean newcomer,
    String location,
    LocalDate closedAt) {}
