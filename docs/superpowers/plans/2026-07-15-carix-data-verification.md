# Carix 데이터 선행 검증 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 재설계 스펙 7장의 선행 검증을 수행해 원천 4개의 사용 가능성을 실측하고, 구현 플랜(2단계)의 입력이 될 검증 결과 문서를 만든다.

**Architecture:** `verification/` 디렉터리에 Python 표준 라이브러리만 사용하는 일회성 측정 스크립트를 만든다. 공통 프로브 스크립트 1개가 원천 API를 호출해 원문과 필드 목록을 저장하고, 스킬 매칭 스크립트가 재현율을 측정한다. 측정값은 검증 결과 문서에 기록한다.

**Tech Stack:** Python 3 (표준 라이브러리만), Git

**참조 스펙:** `docs/superpowers/specs/2026-07-15-carix-pipeline-redesign-design.md` 7장

## Global Constraints

- 브랜치 `feature/data-verification`에서 작업한다. main 직접 커밋 금지(사용자 직접 지시 시에만 허용).
- 커밋 메시지는 한글 Conventional Commits(`feat`, `fix`, `docs`, `test`, `chore`). Co-Authored-By 태그 금지.
- API 키를 코드·커밋에 넣지 않는다. 키는 환경변수로만 주입한다.
- `verification/out/`은 gitignore 대상이다. 원문 응답 파일을 커밋하지 않는다.
- 검증 스크립트는 일회성 측정 도구이므로 단위 테스트를 작성하지 않는다.
- API 엔드포인트 URL은 공식 문서에서 확인한 값만 사용한다. 추측한 URL을 config에 넣지 않는다.
- 스펙 7장 원칙: 데이터가 없는 영역을 추정값으로 채우지 않는다. 측정 불가 항목은 `측정 불가`와 사유를 기록한다.

---

### Task 1: 검증 작업 골격과 API 키 발급 (사용자 행동 포함)

**Files:**
- Create: `verification/README.md`
- Create: `verification/config.example.json`
- Modify: `.gitignore`

**Interfaces:**
- Produces: `verification/config.json` 스키마(로컬 전용) — 이후 모든 태스크가 이 config에서 `base_url`과 `key_env`를 읽는다.

- [ ] **Step 1: 브랜치 생성**

```bash
git checkout -b feature/data-verification
```

- [ ] **Step 2: 디렉터리와 gitignore 추가**

`.gitignore` 끝에 추가:

```text
verification/out/
verification/config.json
```

- [ ] **Step 3: config 예시 작성**

`verification/config.example.json`:

```json
{
  "kosis": {
    "base_url": "공식 문서에서 확인한 KOSIS 통계자료 API URL",
    "key_env": "CARIX_KOSIS_KEY"
  },
  "work24": {
    "base_url": "공식 문서에서 확인한 고용24 채용정보 API URL",
    "key_env": "CARIX_WORK24_KEY"
  },
  "saramin": {
    "base_url": "공식 문서에서 확인한 사람인 채용공고 API URL",
    "key_env": "CARIX_SARAMIN_KEY"
  },
  "wagestat": {
    "base_url": "임금 통계 API URL (임금직무정보시스템 또는 KOSIS 대체)",
    "key_env": "CARIX_WAGESTAT_KEY"
  }
}
```

- [ ] **Step 4: README 작성**

`verification/README.md`:

```markdown
# Carix 데이터 선행 검증

재설계 스펙 7장의 측정 스크립트. 일회성 도구이며 제품 코드가 아니다.

## 준비

1. `config.example.json`을 `config.json`으로 복사하고 공식 문서에서 확인한
   실제 엔드포인트 URL을 채운다.
2. API 키를 환경변수로 설정한다:
   `CARIX_KOSIS_KEY`, `CARIX_WORK24_KEY`, `CARIX_SARAMIN_KEY`, `CARIX_WAGESTAT_KEY`
3. `python3 probe.py <source> --params '<JSON>'` 형태로 실행한다.

## 산출물

- `out/<source>_raw_<n>.json`: 원문 응답 (커밋 금지)
- `out/<source>_fields.json`: 필드 경로 목록
- 측정 요약은 검증 결과 문서에 기록:
  `docs/superpowers/specs/2026-07-XX-carix-data-verification-result.md`
```

