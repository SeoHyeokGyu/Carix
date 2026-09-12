-- 06_fix_salary_units.sql
-- 기존 DB에 만원 단위(예: 3,000 ~ 50,000)로 저장된 salary_min, salary_max 및 market_aggregate 통계치를 표준 원(KRW) 단위로 보정합니다.

-- 1. job_posting 테이블 연봉 단위 보정
UPDATE core.job_posting
SET salary_min = salary_min * 10000
WHERE salary_min IS NOT NULL AND salary_min > 0 AND salary_min < 1000000;

UPDATE core.job_posting
SET salary_max = salary_max * 10000
WHERE salary_max IS NOT NULL AND salary_max > 0 AND salary_max < 1000000;

-- 2. market_aggregate 테이블 5수치 집계 데이터 보정
UPDATE core.market_aggregate
SET salary_min = CASE WHEN salary_min IS NOT NULL AND salary_min > 0 AND salary_min < 1000000 THEN salary_min * 10000 ELSE salary_min END,
    salary_p25 = CASE WHEN salary_p25 IS NOT NULL AND salary_p25 > 0 AND salary_p25 < 1000000 THEN salary_p25 * 10000 ELSE salary_p25 END,
    salary_median = CASE WHEN salary_median IS NOT NULL AND salary_median > 0 AND salary_median < 1000000 THEN salary_median * 10000 ELSE salary_median END,
    salary_p75 = CASE WHEN salary_p75 IS NOT NULL AND salary_p75 > 0 AND salary_p75 < 1000000 THEN salary_p75 * 10000 ELSE salary_p75 END,
    salary_max = CASE WHEN salary_max IS NOT NULL AND salary_max > 0 AND salary_max < 1000000 THEN salary_max * 10000 ELSE salary_max END
WHERE salary_median IS NOT NULL AND salary_median > 0 AND salary_median < 1000000;
