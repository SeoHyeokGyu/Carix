package com.carix.jobposting.seed;

import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

public class JobPostingDateParser {

  private static final Pattern DAYS_AGO = Pattern.compile("^(\\d+)일 전");
  private static final Pattern MONTH_DAY_NO_YEAR = Pattern.compile("(\\d{2})/(\\d{2})");
  private static final Pattern YY_MM_DD = Pattern.compile("(\\d{2})/(\\d{2})/(\\d{2})");

  public static LocalDate parseCreatedAt(String source, String rawText, Instant collectedAt) {
    if (rawText == null || rawText.isBlank()) {
      return null;
    }
    LocalDate collectedDate = collectedAt.atZone(ZoneOffset.UTC).toLocalDate();

    Matcher daysAgo = DAYS_AGO.matcher(rawText);
    if (daysAgo.find()) {
      return collectedDate.minusDays(Long.parseLong(daysAgo.group(1)));
    }

    Matcher yyMmDd = YY_MM_DD.matcher(rawText);
    if (yyMmDd.find()) {
      return LocalDate.of(
          2000 + Integer.parseInt(yyMmDd.group(1)),
          Integer.parseInt(yyMmDd.group(2)),
          Integer.parseInt(yyMmDd.group(3)));
    }

    Matcher monthDay = MONTH_DAY_NO_YEAR.matcher(rawText);
    if (monthDay.find()) {
      return resolveYearlessDateForCreation(
          Integer.parseInt(monthDay.group(1)), Integer.parseInt(monthDay.group(2)), collectedDate);
    }

    return null;
  }

  public static LocalDate parseDeadline(String source, String rawText, Instant collectedAt) {
    if (rawText == null || rawText.isBlank()) {
      return null;
    }
    LocalDate collectedDate = collectedAt.atZone(ZoneOffset.UTC).toLocalDate();

    if (rawText.contains("오늘마감")) {
      return collectedDate;
    }
    if (rawText.contains("내일마감")) {
      return collectedDate.plusDays(1);
    }
    if (rawText.contains("모레마감")) {
      return collectedDate.plusDays(2);
    }
    if (rawText.contains("상시채용") || rawText.contains("채용시") || rawText.contains("진행예정")) {
      return null;
    }

    Matcher monthDay = MONTH_DAY_NO_YEAR.matcher(rawText);
    if (monthDay.find()) {
      return resolveYearlessDateForDeadline(
          Integer.parseInt(monthDay.group(1)), Integer.parseInt(monthDay.group(2)), collectedDate);
    }

    return null;
  }

  private static LocalDate resolveYearlessDateForCreation(
      int month, int day, LocalDate collectedDate) {
    LocalDate candidate = LocalDate.of(collectedDate.getYear(), month, day);
    if (candidate.isAfter(collectedDate)) {
      return candidate.minusYears(1);
    }
    return candidate;
  }

  private static LocalDate resolveYearlessDateForDeadline(
      int month, int day, LocalDate collectedDate) {
    LocalDate candidate = LocalDate.of(collectedDate.getYear(), month, day);
    if (candidate.isBefore(collectedDate)) {
      return candidate.plusYears(1);
    }
    return candidate;
  }
}
