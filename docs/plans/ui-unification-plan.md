# UI 일관성 통일 계획 (B 시각·UX 규범 채택)

프런트엔드가 두 벌의 시각 언어와 두 벌의 DTO 를 동시에 갖고 있다. 이 문서는 그것을
하나로 모으는 실행 계획이다. 규범 자체는 코드가 들어온 뒤 `docs/specs/frontend-design.md`
에 반영한다 — 문서는 현재 상태만 서술하므로 구현보다 먼저 쓰지 않는다.

---

## 1. 남은 불일치 — 프리미티브 미채택

디자인 시스템이 있는데 절반이 쓰이지 않는다. 아래는 실측치다.

| 항목 | 실측 |
| :--- | :--- |
| raw `<table>` | 6곳 — 전부 B·박지수 소유. C 소유는 `Table` 로 옮겼다 |
| raw `<button>` | 29개 — 전부 B·박지수 소유 |
| 포커스 링 | 두 벌 — 프리미티브는 `focus-visible:ring-[3px] ring-ring/50`, raw 는 UA 기본 아웃라인 |
| 오류 표현 | 두 계열 — `text-destructive` 인라인 vs `carix-danger` 박스 배너 |
| 카드 | `<Card>` vs 동일 클래스 문자열 인라인 복제 |
| 팔레트 누출 | admin 4파일 |

차트 색은 누출이 아니다. `IncomeVsPriceChart` 의 stroke/fill 은 전부
`var(--color-carix-*)` 토큰을 쓴다.

**결정 신호 분기·토큰 공백·로딩 표현은 P0~P2 가, 프리미티브 재정의와 C 소유 교체는 P3 이
해소했다.** 남은 것은 B·박지수 화면의 교체(§5)뿐이고, P4 의 eslint 예외 목록이 그 남은 일을 센다.

---

## 2. 결정 사항

| 축 | 결정 | 근거 |
| :--- | :--- | :--- |
| 통일 범위 | **시각 + UX 둘 다.** 프리미티브 계층을 유지하되 그 안을 B 시각 규칙으로 재정의하고, B 의 UX 규범(열화 상태 전용 화면·산출 근거 점진적 공개·빈 값을 0 으로 안 채우기)을 전 화면에 적용 | B 방향은 반(反)프리미티브가 아니다 — B 도 `mypage/profile.tsx` 에서는 프리미티브를 쓰고, `ui/card.tsx` 를 carix 토큰으로 통일한 것도 B 다 |
| 신호 라벨·색 출처 | **프런트 단일 테이블 + 서버 폴백.** 프런트 `decisionSignal.ts` 가 유일 출처, 서버 `label` 은 프런트에 매핑 없는 새 코드일 때만 | B 의 프런트 매핑 방향을 따르면서 서버 DTO 계약을 안 깬다 |
| 실행 주체 | **C 전담, 공용 계층만.** 개별 화면 교체는 각 소유자 | 개발 계획서 §1 「화면 셸 소유는 하나다」 를 안 깨는 유일한 배치. 공용 계층은 이미 C 소유(`coreApi`·라우트 가드 선례) |

---

## 3. 갈림길과 선택

| | **제자리 재정의 (채택)** | variant 추가 (기각) |
| :--- | :--- | :--- |
| 방식 | `Card`·`Button`·`Badge` **기본값**을 B 시각 언어로 교체 | B 스타일 variant 를 새로 달고 기존 기본값 유지 |
| C 화면 7개 | 한 커밋에 같이 바뀜 | 안 바뀜 (opt-in) |
| DRY | 해소 | 두 시각 언어가 공존 — **통일이 아님** |

제자리 재정의를 택한다. "B 방향으로 통일"의 문자 그대로다.

**대가를 명시한다.** `Button` 기본값이 `h-9 px-4 py-2 rounded-md text-sm` 에서
`rounded-lg px-4 py-1.5 text-xs` 로 가면 `dashboard/index`·`simulator`·`onboarding/profile`
`·income·expense·review`·`mypage/profile` 의 모든 버튼이 한 번에 작아지고 모서리가 바뀐다.

## 4. 단계

### 완료 — P0~P4