- [ ] **Step 5: 사용자 행동 — API 키 발급 요청**

아래는 계정 생성·약관 동의가 필요하므로 **사용자가 직접** 수행한다. 작업자는 목록을 제시하고 완료를 기다린다:

1. KOSIS 공유서비스 (kosis.kr/openapi) — 활용신청, 키 발급
2. 고용24 채용정보 OpenAPI (work24.go.kr) — 활용신청, 키 발급
3. 사람인 오픈 API (oapi.saramin.co.kr) — 키 발급, **이용약관에서 비상업(포트폴리오) 이용 허용 여부 확인·기록**
4. 임금직무정보시스템 (wage.go.kr) — OpenAPI 제공 여부 확인. 미제공이면 KOSIS에서 직종별 임금 통계표(예: 고용형태별근로실태조사) 검색해 통계표 ID 기록

각 원천의 공식 문서에서 확인한 엔드포인트 URL·필수 파라미터를 `config.json`에 채운다.

- [ ] **Step 6: 키 주입 확인**

Run:

```bash
python3 -c "import os; missing=[k for k in ['CARIX_KOSIS_KEY','CARIX_WORK24_KEY','CARIX_SARAMIN_KEY'] if not os.environ.get(k)]; print('MISSING:', missing) if missing else print('OK')"
```

Expected: `OK` (임금통계는 4단계 결과에 따라 키가 없을 수 있음 — 그 경우 결과 문서에 기록)

- [ ] **Step 7: 커밋**

```bash
git add verification/README.md verification/config.example.json .gitignore
git commit -m "chore: 데이터 검증 작업 골격 추가"
```

---

### Task 2: 공통 프로브 스크립트

**Files:**
- Create: `verification/probe.py`

**Interfaces:**
- Consumes: `verification/config.json`의 `{source: {base_url, key_env}}`
- Produces: CLI `python3 probe.py <source> --params '<JSON>' [--key-param <이름>]` — `out/<source>_raw_<n>.json`, `out/<source>_fields.json` 생성. Task 3~6이 사용.

- [ ] **Step 1: probe.py 작성**

```python
"""원천 API 호출 → 원문 저장 + 필드 경로 목록 출력. 일회성 측정 도구."""
import argparse
import json
import os
import pathlib
import sys
import urllib.parse
import urllib.request

OUT_DIR = pathlib.Path(__file__).parent / "out"


def load_config(source: str) -> dict:
    config_path = pathlib.Path(__file__).parent / "config.json"
    config = json.loads(config_path.read_text(encoding="utf-8"))
    if source not in config:
        sys.exit(f"config.json에 원천 없음: {source}")
    return config[source]


def fetch(base_url: str, params: dict) -> dict:
    url = f"{base_url}?{urllib.parse.urlencode(params)}"
    req = urllib.request.Request(url, headers={"Accept": "application/json"})
    with urllib.request.urlopen(req, timeout=30) as resp:
        body = resp.read().decode("utf-8")
    try:
        return json.loads(body)
    except json.JSONDecodeError:
        return {"_raw_text": body[:5000]}


def field_inventory(node, prefix="", out=None) -> dict:
    if out is None:
        out = {}
    if isinstance(node, dict):
        for key, value in node.items():
            path = f"{prefix}.{key}" if prefix else key
            out.setdefault(path, type(value).__name__)
            field_inventory(value, path, out)
    elif isinstance(node, list) and node:
        field_inventory(node[0], f"{prefix}[]", out)
    return out


def next_index(source: str) -> int:
    return len(list(OUT_DIR.glob(f"{source}_raw_*.json"))) + 1


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("source")
    parser.add_argument("--params", default="{}", help="쿼리 파라미터 JSON")
    parser.add_argument("--key-param", default=None,
                        help="API 키를 넣을 쿼리 파라미터 이름")
    args = parser.parse_args()

    conf = load_config(args.source)
    params = json.loads(args.params)
    if args.key_param:
        key = os.environ.get(conf["key_env"])
        if not key:
            sys.exit(f"환경변수 없음: {conf['key_env']}")
        params[args.key_param] = key

    OUT_DIR.mkdir(exist_ok=True)
    data = fetch(conf["base_url"], params)

    idx = next_index(args.source)
    raw_path = OUT_DIR / f"{args.source}_raw_{idx}.json"
    raw_path.write_text(json.dumps(data, ensure_ascii=False, indent=2),
                        encoding="utf-8")

    fields = field_inventory(data)
    fields_path = OUT_DIR / f"{args.source}_fields.json"
    fields_path.write_text(json.dumps(fields, ensure_ascii=False, indent=2),
                           encoding="utf-8")

    print(f"raw    -> {raw_path}")
    print(f"fields -> {fields_path}")
    for path, type_name in sorted(fields.items()):
        print(f"  {path}: {type_name}")


if __name__ == "__main__":
    main()
```

