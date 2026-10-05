package com.carix.pipeline.explore;

import com.carix.pipeline.dto.explore.MonthlyOverview;
import com.carix.pipeline.dto.explore.PostingPage;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 「수집 데이터」 열람 API. 읽기 전용. 관리자 키는 /internal/pipeline/** 인터셉터가 건다. */
@RestController
@RequestMapping("/internal/pipeline/explore")
@RequiredArgsConstructor
public class DataExploreController {

  private final DataExploreService service;

  @GetMapping("/monthly")
  public MonthlyOverview monthly(
      @RequestParam(required = false) String from, @RequestParam(required = false) String to) {
    return service.monthly(from, to);
  }

  @GetMapping("/postings")
  public PostingPage postings(
      @RequestParam(required = false) String month,
      @RequestParam(required = false) String source,
      @RequestParam(required = false) Boolean sampled,
      @RequestParam(required = false) String family,
      @RequestParam(required = false) String company,
      @RequestParam(required = false) String skill,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    return service.postings(month, source, sampled, family, company, skill, page, size);
  }
}
