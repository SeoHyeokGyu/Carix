package com.carix.jobposting.seed;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JobKoreaListingEnricherTest {

  private static final String SIX_CELL_ROW = """
      <table><tbody><tr class="devloopArea" data-gno="49744087">
        <td class="tplCo"><a class="link">테스트회사</a></td>
        <td class="tplTit"><div class="titBx"><strong><a class="link" href="/Recruit/GI_Read/49744087">백엔드 개발자</a></strong>
          <p class="etc"><span class="cell">경력2년↑</span> <span class="cell">대졸↑</span> <span class="cell">서울 종로구</span> <span class="cell">정규직</span> <span class="cell">4,000~6,000만원</span> <span class="cell">주임~대리급 외 </span></p>
        </div></td>
        <td class="odd"><span class="time dotum"><span class="tahoma">3</span>일 전 등록</span> <span class="date dotum"><span class="tahoma">~08/17</span>(월)</span></td>
      </tr></tbody></table>
      """;

  private static final String FIVE_CELL_ROW = """
      <table><tbody><tr class="devloopArea" data-gno="49737149">
        <td class="tplCo"><a class="link">다른회사</a></td>
        <td class="tplTit"><div class="titBx"><strong><a class="link" href="/Recruit/GI_Read/49737149">프론트엔드 개발자</a></strong>
          <p class="etc"><span class="cell">경력3년↑</span> <span class="cell">초대졸↑</span> <span class="cell">서울 영등포구</span> <span class="cell">정규직</span> <span class="cell">주임~대리급 외 </span></p>
        </div></td>
        <td class="odd"><span class="time dotum"><span class="tahoma">1</span>일 전 등록</span> <span class="date dotum">상시채용</span></td>
      </tr></tbody></table>
      """;

  private static final String KOSDAQ_ROW = """
      <table><tbody><tr class="devloopArea" data-gno="49727370">
        <td class="tplCo"><a class="link">코스닥회사</a>
          <div class="typ"><a class="lstBtnTy lstBtnKosdaq" href="/x"><span class="blind">코스닥</span></a></div>
        </td>
        <td class="tplTit"><div class="titBx"><strong><a class="link" href="/Recruit/GI_Read/49727370">영업 채용</a></strong>
          <p class="etc"><span class="cell">신입·경력</span> <span class="cell">대졸↑</span> <span class="cell">서울 강서구 외</span> <span class="cell">정규직 외</span> <span class="cell"></span></p>
        </div></td>
        <td class="odd"><span class="time dotum"><span class="tahoma">2</span>일 전 등록</span> <span class="date dotum"><span class="end">오늘마감</span></span></td>
      </tr></tbody></table>
      """;

  @Test
  void sixCellRowExtractsBothSalaryAndPositionLevel() {
    JobKoreaListingFields fields = new JobKoreaListingEnricher().extract(SIX_CELL_ROW, "49744087");

    assertThat(fields.employmentType()).isEqualTo("정규직");
    assertThat(fields.salaryMin()).isEqualTo(4000L);
    assertThat(fields.salaryMax()).isEqualTo(6000L);
    assertThat(fields.positionLevel()).isEqualTo("주임~대리급 외");
    assertThat(fields.postedEndDateRaw()).isEqualTo("~08/17(월)");
  }

  @Test
  void fiveCellRowTreatsFifthCellAsPositionLevelNotSalary() {
    JobKoreaListingFields fields = new JobKoreaListingEnricher().extract(FIVE_CELL_ROW, "49737149");

    assertThat(fields.salaryMin()).isNull();
    assertThat(fields.salaryMax()).isNull();
    assertThat(fields.positionLevel()).isEqualTo("주임~대리급 외");
    assertThat(fields.postedEndDateRaw()).isEqualTo("상시채용");
  }

  @Test
  void extractsCompanyBadgeAndTodayDeadline() {
    JobKoreaListingFields fields = new JobKoreaListingEnricher().extract(KOSDAQ_ROW, "49727370");

    assertThat(fields.companyBadge()).isEqualTo("코스닥");
    assertThat(fields.positionLevel()).isNull();
    assertThat(fields.postedEndDateRaw()).isEqualTo("오늘마감");
  }
}
