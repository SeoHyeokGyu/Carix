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

    assertEquals(3300L, range.min());
    assertEquals(3800L, range.max());
  }

  @Test
  void keepsUpperBoundWhenInclusive() {
    String payJson =
        """
        {"payType":"ANNUALLY_SALARY","payRange":{"from":4000,"to":6000,\
        "fromInclusive":true,"toInclusive":true}}""";

    SalaryRange range = parser.parse(payJson);

    assertEquals(4000L, range.min());
    assertEquals(6000L, range.max());
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
  void returnsNullForBlankOrBrokenJson() {
    assertNull(parser.parse(""));
    assertNull(parser.parse(null));
    assertNull(parser.parse("{not json"));
  }
}
