# Carix 데이터 선행 검증

원천 API 측정 스크립트. 일회성 도구이며 제품 코드가 아니다.
측정 결과의 정본은 [외부 원천 API 연동 명세](../docs/specs/external-api-integration.md)다.

## 준비

1. `config.example.json`을 `config.json`으로 복사한다. 엔드포인트는 실측값으로
   채워져 있으므로 그대로 쓰면 된다.
2. API 키를 환경변수로 설정한다. 이름은 루트 `.env.example`과 동일하다:
   `KOSIS_API_KEY`, `SARAMIN_API_KEY`, `WORK24_API_KEY`, `WAGE_STAT_API_KEY`
3. `python3 probe.py <source> --params '<JSON>' --key-param <키 파라미터명>` 으로 실행한다.

키 파라미터명은 원천마다 다르다. KOSIS는 `apiKey`, 사람인은 `access-key`,
고용24는 `authKey`다.

## 실행 예

```bash
# KOSIS 통계표 메타 (축 순서 OBJ_ID_SN 확인)
python3 probe.py kosis_meta --key-param apiKey \
  --params '{"method":"getMeta","type":"ITM","orgId":"101","tblId":"DT_1J22001","format":"json","jsonVD":"Y"}'

# 사람인 IT개발·데이터 직군, 기간 지정
python3 probe.py saramin --key-param access-key \
  --params '{"job_mid_cd":"22","count":"10","published_min":"2025-01-01","published_max":"2025-12-31"}'
```

## 원천 상태 (2026-07-25)

| 원천 | 상태 |
| --- | --- |
| `kosis`, `kosis_meta` | 측정 완료 |
| `saramin` | 키 발급 대기 |
| `work24` | **측정 불가** — 채용정보 API는 기업회원 전용 |
| `wagestat` | 통계표 미확정 |

## 산출물

- `out/<source>_raw_<n>.json`: 원문 응답 (커밋 금지 — `.gitignore` 처리됨)
- `out/<source>_fields.json`: 필드 경로 목록
- 측정 요약은 [외부 원천 API 연동 명세](../docs/specs/external-api-integration.md)에 반영한다.
