# Carix 전체 애플리케이션 작업 흐름 명세 (Application Workflow Specification)

이 문서는 Carix 플랫폼의 사용자 라이프사이클, 외부 OpenAPI 데이터 수집 배치의 파이프라인, 그리고 사용자 커리어 지표 및 시뮬레이션 산출에 이르는 **전체적인 데이터 흐름(E2E Data Flow)**을 명세화합니다.

> **계보 주의 (2026-07-25 / 2026-08-08):** 아래 다이어그램·Job 2 설명의 "고용24 API"는
> 실측으로 폐기가 확정됐다(개인회원 API 호출 차단). 사람인도 공식 오픈 API가 아니라
> 스크래핑으로 전환됐다. 배치 구조(중복제거·dedup_group·스킬 정규화)는 여전히
> 유효하나, 실제 원천은 잡코리아·사람인 스크래핑 어댑터다. 최신 원천 구성은
> [외부 원천 API 연동 명세](external-api-integration.md) 참조.

---

## 1. Carix 전체 서비스 아키텍처 및 흐름도

사용자의 실시간 요청을 처리하는 **API 영역**과 주기적으로 대량의 외부 데이터를 수집·가공하는 **배치 워커 영역**이 유기적으로 연결된 데이터 파이프라인 구조입니다. 가로 너비를 최소화하고 세로 흐름을 따라 배치되었습니다.

```mermaid
flowchart TD
    %% 1. 외부 데이터 및 수집 (Top)
    subgraph External [외부 OpenAPI]
        Kosis[KOSIS 물가통계 API]
        Work24[고용24 채용정보 API]
        Saramin[사람인 오픈 API]
        WageStat[임금통계 API]
    end

    %% 2. 배치 프로세서 (Down 1)
    subgraph Batch [Spring Batch Worker 프로세스]
        Job1[CPI 수집 Job]
        Job2[구인공고 수집 & 스킬 파싱 Job - 고용24/사람인]
        Job2b[임금통계 수집 Job]
        Job3[시장 데이터 통계 집계 Job - 원천별 + dedup]
        Job4[사용자 지수 스냅샷 갱신 Job]
    end

    %% 3. 데이터베이스 (Down 2)
    subgraph DB [PostgreSQL 데이터베이스]
        RawDB[(수집 원천 데이터 - CPI/공고)]
        AggDB[(시장 데이터 집계 테이블)]
        UsersDB[(사용자 정보/이력 테이블)]
    end

    %% 4. API 서버 (Down 3)
    subgraph API [Spring Boot API 서버]
        AC[AuthController: 매직링크 로그인]
        PC[ProfileController: 소득/소비/스킬]
        DC[DashboardController: 지표 조회]
        SC[ScenarioController: 시나리오 계산]
        Encrypt[JPA Attribute Converter: 데이터 암호화]
        DecEng[결정 신호 룰 엔진]
    end

    %% 5. 프런트엔드 (Bottom)
    subgraph Frontend [React 웹 프런트엔드 - Vite + Node SSR]
        UI[대시보드 / 온보딩 화면]
        Sim[12개월 시뮬레이터 UI]
    end

    %% 외부 -> 배치 연동
    Kosis -->|데이터 응답| Job1
    Work24 -->|본문 텍스트 응답| Job2
    Saramin -->|공고 응답| Job2
    WageStat -->|임금통계 응답| Job2b
    Job1 -->|API 호출| Kosis
    Job2 -->|API 호출| Work24
    Job2 -->|API 호출| Saramin
    Job2b -->|API 호출| WageStat
    Job2b -->|정규화 적재| RawDB

    %% 배치 -> DB 연동
    Job1 -->|raw CPI 저장| RawDB
    Job2 -->|정규화 스킬 저장| RawDB
    RawDB -->|데이터 조회| Job3
    Job3 -->|통계 적재| AggDB
    AggDB & UsersDB -->|지수 계산| Job4
    Job4 -->|snap 저장| UsersDB

    %% 사용자 입력 -> API -> DB 연동
    UI -->|1. 이메일 로그인| AC
    UI -->|2. 소득/소비/스킬 입력| PC
    PC -->|3. 민감 데이터 암호화| Encrypt
    Encrypt -->|4. 데이터 저장| UsersDB

    %% API -> 프런트엔드 서빙 연동
    UsersDB & AggDB -->|지표 가공| DecEng
    DecEng -->|결정 신호 반환| DC
    DC -->|결과 렌더링| UI
    UI -->|시뮬레이션 인풋| SC
    SC -->|세전 비교 결과 반환| Sim
```

