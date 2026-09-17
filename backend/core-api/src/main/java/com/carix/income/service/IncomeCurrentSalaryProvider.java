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
 * <p><b>스냅샷 복호화 경계 (Decryption Boundary, SECURITY §3.2):</b>
 *
 * <ul>
 *   <li><b>단일 진입점 계약:</b> 스냅샷 엔진(Member C의 {@code SnapshotRecalculationService})이나 대시보드는 소득 도메인의
 *       암호화 컬럼({@code gross_salary_encrypted})을 직접 조회하거나 복호화하지 않는다. 오직 이 서비스의 {@link
 *       #currentGrossSalary(UUID, String)} SPI를 통해서만 기준월 시점의 연봉을 얻는다.
 *   <li><b>복호화 캡슐화:</b> 연봉 복호화는 소득 도메인 내부의 JPA Converter({@code EncryptedConverter})에서만 수행되어 외부로
 *       복호화 키나 암호화 상세가 노출되지 않는다.
 *   <li><b>단기 휘발성(Ephemeral Lifecycle):</b> 반환된 {@link CurrentSalary} DTO는 스냅샷 재계산 루프 내에서 결정 신호 평가
 *       인자로만 소비되고 즉시 GC 대상이 된다. 스냅샷 엔티티나 JSONB 근거 데이터에는 평문 금액이나 역산 가능한 연속형 비율이 전혀 남지 않는다.
 *   <li><b>감사 및 로깅:</b> 복호화 조회 시 사용자 식별자({@code userId})와 기준월({@code baseYearMonth})만 디버그 감사 로그로
 *       남기며, 완료 기준 10(민감 금액 로그 미기록)을 준수하여 금액은 일절 출력하지 않는다.
 * </ul>
 *
 * <p><b>기준월 연봉 산정 규약:</b> 기준월({@code YYYYMM}) 말일 이하 중 {@code effective_date} 가 가장 늦은(가장 최근의) 연봉을 유효
 * 연봉으로 채택한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IncomeCurrentSalaryProvider implements CurrentSalaryProvider {

  private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");
  private final IncomeHistoryRepository incomeHistoryRepository;

  /**
   * 사용자의 기준월 시점 세전 연봉을 복호화하여 조회한다.
   *
   * @param userId 대상 사용자 ID (UUID)
   * @param baseYearMonth 기준년월 (YYYYMM 6자리)
   * @return 기준월 말일 시점에 유효한 세전 연봉 DTO. 이력이 없거나 금액이 0 이하이면 {@link Optional#empty()}
   */
  @Override
  @Transactional(readOnly = true)
  public Optional<CurrentSalary> currentGrossSalary(UUID userId, String baseYearMonth) {
    if (userId == null || baseYearMonth == null || baseYearMonth.length() != 6) {
      return Optional.empty();
    }

    YearMonth ym = YearMonth.parse(baseYearMonth, YM);
    LocalDate endOfMonth = ym.atEndOfMonth();

    // 감사 목적의 디버그 로깅: 금액은 절대 포함하지 않고 요청 컨텍스트만 기록
    log.debug("기준월 연봉 복호화 조회: userId={}, baseYearMonth={}", userId, baseYearMonth);

    List<IncomeHistory> histories =
        incomeHistoryRepository.findByUserIdOrderByEffectiveDateDesc(userId);

    // 기준월 말일 이하 중 가장 최근 (내림차순 정렬이므로 조건 충족하는 첫 번째)
    Optional<IncomeHistory> matched =
        histories.stream().filter(h -> !h.getEffectiveDate().isAfter(endOfMonth)).findFirst();

    // 연봉이 없거나 0 이하인 경우 empty 반환 (결정 신호 소득 격차 비율의 분모 0 오류 방지)
    return matched
        .filter(h -> h.getGrossSalary() != null && h.getGrossSalary() > 0)
        .map(h -> new CurrentSalary(h.getGrossSalary(), h.getEffectiveDate()));
  }
}
