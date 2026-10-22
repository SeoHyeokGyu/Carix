package com.carix.jobposting.normalize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class JobKoreaPayParserTest {

  private final JobKoreaPayParser parser = new JobKoreaPayParser();

  @Test
  void parsesAnnualSalaryRange() {
    String payJson =
        """
        {"payType":"ANNUALLY_SALARY","payRange":{"from":3300,"to":3801,\
        "fromInclusive":true,"toInclusive":false},"payOptionTypes":[]}""";

    SalaryRange range = parser.parse(payJson);

    assertEquals(33_000_000L, range.min());
    assertEquals(38_000_000L, range.max());
  }

  @Test
  void keepsUpperBoundWhenInclusive() {
    String payJson =
        """
        {"payType":"ANNUALLY_SALARY","payRange":{"from":4000,"to":6000,\
        "fromInclusive":true,"toInclusive":true}}""";

    SalaryRange range = parser.parse(payJson);

    assertEquals(40_000_000L, range.min());
    assertEquals(60_000_000L, range.max());
  }

  @Test
  void returnsNullWhenSalaryIsNotDisclosed() {
    String payJson =
        """
        {"payType":"COMPANY_POLICY","payRange":null,"payOptionTypes":[]}""";

    assertNull(parser.parse(payJson));
  }

  @Test
  void returnsNullForNonAnnualPayTypes() {
    String payJson =
        """
        {"payType":"MONTHLY_SALARY","payRange":{"from":300,"to":401,\
        "fromInclusive":true,"toInclusive":false}}""";

    assertNull(parser.parse(payJson));
  }

  @Test
  void bandConvertsMonthlyToAnnual() {
    String payJson =
        """
        {"payType":"MONTHLY_SALARY","payRange":{"from":500,"to":601,\
        "fromInclusive":true,"toInclusive":false}}""";

    SalaryRange range = parser.parseBand(payJson);

    assertEquals(60_000_000L, range.min());
    assertEquals(72_000_000L, range.max());
  }

  @Test
  void bandIgnoresHourlyPay() {
    assertNull(parser.parseBand("{\"payType\":\"HOURLY_WAGE\",\"payRange\":{\"from\":10030}}"));
  }
}
