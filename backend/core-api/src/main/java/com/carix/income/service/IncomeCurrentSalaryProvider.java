package com.carix.income.service;

import com.carix.income.domain.IncomeHistory;
import com.carix.income.repository.IncomeHistoryRepository;
import com.carix.index.CurrentSalaryProvider;
import com.carix.index.dto.CurrentSalary;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code core.income_history} 를 바탕으로 기준월 시점의 세전 연봉을 조회하는 구현체.
 *
 * <p>기준월 말일 이하 중 가장 최근 {@code effective_date} 의 연봉을 사용한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IncomeCurrentSalaryProvider implements CurrentSalaryProvider {

  private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");
  private final IncomeHistoryRepository incomeHistoryRepository;

  @Override
  @Transactional(readOnly = true)
  public Optional<CurrentSalary> currentGrossSalary(UUID userId, String baseYearMonth) {
    if (userId == null || baseYearMonth == null || baseYearMonth.length() != 6) {
      return Optional.empty();
    }

    YearMonth ym = YearMonth.parse(baseYearMonth, YM);
    LocalDate endOfMonth = ym.atEndOfMonth();

    log.debug("기준월 연봉 복호화 조회: userId={}, baseYearMonth={}", userId, baseYearMonth);

    List<IncomeHistory> histories =
        incomeHistoryRepository.findByUserIdOrderByEffectiveDateDesc(userId);

    // 기준월 말일 이하 중 가장 최근 (내림차순 정렬이므로 조건 충족하는 첫 번째)
    Optional<IncomeHistory> matched =
        histories.stream().filter(h -> !h.getEffectiveDate().isAfter(endOfMonth)).findFirst();

    return matched
        .filter(h -> h.getGrossSalary() != null && h.getGrossSalary() > 0)
        .map(h -> new CurrentSalary(h.getGrossSalary(), h.getEffectiveDate()));
  }
}