- [ ] **Step 2: 동작 확인 (config 없이 실패 경로)**

Run: `cd verification && python3 probe.py unknown --params '{}'`
Expected: `config.json에 원천 없음: unknown` 또는 config.json 부재 에러 — 스크립트 자체는 실행됨

- [ ] **Step 3: 커밋**

```bash
git add verification/probe.py
git commit -m "feat: 원천 API 공통 프로브 스크립트 추가"
```

---

### Task 3: KOSIS CPI 실측과 소비 카테고리 매핑표

**Files:**
- Create: `verification/notes/kosis-cpi.md`

**Interfaces:**
- Consumes: `probe.py` CLI
- Produces: `notes/kosis-cpi.md` — 지출목적별 CPI 분류 목록, Carix 6카테고리 매핑표. Task 7 결과 문서가 인용.

- [ ] **Step 1: 지출목적별 CPI 호출**

공식 문서에서 확인한 지출목적별 소비자물가지수 통계표의 파라미터로 호출:

```bash
cd verification && python3 probe.py kosis --key-param apiKey \
  --params '{"공식 문서 기준 통계표·기간 파라미터": "값"}'
```

Expected: `out/kosis_raw_1.json` 생성, 필드 목록 출력. 지출목적 12분류(식료품, 주택·수도·전기, 교통, 교육 등)가 응답에 존재.

- [ ] **Step 2: 매핑표 작성**

`verification/notes/kosis-cpi.md`에 실제 응답 분류명으로 작성:

```markdown
# KOSIS 지출목적별 CPI ↔ Carix 소비 카테고리 매핑

측정일: YYYY-MM-DD / 원문: out/kosis_raw_1.json

| Carix 카테고리 | KOSIS 분류(실측 명칭) | 비고 |
| --- | --- | --- |
| 주거·공공요금 | (응답의 실제 분류명) | |
| 식비 | | 식료품+외식 분리 여부 기록 |
| 교통 | | |
| 의료 | | |
| 교육·자기계발 | | |
| 여가·기타 | | 잔여 분류 귀속 규칙 기록 |

## 확인 사항
- 월별 시계열 제공 범위: (실측)
- 지역 구분 제공 여부: (실측)
- 갱신 주기와 발표 시차: (실측)
```

모든 칸을 실측값으로 채운다. 채울 수 없는 칸은 `측정 불가`와 사유를 적는다.

- [ ] **Step 3: 커밋**

```bash
git add verification/notes/kosis-cpi.md
git commit -m "docs: KOSIS CPI 실측 및 소비 카테고리 매핑표"
```

---

### Task 4: 고용24 공고 실측

**Files:**
- Create: `verification/notes/work24.md`

**Interfaces:**
- Consumes: `probe.py` CLI
- Produces: `notes/work24.md` — 개발직군 공고 수, 필드 목록, 공개연봉 결측률, 직무 필터 정확도. Task 6 스킬 매칭과 Task 7이 사용. 공고 텍스트 샘플은 `out/work24_raw_*.json`.

- [ ] **Step 1: 개발직군 필터로 공고 조회**

공식 문서에서 직종 코드 체계를 확인하고, 개발·데이터·AI에 해당하는 코드로 호출:

```bash
cd verification && python3 probe.py work24 --key-param authKey \
  --params '{"공식 문서 기준 직종·페이징 파라미터": "값"}'
```

