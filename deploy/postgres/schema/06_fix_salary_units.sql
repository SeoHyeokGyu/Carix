-- 06_fix_salary_units.sql
-- 기존 DB에 만원 단위(예: 3,000 ~ 50,000)로 저장된 salary_min, salary_max 및 market_aggregate 통계치를 표준 원(KRW) 단위로 보정합니다.

-- 1. job_posting 테이블 연봉 단위 보정
UPDATE core.job_posting
SET salary_min = salary_min * 10000
WHERE salary_min IS NOT NULL AND salary_min > 0 AND salary_min < 1000000;

UPDATE core.job_posting
SET salary_max = salary_max * 10000
WHERE salary_max IS NOT NULL AND salary_max > 0 AND salary_max < 1000000;
