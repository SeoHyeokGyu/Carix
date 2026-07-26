# OCI Deployment Plan Documentation Implementation Plan

> **완료·대체됨.** 이 계획은 README에 "OCI 배포 예정" 체크리스트 한 줄을 추가하는 문서 작업이었고
> 이미 반영됐다 (해당 항목은 2026-07-26 배포 성공으로 완료 표시됨). 실제 인프라 구성과 배포
> 절차는 [OCI 서버 구성·배포 계획](oci-server-provisioning-plan.md)이 정본이다.
> 아래 체크박스는 당시 원문 그대로 두며 더 이상 실행 대상이 아니다.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** README의 프로젝트 상태에 Carix가 Oracle Cloud Infrastructure(OCI) 배포를 예정하고 있음을 기록한다.

**Architecture:** 별도 배포 문서를 만들지 않고 기존 `README.md`의 프로젝트 상태 체크리스트에 미완료 항목 한 줄을 추가한다. 배포 방법이나 인프라 구성은 이번 변경 범위에 포함하지 않는다.

**Tech Stack:** Markdown, Git

## Global Constraints

- 표기는 `Oracle Cloud Infrastructure(OCI) 배포 환경 구성`을 그대로 사용한다.
- 배포 계획은 완료되지 않은 체크리스트 항목인 `- [ ]`로 표시한다.
- 인스턴스, 네트워크, 컨테이너, 도메인, HTTPS 등 상세 배포 절차는 추가하지 않는다.
- 기존 프로젝트 상태와 기술 스택 설명은 변경하지 않는다.

---

### Task 1: README에 OCI 배포 계획 추가

**Files:**
- Modify: `README.md:151`

**Interfaces:**
- Consumes: `README.md`의 `프로젝트 상태` 체크리스트
- Produces: README 독자가 확인할 수 있는 OCI 배포 예정 상태

- [ ] **Step 1: 현재 항목이 아직 없는지 확인**

Run:

```bash
rg -n "Oracle Cloud Infrastructure|OCI 배포" README.md
```

Expected: 일치 항목이 없어 종료 코드 1을 반환한다.

- [ ] **Step 2: 프로젝트 상태 체크리스트에 미완료 항목 추가**

`README.md`의 `- [ ] 애플리케이션 구현` 바로 다음 줄에 다음 내용을 추가한다.

```md
- [ ] Oracle Cloud Infrastructure(OCI) 배포 환경 구성
```

- [ ] **Step 3: 문구와 위치 확인**

Run:

```bash
rg -n -C 2 "Oracle Cloud Infrastructure\(OCI\) 배포 환경 구성" README.md
```

Expected: `애플리케이션 구현` 다음 줄에 OCI 배포 환경 구성 항목이 한 번 표시된다.

- [ ] **Step 4: Markdown 공백 오류와 변경 범위 확인**

Run:

```bash
git diff --check
git diff -- README.md
```

Expected: `git diff --check` 출력이 없고, README diff에는 체크리스트 한 줄 추가만 표시된다.

- [ ] **Step 5: 문서 변경 커밋**

```bash
git add README.md
git commit -m "docs: note planned OCI deployment"
```