| 단계 | 납품 | PR |
| :--- | :--- | :--- |
| P0 | 결정 신호 단일 출처(`lib/decisionSignal.ts`)와 서버 `label` 폴백, `GET /dashboard/history` 응답의 `label`, `dev/primitives` 갤러리 시각 회귀 베이스라인 | #161 |
| P1 | 다섯 번째 의미 색상 `--carix-action`(라이트/다크 쌍), `@custom-variant dark` — 이것이 없어 `dark:` 22곳이 토글 경로에서 무효였다 | #162 |
| P2 | `ToneBadge`·`EmptyState`·`LoadingState` 표현 계층, 도메인별 해석 모듈(`lib/indexStatus.ts`·`lib/skillDemandStatus.ts`), `SkillDemandDetail.status` 와이어 타입 좁히기 | #164 |
| P3 | `Button`(rounded-lg·text-xs, 전 크기 재조정)·`Card`(p-6·gap-4)·`Badge`(중립 칩 전용)·`Table`(B 틀 + `plain`) 제자리 재정의, `alert`·`select` 삭제, `EmptyState` `headingLevel`, C 소유 raw 표 4·버튼 5 교체 | `feat/ui3-p3-primitives` (로컬 머지) |
| P4 | eslint 로 raw `<button>`·토큰 밖 팔레트를 `error` 로 막고 현재 위반 11파일을 소유자별 예외 목록으로, `frontend-design.md` 컴포넌트 계약, `trends.html` 연봉 분포 표류 정리 | #222 |

**`StatusBadge` 는 결번이다.** `IndexStatus`(5종)와 `SkillDemandStatus`(4종)를 "둘로 갈린
하나"로 본 전제가 틀렸다 — 서로 다른 도메인 어휘라 합치면 없는 상태값을 노출한다. 합칠 수
있는 것은 어휘가 아니라 표현 계층이고 그 자리를 `ToneBadge` 가 갖는다.

**화면 단위 시각 회귀 베이스라인은 아직 없다.** 인증이 필요한 7화면은
`CARIX_E2E_EMAIL`/`CARIX_E2E_PASSWORD` 게이트 뒤에 있어 갤러리로 대신했다. 갤러리는
`Card` 의 `gap` 변경이 화면 내부를 재배치하는 것을 못 잡는다. 계정·시드·CI 시크릿이
설 때 화면 베이스라인을 붙인다.

**수동 다크모드 확인 시 주의.** `/dev/trends`·`/dev/skills`·`/dev/primitives` 는
`routes.ts` 에서 `AppShell` 밖에 매핑돼 `ThemeToggle` 이 마운트되지 않는다 — devtools
로 `documentElement` 에 클래스를 수동 주입하지 않는 한 `.dark`/`.light` 클래스가 전혀
걸리지 않는다. `primitives.spec.ts` 는 클래스를 직접 토글하므로 자동 베이스라인은
영향이 없다. 문제는 사람이 이 URL 을 열어 `dark:` 유틸(예: `SalaryDistributionChart`
의 `dark:ring-carix-surface`)을 육안 확인할 때다 — OS 가 다크면 토큰만 다크값으로
바뀌고 `dark:` 규칙은 안 걸려, 운영에는 없는 상태(흰 마커 링이 남는 것)를 버그로
오인하게 된다. 수동 확인은 `ThemeToggle` 이 실제로 뜨는 라우트(`/dashboard/trends`,
`/onboarding/skills` 등)에서 토글을 눌러 해야 한다.

**P2 가 넘긴 것.**

| 항목 | 내용 |
| :--- | :--- |
| 손수 뱃지 잔여 | `SalaryDistributionChart` 는 B 몫이다(§5). `admin/JobHistoryTable`·`admin/CollectorStatusBanner`·`onboarding/skills` 는 소유자 몫(§5). `hero/Hero` 의 알약은 랜딩 제목 위 장식 문구라 배지 계약 대상이 아니다 |
| `history.tsx` 로딩 분기 | 기계 검증이 0인 유일한 자리다. 모든 테스트가 `fetchSnapshotHistory` 를 즉시 resolve 해서 `isLoading` 을 안 탄다 |
| 시각 커버리지 없는 호출부 | `IndexCard` 의 뱃지, `SkillDemandSection` 의 `variant="plain"` 빈 상태 — 갤러리엔 합성 카드만 있다 |

### P3 의 그물 — 시각 회귀

`e2e/primitives.spec.ts` 가 `/dev/primitives` 갤러리를 두 겹으로 잰다. 로컬 전용이다 —
CI 에서 돌지 않고 베이스라인은 darwin 것만 있다.

