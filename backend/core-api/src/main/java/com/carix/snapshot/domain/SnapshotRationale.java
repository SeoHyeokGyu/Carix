package com.carix.snapshot.domain;

import com.carix.index.dto.SkillContribution;
import com.carix.index.dto.SourceCoverage;
import com.carix.index.dto.TopPriceDriver;
import com.carix.index.enums.IndexStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * {@code index_snapshot.rationale} 의 JSON 페이로드.
 *
 * <p><b>금액 필드를 두지 않는다.</b> 이 컬럼은 평문이고 연봉은 {@code income_history} 에서 AES-256-GCM 으로 보호된다. 격차도 비율이
 * 아니라 구간으로 담는다 — 비율과 임금통계 중앙값이 함께 있으면 $연봉 = 중앙값 / (1 + 격차비율)$ 로 정확히 복원된다 (기술 명세 §1.3, 기술 명세 §1.3).
 * {@link #wageStatVintage} 는 통계 개정판 식별자라 금액이 아니다.
 *
 * @param rulesApplied 적용된 규칙 문구. 임계값 서술이라 금액을 담지 않는다
 * @param salaryGapBucket 소득 격차 구간({@link com.carix.snapshot.SalaryGapBucket}). 비율을 그대로 담으면 중앙값과 조인해
 *     연봉이 원 단위로 복원되므로 구간만 남긴다 (기술 명세 §1.3). 산출 불가면 {@code null}
 * @param sourceCoverages 원천별 완화 단계·표본 수·결측률. {@code MviResult} 에만 있고 저장되지 않으면 "표본이 부족해 조건을 넓혔다"는
 *     이력이 사라진다 (기술 명세 §1.3)
 * @param skillContributions 스킬별 가중치 × 수요 지수. 빠진 스킬의 이름만으로는 어떤 스킬이 지수를 끌어올렸는지 시간축으로 볼 수 없다 (기술 명세
 *     §1.3)
 * @param excludedSkills 가중합에서 빠진 스킬 이름
 * @param cpiAsOf 사용한 CPI 원천의 기준일
 * @param aggregateAsOf 사용한 시장 집계의 기준일
 * @param wageStatVintage 임금통계 개정판 {@code YYYYMM}
 * @param runId 이 행을 만든 계산 실행 ID. 발급된 {@code data_version} 과 같다
 * @param indexStatuses 지수별 산출 상태. 절대 {@code null} 이 아니다 — 이 필드 도입 전 행은 {@link
 * @param indexStatuses 지수별 산출 상태. 절대 {@code null} 이 아니다 — 이 필드 도입 전 행은 {@link
 *     IndexStatuses#unknown()} 으로 접힌다
 * @param openingWindow 개방창 지수의 산출 근거. 근거가 하나도 없으면 {@code null}
 * @param wageStatSource 임금통계 기준선 출처. {@code KOSIS}, {@code FALLBACK}, {@code UNKNOWN}
 * @param topPriceDriver 물가 기여도 1위 카테고리 (c12). 산출 불가 또는 이 필드 도입 전 행은 {@code null}. 금액을 담지 않는다
 * @param ppiFormulaVersion 구매력 지수 산식 버전. 이 필드 도입 전 행은 {@code null} 로 읽히며 {@link
 *     #ppiFormulaVersionOrLegacy()} 가 {@code "1"} 로 돌려준다. 저장된 행은 다시 쓰지 않는다
 * @param expenseWeightStatus 그 달 구매력 지수를 만든 소비 가중치 상태({@code ExpenseWeightStatus} 이름). 표시된 숫자와 같은
 *     가중치를 말해야 하므로 조회 때 다시 계산하지 않고 저장한다. 이 필드 도입 전 행은 {@code null} 이고 뱃지를 그리지 않는다
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SnapshotRationale(
    List<String> rulesApplied,
    String salaryGapBucket,
    List<SourceCoverage> sourceCoverages,
    List<SkillContribution> skillContributions,
    List<String> excludedSkills,
    LocalDate cpiAsOf,
    LocalDate aggregateAsOf,
    String wageStatVintage,
    String runId,
    IndexStatuses indexStatuses,
    OpeningWindowEvidence openingWindow,
    String wageStatSource,
    TopPriceDriver topPriceDriver,
    String ppiFormulaVersion,
    String expenseWeightStatus) {

  /** 구매력 지수 산식 버전이 없는 행(도입 전)이 읽힐 때의 버전. */
  public static final String LEGACY_PPI_FORMULA_VERSION = "1";

  public SnapshotRationale(
      List<String> rulesApplied,
      String salaryGapBucket,
      List<SourceCoverage> sourceCoverages,
      List<SkillContribution> skillContributions,
      List<String> excludedSkills,
      LocalDate cpiAsOf,
      LocalDate aggregateAsOf,
      String wageStatVintage,
      String runId,
      IndexStatuses indexStatuses,
      OpeningWindowEvidence openingWindow,
      String wageStatSource,
      TopPriceDriver topPriceDriver,
      String ppiFormulaVersion) {
    this(
        rulesApplied,
        salaryGapBucket,
        sourceCoverages,
        skillContributions,
        excludedSkills,
        cpiAsOf,
        aggregateAsOf,
        wageStatVintage,
        runId,
        indexStatuses,
        openingWindow,
        wageStatSource,
        topPriceDriver,
        ppiFormulaVersion,
        null);
  }

  public SnapshotRationale(
      List<String> rulesApplied,
      String salaryGapBucket,
      List<SourceCoverage> sourceCoverages,
      List<SkillContribution> skillContributions,
      List<String> excludedSkills,
      LocalDate cpiAsOf,
      LocalDate aggregateAsOf,
      String wageStatVintage,
      String runId,
      IndexStatuses indexStatuses,
      OpeningWindowEvidence openingWindow,
      String wageStatSource,
      TopPriceDriver topPriceDriver) {
    this(
        rulesApplied,
        salaryGapBucket,
        sourceCoverages,
        skillContributions,
        excludedSkills,
        cpiAsOf,
        aggregateAsOf,
        wageStatVintage,
        runId,
        indexStatuses,
        openingWindow,
        wageStatSource,
        topPriceDriver,
        null);
  }

  public SnapshotRationale(
      List<String> rulesApplied,
      String salaryGapBucket,
      List<SourceCoverage> sourceCoverages,
      List<SkillContribution> skillContributions,
      List<String> excludedSkills,
      LocalDate cpiAsOf,
      LocalDate aggregateAsOf,
      String wageStatVintage,
      String runId,
      IndexStatuses indexStatuses,
      OpeningWindowEvidence openingWindow,
      String wageStatSource) {
    this(
        rulesApplied,
        salaryGapBucket,
        sourceCoverages,
        skillContributions,
        excludedSkills,
        cpiAsOf,
        aggregateAsOf,
        wageStatVintage,
        runId,
        indexStatuses,
        openingWindow,
        wageStatSource,
        null);
  }

  public SnapshotRationale(
      List<String> rulesApplied,
      String salaryGapBucket,
      List<SourceCoverage> sourceCoverages,
      List<SkillContribution> skillContributions,
      List<String> excludedSkills,
      LocalDate cpiAsOf,
      LocalDate aggregateAsOf,
      String wageStatVintage,
      String runId,
      IndexStatuses indexStatuses,
      OpeningWindowEvidence openingWindow) {
    this(
        rulesApplied,
        salaryGapBucket,
        sourceCoverages,
        skillContributions,
        excludedSkills,
        cpiAsOf,
        aggregateAsOf,
        wageStatVintage,
        runId,
        indexStatuses,
        openingWindow,
        null);
  }

  public SnapshotRationale {
    rulesApplied = rulesApplied == null ? List.of() : List.copyOf(rulesApplied);
    excludedSkills = excludedSkills == null ? List.of() : List.copyOf(excludedSkills);
    sourceCoverages = sourceCoverages == null ? List.of() : List.copyOf(sourceCoverages);
    skillContributions = skillContributions == null ? List.of() : List.copyOf(skillContributions);
    // jsonb 라 이 필드 도입 전에 쓰인 행은 null 로 역직렬화된다. 여기서 접어 두면 읽기 경로가
    // 널 체크를 하지 않는다. 세 지수가 모두 null 인 인스턴스는 `value == null` 과 만나
    // NOT_AVAILABLE 로 접히므로(§3.3) 결과가 도입 전 동작과 같다.
    indexStatuses = indexStatuses == null ? IndexStatuses.unknown() : indexStatuses;
  }

  public String ppiFormulaVersionOrLegacy() {
    return ppiFormulaVersion == null ? LEGACY_PPI_FORMULA_VERSION : ppiFormulaVersion;
  }

  /**
   * 지수별 산출 상태. 값을 {@code null} 로 저장하는 것만으로는 "표본이 부족했다"와 "산출기가 없었다"가 구분되지 않아 화면이 사유를 말하지 못한다.
   *
   * @param ppi 구매력 지수 상태
   * @param mvi 시장가치 지수 상태
   * @param owi 개방창 지수 상태
   */
  public record IndexStatuses(IndexStatus ppi, IndexStatus mvi, IndexStatus owi) {

    /** 이 필드 도입 이전에 저장된 행. 세 지수 모두 사유를 모른다. */
    public static IndexStatuses unknown() {
      return new IndexStatuses(null, null, null);
    }
  }

  /**
   * 채용 활발도(OWI)의 산출 근거 (기능 명세 §4.5, b22). 비중·표본 수뿐이라 금액 제약에 걸리지 않는다.
   *
   * <p>화면에 나가는 것은 API 규격 §5.1 의 셋과 그 재료로 만든 시점 근거 문장뿐이지만 전부 저장한다 — 완화 결과는 당시 프로필에 의존해 나중에 재계산하면 같은
   * 값이 안 나온다. {@code b22} 이전 스냅샷 행의 구 필드({@code medianWindowDays} 등)도 역직렬화 시 무시되도록 {@link
   * JsonIgnoreProperties} 를 둔다.
   *
   * @param peerSampleSize 최근 3개월 동료집단 표본 수
   * @param baselineSampleSize 이전 12개월 동료집단 표본 수
   * @param recentSharePercent 최근 3개월 동료집단 채용 비중(%). 최근 창 표본 부족이면 {@code null}
   * @param baselineSharePercent 이전 12개월 동료집단 채용 비중(%). 이전 창 표본 부족이면 {@code null}
   * @param fallbackLevel 안착한 완화 단계
   * @param appliedConditions 안착 조건 문구
   * @param marketSampleSize 최근 3개월 전체시장 공고 수
   * @param standardError 지수의 표준오차(지수 포인트). 이 필드 도입 전 행은 {@code null}
   * @param recentFromMonth 최근 창의 첫 달({@code YYYYMM}). 이 필드 도입 전 행은 {@code null}
   * @param recentToMonth 최근 창의 마지막 달({@code YYYYMM}). 이 필드 도입 전 행은 {@code null}
   * @param windowsFullyCovered 결합 원천 전부가 두 창을 채웠는가. 이 필드 도입 전 행은 {@code null}
   * @param maxFallbackLevel 결합 원천 중 가장 넓은 완화 단계. 이 필드 도입 전 행은 {@code null}
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record OpeningWindowEvidence(
      Integer peerSampleSize,
      Integer baselineSampleSize,
      BigDecimal recentSharePercent,
      BigDecimal baselineSharePercent,
      Integer fallbackLevel,
      String appliedConditions,
      Integer marketSampleSize,
      BigDecimal standardError,
      String recentFromMonth,
      String recentToMonth,
      Boolean windowsFullyCovered,
      Integer maxFallbackLevel) {}
}