Expected: `out/work24_raw_1.json` 생성. 전체 건수 필드와 공고 목록 확인.

- [ ] **Step 2: 표본 수집 (3페이지 이상)**

페이지 파라미터를 바꿔 3회 이상 호출해 공고 표본 100건 이상 확보. 각 호출은 `out/work24_raw_N.json`으로 저장됨.

- [ ] **Step 3: 측정 기록**

`verification/notes/work24.md`:

```markdown
# 고용24 채용정보 실측

측정일: YYYY-MM-DD / 원문: out/work24_raw_1..N.json

## 커버리지
- 개발·데이터·AI 직종 코드: (사용한 코드 목록)
- 해당 직종 전체 공고 수: (실측)
- 일 호출 제한: (문서 기준)

## 필드
- 공고 본문/직무내용 필드 존재 여부와 평균 길이: (실측)
- 연봉 필드 형태(범위/단일/코드): (실측)
- 경력·지역 필드 체계: (실측)

## 공개연봉 결측률
표본 N건 중 연봉 미기재·"회사내규" 건수: X건 → 결측률 X/N

## 직무 필터 정확도
표본 20건 제목·본문 수동 확인 → 개발직 아님 건수: X건
```

- [ ] **Step 4: 커밋**

```bash
git add verification/notes/work24.md
git commit -m "docs: 고용24 공고 실측 기록"
```

---

### Task 5: 사람인 공고 실측

**Files:**
- Create: `verification/notes/saramin.md`

**Interfaces:**
- Consumes: `probe.py` CLI
- Produces: `notes/saramin.md` — Task 4와 동일 항목 + 고용24 대비 비교. 공고 텍스트 샘플은 `out/saramin_raw_*.json`.

- [ ] **Step 1: 개발직군 필터로 공고 조회**

공식 문서에서 직무 카테고리 코드를 확인하고 호출:

```bash
cd verification && python3 probe.py saramin --key-param access-key \
  --params '{"공식 문서 기준 직무·페이징 파라미터": "값"}'
```

Expected: `out/saramin_raw_1.json` 생성.

- [ ] **Step 2: 표본 수집 (3페이지 이상, 100건 이상)**

Task 4 Step 2와 동일한 방식.

- [ ] **Step 3: 측정 기록**

`verification/notes/saramin.md`에 Task 4 Step 3과 동일한 구조로 기록하고, 마지막에 비교 절을 추가:

```markdown
## 고용24 대비
- 개발직군 공고 수 비율: 사람인 X건 vs 고용24 Y건
- 기술스택 필드 유무: (사람인은 직무 태그 제공 여부 실측)
- 본문 상세도 차이: 표본 10건 비교 요약
```

- [ ] **Step 4: 커밋**

```bash
git add verification/notes/saramin.md
git commit -m "docs: 사람인 공고 실측 기록"
```

---

### Task 6: 스킬 사전 시드와 매칭 재현율 측정

**Files:**
- Create: `verification/skills_seed.csv`
- Create: `verification/skill_match.py`
- Create: `verification/notes/skill-match.md`

**Interfaces:**
- Consumes: `out/work24_raw_*.json`, `out/saramin_raw_*.json` (Task 4·5 산출물)
- Produces: `skills_seed.csv`(정규명,별칭 목록) — 구현 단계 스킬 사전의 초안. `notes/skill-match.md` — 재현율 측정값.

- [ ] **Step 1: 스킬 사전 시드 작성**

`verification/skills_seed.csv` (형식: `정규명,별칭1|별칭2|...`):

```csv
Java,java|자바
Kotlin,kotlin|코틀린
Spring,spring|스프링|spring boot|스프링부트
Python,python|파이썬
Django,django|장고
FastAPI,fastapi
JavaScript,javascript|js|자바스크립트
TypeScript,typescript|ts|타입스크립트
React,react|리액트|react.js|reactjs
Next.js,next.js|nextjs|넥스트
Vue,vue|vue.js|뷰
Node.js,node.js|nodejs|노드
Go,golang|go언어
Rust,rust|러스트
C++,c++
SQL,sql
PostgreSQL,postgresql|postgres|포스트그레스
MySQL,mysql
Redis,redis|레디스
Kafka,kafka|카프카
Docker,docker|도커
Kubernetes,kubernetes|k8s|쿠버네티스
AWS,aws|아마존웹서비스
GCP,gcp|google cloud
Azure,azure
Spark,spark|스파크
Airflow,airflow|에어플로우
Pandas,pandas|판다스
PyTorch,pytorch|파이토치
TensorFlow,tensorflow|텐서플로우
머신러닝,머신러닝|machine learning|ml
딥러닝,딥러닝|deep learning
LLM,llm|대규모언어모델
데이터분석,데이터분석|data analysis
```

