# Carix 전체 애플리케이션 작업 흐름 명세 (Application Workflow Specification)

이 문서는 Carix 플랫폼의 사용자 라이프사이클, 외부 OpenAPI 데이터 수집 배치의 파이프라인, 그리고 사용자 커리어 지표 및 시뮬레이션 산출에 이르는 **전체적인 데이터 흐름(E2E Data Flow)**을 명세화합니다.

---

## 1. Carix 전체 서비스 아키텍처 및 흐름도

사용자의 실시간 요청을 처리하는 **API 영역**과 주기적으로 대량의 외부 데이터를 수집·가공하는 **배치 워커 영역**이 유기적으로 연결된 데이터 파이프라인 구조입니다.

```mermaid
flowchart TD
    subgraph Frontend [Next.js 웹 프런트엔드]
        UI[대시보드 / 온보딩 화면]
        Sim[12개월 시뮬레이터 UI]
    end

    subgraph API [Spring Boot API 서버]
        AC[AuthController: 매직링크 로그인]
        PC[ProfileController: 소득/소비/스킬]
        DC[DashboardController: 지표 조회]
        SC[ScenarioController: 시나리오 계산]
        
        Encrypt[JPA Attribute Converter: 데이터 암호화]
        DecEng[결정 신호 룰 엔진]
    end

    subgraph DB [PostgreSQL 데이터베이스]
        AccountDB[(사용자 계정/이력 테이블)]
        RawDB[(수집 원천 데이터 - CPI/공고)]
        AggDB[(시장 데이터 집계 테이블)]
    end

    subgraph Batch [Spring Batch Worker 프로세스]
        Job1[CPI 수집 Job]
        Job2[구인공고 수집 & 스킬 파싱 Job]
        Job3[시장 데이터 통계 집계 Job]
        Job4[사용자 지수 스냅샷 갱신 Job]
    end

    subgraph External [외부 OpenAPI]
        Kosis[KOSIS 물가통계 API]
        Work24[고용24 채용정보 API]
    end

    %% 사용자 가입 및 온보딩 흐름
    UI -->|1. 이메일 로그인| AC
    UI -->|2. 소득/소비/스킬 입력| PC
    PC -->|3. 민감 데이터 암호화| Encrypt
    Encrypt -->|4. 데이터 저장| AccountDB

    %% 배치 수집 파이프라인
    Job1 -->|월 1회 API 호출| Kosis
    Job2 -->|일별/주별 호출| Work24
    Kosis -->|데이터 적재| Job1
    Work24 -->|본문 텍스트 적재| Job2
    
    Job1 -->|5. raw 데이터 저장| RawDB
    Job2 -->|6. 스킬 파싱 및 저장| RawDB
    
    %% 통계 집계 및 스냅샷 갱신
    RawDB -->|7. 데이터 취합| Job3
    Job3 -->|8. 동료집단 통계 적재| AggDB
    
    AggDB & AccountDB -->|9. 지수 계산| Job4
    Job4 -->|10. snap 저장| AccountDB

    %% 서비스 서빙 흐름
    AccountDB & AggDB -->|11. 지표 가공| DecEng
    DecEng -->|12. 결정 신호 및 결과 반환| DC
    DC -->|13. 데이터 시각화 렌더링| UI
    UI -->|14. 시뮬레이션 인풋 전송| SC
    SC -->|15. 잔류/이직 세후 비교 계산| Sim
```

---

## 2. Spring Batch 수집 및 집계 파이프라인 (2번 상세 흐름)

외부 수집 데이터는 대량 데이터의 무결성 보장과 유휴 트래픽 분산을 위해 **Spring Batch 3단계 Job**으로 나누어 실행됩니다.

### 2.1 Job 1: CPI 소비자물가지수 수집 (월 1회 실행)
통계청 물가지수가 매월 초 갱신되는 시점에 기동합니다.

