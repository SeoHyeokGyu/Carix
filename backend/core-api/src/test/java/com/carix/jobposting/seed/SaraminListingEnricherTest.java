package com.carix.jobposting.seed;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SaraminListingEnricherTest {

  private static final String ITEM_HTML = """
      <div class="item_recruit" value="54670349">
        <div class="area_job">
          <h2 class="job_tit"><a href="/x" title="iOS &amp; Android 앱개발(풀스텍) 개발자 모집"><span>iOS &amp; Android 앱개발(풀스텍) <b>개발자</b> 모집</span></a></h2>
          <div class="job_date"><span class="date">~ 09/05(토)</span></div>
          <div class="job_condition">
            <span><a href="/x">서울</a> <a href="/x">서초구</a></span>
            <span>경력3년↑</span>
            <span>대졸↑</span>
            <span>정규직</span>
          </div>
          <div class="job_sector">
            <a href="/zf_user/jobs/list/job-category?cat_kewd=86">앱개발</a>,
            <a href="/zf_user/jobs/list/job-category?cat_kewd=195">Android</a>,
            <a href="/zf_user/jobs/list/job-category?cat_kewd=234">iOS</a>,
            <a href="/zf_user/jobs/list/industry?ind_key=30116">솔루션업체</a>,
            <a href="/zf_user/jobs/list/industry?ind_key=30118">소프트웨어개발</a>
            <span class="job_day">수정일 26/08/06</span>
          </div>
        </div>
      </div>
      """;

  @Test
  void extractsEmploymentTypeDeadlineAndSplitTags() {
    SaraminListingFields fields = new SaraminListingEnricher().extract(ITEM_HTML, "54670349");

    assertThat(fields.employmentType()).isEqualTo("정규직");
    assertThat(fields.postedEndDateRaw()).isEqualTo("~ 09/05(토)");
    assertThat(fields.skillTags()).containsExactly("앱개발", "Android", "iOS");
    assertThat(fields.industryTags()).containsExactly("솔루션업체", "소프트웨어개발");
  }
}