---

## 2. Spring Batch 수집 및 집계 파이프라인 (2번 상세 흐름)

외부 수집 데이터는 대량 데이터의 무결성 보장과 유휴 트래픽 분산을 위해 **Spring Batch Job 파이프라인**(CPI 수집, 공고 수집·파싱, 임금통계 수집, 집계·스냅샷)으로 나누어 실행됩니다. 모든 원천 응답은 정규화 전에 `raw_ingest`에 원문 보존됩니다.

### 2.1 Job 1: CPI 소비자물가지수 수집 (월 1회 실행)
통계청 공표 일정에 맞춰 기동합니다. 구체적 스케줄은 선행 검증에서 KOSIS 공표 일정을 확인한 뒤 확정합니다.

```mermaid
stateDiagram-v2
    [*] --> KosisReader : Job 기동 (통계청 공표 일정 기준)
    KosisReader --> RawIngest : 원문 응답 raw_ingest 보존
    RawIngest --> KosisProcessor : KOSIS API 연월별 데이터 (JSON)
    KosisProcessor --> KosisWriter : Carix 6대 소비 카테고리 매핑 & 기본 가중치 보정
    KosisWriter --> cpi_observation : DB 벌크 삽입 (bulk insert)
    cpi_observation --> [*]
```

### 2.2 Job 2: 구인공고 수집 및 스킬 정규화 (일/주 1회 실행)
가장 많은 연산과 파싱이 필요한 핵심 배치 파이프라인입니다. 고용24와 사람인 어댑터가 동일 구조로 실행되며, 수집 후 원천 간 중복 공고를 `dedup_group_id`로 매칭합니다. 파싱된 스킬은 `job_posting_skill`에 공고별로 저장합니다.

```mermaid
flowchart TD
    Start([Job 시작]) --> Reader[원천별 API Reader - 고용24/사람인: <br>IT/개발 직군 목록 조회]
    Reader --> Filter[Duplicate Filter: <br>원천 공고ID 중복 및 만료공고 제외 <br>+ 원천 간 dedup_group 매칭]
    Filter --> DetailReader[Detail Reader: <br>공고별 상세 본문 및 연봉 수집]
    
    subgraph SkillParser [스킬 파싱 Processor]
        Preprocess[소문자화 + 영숫자 경계 토큰 분리 <br>상세 명세 §3.2 토큰 경계 매칭] --> AliasMatch{SkillAlias 토큰 시퀀스 일치}
        AliasMatch -->|매핑 성공| TagSkill[정규화 스킬 태그 지정]
        AliasMatch -->|실패| Drop[통계 수집 제외]
    end
    
    DetailReader --> RawStore[raw_ingest 원문 보존]
    RawStore --> SkillParser
    SkillParser --> Writer[Writer: job_posting + job_posting_skill 적재]
    Writer --> End([Job 완료])
```

### 2.3 Job 3·Job 4: 시장 데이터 통계 집계와 사용자 스냅샷 (수집 Job 완료 직후 연쇄 실행)
Job 3(원천별 집계)과 Job 4(사용자 스냅샷)는 별도 Job이며 Job 3 성공 시 Job 4가 이어서 실행됩니다. 아래 흐름도는 두 Job의 연쇄를 하나로 표현한 것입니다. 수집된 원천 공고 및 CPI 데이터를 사용자의 동료집단(Peer Group) 단위로 그룹핑하고 통계를 집계합니다.

롤업 집합(GROUPING SETS) 열거 — 롤업 차원은 `employment_type`, `company_size`, `skill_id` 3개이며, 원천×월×직무×지역×경력구간마다 다음 6개 집합을 생성합니다 (`NULL` = 전체 롤업, 원천 미상 `'UNKNOWN'`은 세분 값의 하나로 취급):