- [ ] **Step 2: 매칭 스크립트 작성**

`verification/skill_match.py`:

```python
"""수집된 공고 원문에서 스킬 사전 매칭률 측정. 일회성 측정 도구."""
import csv
import json
import pathlib
import re
import sys

BASE = pathlib.Path(__file__).parent
OUT_DIR = BASE / "out"


def load_skills() -> dict:
    skills = {}
    with open(BASE / "skills_seed.csv", encoding="utf-8") as f:
        for row in csv.reader(f):
            if len(row) < 2:
                continue
            name, aliases = row[0], row[1].split("|")
            skills[name] = [a.strip().lower() for a in aliases if a.strip()]
    return skills


def extract_texts(source: str) -> list:
    """원문 JSON에서 문자열 값을 전부 이어붙여 공고 단위 텍스트로 만든다."""
    texts = []
    for path in sorted(OUT_DIR.glob(f"{source}_raw_*.json")):
        data = json.loads(path.read_text(encoding="utf-8"))
        postings = find_posting_list(data)
        for posting in postings:
            texts.append(" ".join(collect_strings(posting)).lower())
    return texts


def find_posting_list(node):
    """응답에서 가장 큰 dict 리스트를 공고 목록으로 간주한다."""
    best = []
    if isinstance(node, list):
        if node and isinstance(node[0], dict) and len(node) > len(best):
            best = node
    elif isinstance(node, dict):
        for value in node.values():
            candidate = find_posting_list(value)
            if len(candidate) > len(best):
                best = candidate
    return best


def collect_strings(node) -> list:
    if isinstance(node, str):
        return [node]
    if isinstance(node, dict):
        return [s for v in node.values() for s in collect_strings(v)]
    if isinstance(node, list):
        return [s for v in node for s in collect_strings(v)]
    return []


def main() -> None:
    if len(sys.argv) != 2:
        sys.exit("사용법: python3 skill_match.py <source>")
    source = sys.argv[1]
    skills = load_skills()
    texts = extract_texts(source)
    if not texts:
        sys.exit(f"out/에 {source} 원문 없음. probe.py 먼저 실행.")

    print(f"공고 수: {len(texts)}")
    matched_any = 0
    counts = {}
    for text in texts:
        hit = False
        for name, aliases in skills.items():
            for alias in aliases:
                if re.search(re.escape(alias), text):
                    counts[name] = counts.get(name, 0) + 1
                    hit = True
                    break
        if hit:
            matched_any += 1

    print(f"1개 이상 스킬 매칭된 공고: {matched_any} "
          f"({matched_any / len(texts):.0%})")
    for name, count in sorted(counts.items(), key=lambda x: -x[1])[:20]:
        print(f"  {name}: {count}")


if __name__ == "__main__":
    main()
```

- [ ] **Step 3: 두 원천에 대해 실행**

Run: `cd verification && python3 skill_match.py work24 && python3 skill_match.py saramin`
Expected: 원천별 공고 수, 매칭률, 상위 스킬 분포 출력

- [ ] **Step 4: 수동 재현율 표본 확인**

원천별 공고 10건의 원문을 직접 읽고, 사람이 식별한 스킬 대비 스크립트 매칭 누락을 센다. 결과를 `verification/notes/skill-match.md`에 기록:

```markdown
# 스킬 매칭 재현율 측정

측정일: YYYY-MM-DD

| 원천 | 공고 수 | 1개 이상 매칭률 | 수동 표본 10건 누락 스킬 수 |
| --- | --- | --- | --- |
| work24 | | | |
| saramin | | | |

## 누락 원인 분류
- 사전에 없는 스킬: (목록 → 시드 보강 후보)
- 표기 변형: (목록 → 별칭 보강 후보)

## 판단
공고 텍스트 기반 스킬 추출이 재현 가능한가: 가능 / 조건부 / 불가 + 근거
```

