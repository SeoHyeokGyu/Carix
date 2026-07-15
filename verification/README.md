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