```mermaid
stateDiagram-v2
    [*] --> KosisReader : Job 기동 (매월 초)
    KosisReader --> KosisProcessor : KOSIS API 연월별 데이터 호출 (JSON)
    KosisProcessor --> KosisWriter : Carix 6대 소비 카테고리 매핑 & 기본 가중치 보정
    KosisWriter --> cpi_observation : DB 벌크 삽입 (bulk insert)
    cpi_observation --> [*]
```

### 2.2 Job 2: 고용24 구인공고 수집 및 스킬 정규화 (일/주 1회 실행)
가장 많은 연산과 파싱이 필요한 핵심 배치 파이프라인입니다.

```mermaid
flowchart LR
    Start([Job 시작]) --> Reader[고용24 API Reader: <br>IT/개발 직군 목록 조회]
    Reader --> Filter[Duplicate Filter: <br>wantedAuthNo 중복 및 만료공고 제외]
    Filter --> DetailReader[Detail Reader: <br>공고별 상세 본문 및 연봉 수집]
    
    subgraph SkillParser [스킬 파싱 Processor]
        Preprocess[공백/특수문자 제거, 소문자화] --> AliasMatch{SkillAlias 테이블 매칭}
        AliasMatch -->|매핑 성공| TagSkill[정규화 스킬 태그 지정]
        AliasMatch -->|실패| Drop[통계 수집 제외]
    end
    
    DetailReader --> SkillParser
    TagSkill --> Writer[Writer: job_posting 테이블 적재]
    Writer --> End([Job 완료])
```

### 2.3 Job 3: 시장 데이터 통계 집계 및 사용자 스냅샷 (주 1회 또는 배치 직후)
수집된 원천 공고 및 CPI 데이터를 사용자의 동료집단(Peer Group) 단위로 정교하게 그룹핑하고 통계를 집계합니다.

```mermaid
flowchart TD
    Start([집계 시작]) --> AggregatePeer[동료집단 그룹핑: <br>직무 + 지역 + 경력구간 + 고용형태]
    
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

```text
[사용자]                 [Next.js 웹]             [Spring API]            [DB (PG)]
   │                          │                        │                     │
   │ 1. 이메일 로그인 요청 ────▶│                        │                     │
   │                          │ 2. POST /auth/magic ──▶│                     │
   │                          │                        │ 3. 토큰 해시 저장 ──▶│
   │◀─────────────────────────┼────────────────────────┼─────────────────────│ (이메일 수신 및 링크 클릭)
   │ 4. 매직링크 토큰 제출 ──▶│                        │                     │
   │                          │ 5. POST /auth/verify ─▶│                     │
   │                          │◀───────────────────────┼ 6. 세션 쿠키 발급 ──│
   │                          │                        │                     │
   │ 7. 프로필/소득/소비 입력 ─▶│                        │                     │
   │                          │ 8. PUT /profile ──────▶│                     │
   │                          │                        │ 9. 대칭키 암호화 ───▶│ (연봉/소비 데이터 암호화)
   │                          │                        │                     │
   │ 10. 대시보드 진입 ──────▶│                        │                     │
   │                          │ 11. GET /db/summary ──▶│                     │
   │                          │                        │ 12. 통계 데이터 ◀───│ (집계된 MVI, CPI, 연봉조회)
   │                          │                        │ 13. 결정룰 엔진 기동│
   │                          │◀───────────────────────┼─────────────────────│ (구매력/시장가치/결정신호 반환)
   │ 14. 화면에 차트 렌더링   │                        │                     │
   │                          │                        │                     │
   │ 15. 이직 시뮬레이션 시도 ─▶│                        │                     │
   │ (목표액, 이직비용 조정)  │ 16. POST /scenarios ──▶│                     │
   │                          │                        │ 17. 세후 소득 계산 │
   │                          │◀───────────────────────┼─────────────────────│ (추정 기회비용 피드백 제공)
```
