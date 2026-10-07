package com.carix.jobposting.wanted;

import java.util.List;

/** 원티드 목록 API 공고 한 건. */
public record WantedListItem(String id, String company, String title, List<Integer> childTagIds) {}