- [ ] **Step 5: 커밋**

```bash
git add verification/skills_seed.csv verification/skill_match.py verification/notes/skill-match.md
git commit -m "feat: 스킬 사전 시드와 매칭 재현율 측정"
```

---

### Task 7: 임금통계 확인과 검증 결과 문서 종합

**Files:**
- Create: `verification/notes/wage-stat.md`
- Create: `docs/superpowers/specs/2026-07-XX-carix-data-verification-result.md` (XX = 실제 완료일)

**Interfaces:**
- Consumes: Task 1~6의 모든 `notes/*.md`
- Produces: 검증 결과 문서 — 2단계 구현 플랜의 직접 입력. 원천별 사용 판정, 실측 필드 목록, 설정 후보값 포함.

- [ ] **Step 1: 임금통계 실측**

Task 1 Step 5의 4번 결과에 따라:
- 임금직무정보시스템 API가 있으면: `probe.py wagestat`으로 호출해 직종×경력×지역 차원과 임금 정의 확인
- 없으면: KOSIS에서 확인한 직종별 임금 통계표를 `probe.py kosis`로 호출

`verification/notes/wage-stat.md`에 기록:

```markdown
# 임금통계 실측

- 사용 원천: 임금직무정보시스템 / KOSIS 대체 (선택 사유)
- 제공 차원: 직종 분류 체계, 경력(근속) 구간, 지역 구분 (실측)
- 임금 정의: 월임금총액/연봉 환산, 초과급여·상여 포함 여부 (실측)
  → 스펙 4.4 상여 포함 여부 입력 추가 결정: 추가 / 캐비앳만
- 갱신 주기, 최신 기준 시점: (실측)
- 개발·데이터·AI 직종의 분류 매핑 가능성: (실측)
```

- [ ] **Step 2: 검증 결과 문서 작성**

`docs/superpowers/specs/2026-07-XX-carix-data-verification-result.md`:

```markdown
# Carix 데이터 선행 검증 결과

검증 기간: YYYY-MM-DD ~ YYYY-MM-DD
스펙: 2026-07-15-carix-pipeline-redesign-design.md 7장

## 원천별 판정

| 원천 | 판정 | 근거 요약 |
| --- | --- | --- |
| KOSIS CPI | 사용 / 제외 | notes/kosis-cpi.md |
| 고용24 | 사용 / 제외 | notes/work24.md |
| 사람인 | 사용 / 제외 | notes/saramin.md (약관 확인 결과 포함) |
| 임금통계 | 사용 원천 명시 | notes/wage-stat.md |

## 구현 플랜 입력값

- 각 원천 엔드포인트, 인증 방식, 호출 제한: (실측)
- 정규화 대상 필드 목록 (원천별): (out/*_fields.json 요약)
- CPI ↔ 소비 카테고리 확정 매핑표: (kosis-cpi.md에서 복사)
- 스킬 시드 보강 목록: (skill-match.md에서 복사)
- 상여 포함 여부 입력: 추가 / 캐비앳만
- 최소 표본 기준 후보값: 직무×경력×지역 셀별 실측 공고 수 분포에서 산출
  (예: 하위 4분위 셀이 N건이면 최소 표본 = N 근방에서 시작)

## 스펙 변경 필요 사항

검증 결과가 스펙 가정과 다른 부분. 없으면 "없음".
```

모든 표를 실측값으로 채운다.

- [ ] **Step 3: 커밋과 PR**

```bash
git add verification/notes/wage-stat.md docs/superpowers/specs/2026-07-*-carix-data-verification-result.md
git commit -m "docs: 데이터 선행 검증 결과 종합"
git push -u origin feature/data-verification
```

PR 생성은 사용자 확인 후 진행.

---

## 완료 후

검증 결과 문서를 입력으로 2단계 구현 플랜(백엔드·파이프라인·프론트)을 작성한다. 판정이 `제외`인 원천은 구현 플랜에서 어댑터를 만들지 않는다.
