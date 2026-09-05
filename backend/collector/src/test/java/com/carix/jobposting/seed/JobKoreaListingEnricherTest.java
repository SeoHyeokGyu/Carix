package com.carix.jobposting.seed;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JobKoreaListingEnricherTest {

  private final JobKoreaListingEnricher enricher = new JobKoreaListingEnricher();

  @Test
  @DisplayName("extract - 6개 셀을 포함한 전체 잡코리아 공고 행 파싱")
  void extract_fullRow() {
    String html =
        """
        <table>
          <tr class="devloopArea" data-gno="12345">
            <td class="tplCo">
              <div class="typ">유망중소기업</div>
            </td>
            <td>
              <p class="etc">
                <span class="cell">경력 3~5년</span>
                <span class="cell">학력무관</span>
                <span class="cell">서울 강남구</span>
                <span class="cell">정규직</span>
                <span class="cell">4,000 ~ 6,000 만원</span>
                <span class="cell">대리~과장급</span>
              </p>
            </td>
            <td class="odd">
              <span class="date">~ 09/30(화)</span>
            </td>
          </tr>
        </table>
        """;

    JobKoreaListingFields fields = enricher.extract(html, "12345");

    assertThat(fields.employmentType()).isEqualTo("정규직");
    assertThat(fields.salaryMin()).isEqualTo(4000L);
    assertThat(fields.salaryMax()).isEqualTo(6000L);
    assertThat(fields.positionLevel()).isEqualTo("대리~과장급");
    assertThat(fields.companyBadge()).isEqualTo("유망중소기업");
    assertThat(fields.postedEndDateRaw()).isEqualTo("~ 09/30(화)");
  }

  @Test
  @DisplayName("extract - 5개 셀(급여 미공개) 및 단일 급여 표기 파싱")
  void extract_singleSalary_and_fiveCells() {
    String htmlSingle =
        """
        <table>
          <tr class="devloopArea" data-gno="222">
            <td>
              <p class="etc">
                <span class="cell">신입</span>
                <span class="cell">대졸</span>
                <span class="cell">전국</span>
                <span class="cell">계약직</span>
                <span class="cell">3,500 만원</span>
                <span class="cell">사원</span>
              </p>
            </td>
          </tr>
        </table>
        """;
    JobKoreaListingFields fieldsSingle = enricher.extract(htmlSingle, "222");
    assertThat(fieldsSingle.salaryMin()).isEqualTo(3500L);
    assertThat(fieldsSingle.salaryMax()).isEqualTo(3500L);

    String htmlFive =
        """
        <table>
          <tr class="devloopArea" data-gno="333">
            <td>
              <p class="etc">
                <span class="cell">경력무관</span>
                <span class="cell">학력무관</span>
                <span class="cell">경기 성남시</span>
                <span class="cell">정규직</span>
                <span class="cell">팀원급</span>
              </p>
            </td>
          </tr>
        </table>
        """;
    JobKoreaListingFields fieldsFive = enricher.extract(htmlFive, "333");
    assertThat(fieldsFive.salaryMin()).isNull();
    assertThat(fieldsFive.positionLevel()).isEqualTo("팀원급");
  }

  @Test
  @DisplayName("extract - 존재하지 않는 externalId는 null 필드 반환")
  void extract_notFound() {
    JobKoreaListingFields fields = enricher.extract("<div></div>", "non-existent");
    assertThat(fields.employmentType()).isNull();
    assertThat(fields.salaryMin()).isNull();
  }
}
