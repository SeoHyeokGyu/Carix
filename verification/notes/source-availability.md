# 원천 가용성 사전 조사 (Task 1 Step 5 지원)

조사일: 2026-07-15 / 방법: 공식 포털·문서 페이지 열람 (로그인 불필요 범위)

> **조사 기록이며 정본이 아니다.** 현재 유효한 원천 상태와 호출 규격은
> [외부 원천 API 연동 명세](../../docs/specs/external-api-integration.md)를 따른다.
> 이 문서의 `CARIX_*` 환경변수명은 루트 `.env.example` 기준(`KOSIS_API_KEY` 등)으로
> 대체되었다. 2026-07-25 실측으로 고용24 개인 이용 불가 판정이 확인되었다.

## 판정 요약

| 원천 | 상태 | 근거 |
| --- | --- | --- |
| 고용24 채용정보목록·상세 API | **개인 이용 불가** | 신청 페이지 고지: 개인회원은 채용행사·공채속보·공채기업정보 API만 가능. data.go.kr 항목(3038225)도 LINK 유형으로 동일 신청 페이지로 연결됨 |
| 사람인 job-search API | 이용 가능 (키 발급 필요) | oapi.saramin.co.kr/guide/job-search 문서 확인 |
| KOSIS 공유서비스 | 이용 가능 (키 발급 필요) | CPI와 임금통계 모두 KOSIS 경유 가능 |
| 임금직무정보시스템(wage.go.kr) OpenAPI | 미확인·부정적 | data.go.kr에 해당 시스템 OpenAPI 미등록. 스펙 대체 경로(KOSIS) 사용 |

## 사람인 job-search API 실측 파라미터 (문서 기준)

- 엔드포인트: `https://oapi.saramin.co.kr/job-search`
- 인증: 쿼리 파라미터 `access-key`
- 응답 형식: `Accept: application/json` 헤더로 JSON 선택
- 개발직군 필터: `job_mid_cd=22` (IT개발·데이터)
- 페이징: `start`(0-기반), `count`(기본 10, 최대 110)
- 기간 필터: `published_min`/`published_max`
- 핵심 응답 필드: `keyword`(스킬 키워드, 쉼표 구분), `salary`(@code + 범위 문자열),
  `experience-level`(@code, @min, @max), `job-code`, `location`, `posting-timestamp`
- 호출 제한: 일일 최대 요청 횟수 존재 (에러 코드 4)

## 연봉 기준선 후보 (임금직무정보시스템 대체)

1. **한국고용정보원_직업별_임금정보** — data.go.kr 파일데이터 15122500
   (CSV/JSON+XML). 필드: 직업명, 직종코드, 상위·중위·하위 임금.
   차원 한계: 경력·지역 구분 없음 → 스펙 4.4의 경력구간 요구와 불일치.
2. **KOSIS 고용형태별근로실태조사 계열 통계표** — 직종×경력 임금 차원 기대.
   KOSIS 키로 접근. Task 7에서 통계표 ID와 차원 실측 필요.

## 필요한 키 (사용자 발급)

1. `CARIX_KOSIS_KEY` — kosis.kr/openapi 활용신청 (CPI + 임금통계 겸용)
2. `CARIX_SARAMIN_KEY` — oapi.saramin.co.kr 앱 등록
3. (선택) data.go.kr 일반 인증키 — 직업별_임금정보 파일데이터 API 사용 시

## 스펙 영향

- 채용공고 원천이 사람인 단독이 됨 → 스펙 4.3 `단일 원천` 표시 경로가 기본이 됨
- 고용24 어댑터는 구현하지 않음 (스펙 3.2: 어댑터 비활성화 대응)
- 검증 플랜 Task 4(고용24 실측)는 본 판정으로 대체됨