| # | employment_type | company_size | skill_id | 용도 |
| --- | --- | --- | --- | --- |
| 1 | 세분 값 | 세분 값 | 세분 값 | 완화 Level 1 |
| 2 | 세분 값 | NULL | 세분 값 | 완화 Level 2~4 |
| 3 | NULL | NULL | 세분 값 | 완화 Level 5 |
| 4 | 세분 값 | 세분 값 | NULL | 스킬 무관 전체 (Level 1 분모·분포) |
| 5 | 세분 값 | NULL | NULL | 스킬 무관 전체 (Level 2~4) |
| 6 | NULL | NULL | NULL | 스킬 무관 전체 (Level 5) |

```mermaid
flowchart TD
    Start([집계 시작]) --> AggregatePeer[동료집단 그룹핑 - GROUPING SETS: <br>직무 + 지역 + 경력구간 + 고용형태 + 회사규모 <br>완화 대응 롤업 행: NULL=전체, 원천 미상='UNKNOWN' 버킷 분리 <br>원천별 저장]
    
    subgraph Stats [통계 계산기]
        CalDemand[스킬 수요율 계산: <br>특정 스킬 공고 / 비교군 전체 공고]
        CalMedian[연봉 중앙값 계산: <br>면접후 결정 제외한 유효 연봉]
        CalMissing[연봉 결측률 집계: <br>연봉 미공개 공고 비율 %]
    end
    
    AggregatePeer --> Stats
    Stats --> SaveAgg[market_aggregate 테이블 저장]
    
    subgraph Snapshot [사용자 스냅샷 생성]
        MapUser[사용자 보유 스킬 및 프로필 매칭] --> CalPPI[실질 구매력 지수 계산]
        MapUser --> CalMVI[스킬 시장가치 지수 계산]
        CalPPI & CalMVI --> GetSignal[결정 신호 룰 엔진 가동]
    end
    
    SaveAgg --> Snapshot
    Snapshot --> SaveSnap[index_snapshot 테이블 저장]
    SaveSnap --> End([완료])
```

---

## 3. 사용자 경험 시나리오 흐름 (User Experience Flow)

실제 사용자가 매직링크로 유입되어 의사결정 시뮬레이션을 받기까지의 API 통신 순서입니다.

```mermaid
sequenceDiagram
    autonumber
    actor User as 사용자
    participant Web as React 웹 (Node SSR)
    participant API as Spring API
    participant DB as PostgreSQL DB

    User->>Web: 이메일 로그인 요청
    Web->>API: POST /api/v1/auth/magic-link (이메일 전송 요청)
    API->>DB: 토큰 해시 저장
    Note over User, API: (이메일 수신 및 매직링크 클릭)
    User->>Web: 매직링크 토큰 제출
    Web->>API: POST /api/v1/auth/verify (토큰 검증)
    API-->>Web: 세션 쿠키 발급
    User->>Web: 프로필/소득/소비 입력
    Web->>API: PATCH /api/v1/profile (프로필 저장, 부분 필드 허용)
    Web->>API: POST /api/v1/income (소득 저장)
    Web->>API: PUT /api/v1/expenses/{yyyymm} (소비 저장)
    API->>DB: 연봉/소비 데이터 암호화 저장 (JPA Converter)
    User->>Web: 대시보드 진입
    Web->>API: GET /api/v1/dashboard/summary (지표 조회)
    API->>DB: 통계 데이터 조회 (CPI, MVI, 연봉 등)
    DB-->>API: 통계 데이터 반환
    Note over API: 결정 신호 룰 엔진 기동
    API-->>Web: 구매력/시장가치/결정신호 반환
    Web-->>User: 화면에 차트 및 지표 렌더링
    User->>Web: 이직 시뮬레이션 시도 (목표액, 비용 조정)
    Web->>API: POST /api/v1/scenarios/simulate (시뮬레이션 요청)
    Note over API: 공백 기간·실업급여 반영 세전 비교 연산
    API-->>Web: 추정 추가보상/기회비용 결과 반환
    Web-->>User: 시뮬레이션 비교 차트 표시
```
