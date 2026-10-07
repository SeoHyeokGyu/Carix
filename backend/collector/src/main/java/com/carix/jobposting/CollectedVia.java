package com.carix.jobposting;

/** 공고를 확인한 수집 경로. `job_posting.collected_via` 에 쉼표로 쌓인다(관리자 화면 표시용). */
public final class CollectedVia {

  public static final String JOBKOREA_LIST = "JOBKOREA_LIST";
  public static final String JOBKOREA_BACKFILL = "JOBKOREA_BACKFILL";
  public static final String JOBKOREA_SEED = "JOBKOREA_SEED";
  public static final String SARAMIN_SEARCH = "SARAMIN_SEARCH";
  public static final String JUMPIT = "JUMPIT";
  public static final String WANTED_LIST = "WANTED_LIST";
  public static final String WANTED_BACKFILL = "WANTED_BACKFILL";

  private CollectedVia() {}
}