- **computed-style 단언.** `Button`·`Badge`·`Card`·`Table` thead·`Input` 이 어느 토큰을
  쓰는지 라이트·다크로 잰다. 기대값은 탐침 요소에 같은 CSS 값을 칠해 얻어 rgb 를 박지
  않는다. **P3 는 이 표(`STYLE_EXPECTATIONS`)를 고쳐 쓰는 것으로 무엇을 바꿨는지 적는다.**
  `--update-snapshots` 후 png diff 가 없다는 것은 "색 변화 없음" 의 증거가 아니다
- **스크린샷.** `threshold: 0.05`(컷오프 88.04 — 0.1 의 352.15 는 재색상화 YIQ 델타
  268.43 / 102.67 보다 커서 무효), `maxDiffPixels: 100`(라틴 글자 안티에일리어싱 잡음 최대
  63px 위, 이 그물이 처음 잡은 `NEGOTIATE` 뱃지 표류 261px 아래). 그 표류는 P1 이 `action`
  톤을 토큰으로 옮긴 뒤 베이스라인이 따라가지 못한 것으로, 기본값 0.2 가 가리고 있었다
- **새 절은 갤러리 맨 끝에 붙인다.** 앞에 끼우면 뒤 절의 y 가 소수로 밀려 캡처 높이가 1px
  반올림되고 안 바뀐 절이 깨진다
- **남은 흔들림.** 660회에 1회 "waiting for fonts to load" 로 단언 타임아웃 5초를 넘긴다.
  `document.fonts.ready` 를 먼저 기다려도 안 막힌다. Google Fonts 서브셋을 글리프가 그려질 때
  네트워크로 받는 탓으로 보인다

미니파이된 CSS 를 검증할 때는 단일행 빌드 산출물을 grep 가능하게 바꾼다.

```sh
tr '}' '\n' < build/client/assets/root-*.css | grep ...
```

---

## 5. 마이그레이션 계약

C 는 공용 계층만 낸다. 화면 교체는 소유자 몫이므로 **이 표가 곧 납품물이다.**

| 소유 | 파일 | 교체 |
| :--- | :--- | :--- |
| B | `SalaryDistributionChart`·`SkillDemandSection`·`IncomeVsPriceChart`·`routes/dashboard/history`·`trends`·`routes/onboarding/skills` | raw 카드 6 → `Card`, raw button 10종 → `Button`, raw table 3 → `Table`, 열화 박스 → `EmptyState`, 상태 span → `ToneBadge`, `getSignalBadge` → `SignalBadge`, `SalaryDistributionChart` 손수 배지 → `ToneBadge`, `onboarding/skills` 단계 이동 줄 → 컴포넌트 계약 「단계 이동 줄」 |
| 박지수 | `components/admin/` 6파일 + `routes/admin/collector.tsx` | 팔레트 누출 → 토큰, raw button → `Button`, `animate-pulse` → `LoadingState`, `collector.tsx` 상태 뱃지 `-300` → 테이블과 동일한 `-400`, `JobHistoryTable` 링크 → `TextLink direction="none" size="inherit"`, `routes/admin/collector.tsx` → `PageFrame width="admin" as="main"`·`PageHeader` |
| C | 온보딩 4화면·대시보드·가이드·시뮬레이터·`S-12`·`mypage/profile`·`AuthForm` | 완료(P3). 프리미티브를 import 하는 파일은 전부 C 소유라 P3 재정의가 닿는 것도 C 화면뿐이다 |

**남은 일을 세는 곳은 `frontend/eslint.config.js` 의 예외 목록이다.** raw `<button>`·토큰 밖
팔레트는 `error` 이고, 아직 교체하지 않은 B·박지수 11파일만 소유자 주석과 함께 예외로 남았다.
교체하면 목록에서 한 줄씩 지운다. 새 파일은 목록에 넣지 않는다 — 넣는 순간 공존이 굳는다.

## 6. 범위 밖

- **프런트 API 클라이언트 병합** — `lib/api/dashboard.ts` 와 `lib/dashboardMarketApi.ts` 가
  같은 대시보드 도메인을 쪼개 갖고 있다. P0 이 고친 결정 신호 분기의 뿌리이고, 병합은 별도 건으로 다룬다
- **`MarketValueIndexServiceImpl` 1026줄** — 코딩 규칙 800줄 상한을 넘는 레포 유일 파일.
  UI 와 무관하다
- **`SalaryDistributionChart` 의 격자 두 벌** — `getBodyPartInfo` 의 `relativeY` 가 죽은
  값이고 `percentileToBodyY` 와 경계가 어긋난다. B 소유 컴포넌트 내부 결함이라 위 교체표의
  작업과 함께 B 가 다룬다
